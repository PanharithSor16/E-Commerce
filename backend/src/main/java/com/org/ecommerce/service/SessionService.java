package com.org.ecommerce.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.entity.AuthenticationSession;
import com.org.ecommerce.entity.Enums.RevokedReason;
import com.org.ecommerce.entity.Permission;
import com.org.ecommerce.entity.Role;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.AuthenticationSessionRepository;
import com.org.ecommerce.security.AuthenticatedUser;
import com.org.ecommerce.security.ClientInfo;
import com.org.ecommerce.security.TokenHasher;

/** Creates, validates, extends and revokes server-side authentication sessions. */
@Service
public class SessionService {

    /** Sliding expiry is written at most once per minute per session to avoid a DB write on every request. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final AuthenticationSessionRepository sessions;
    private final TokenHasher tokenHasher;
    private final AppProperties.Security security;
    private final Clock clock;

    public SessionService(AuthenticationSessionRepository sessions, TokenHasher tokenHasher, AppProperties properties,
                          Clock clock) {
        this.sessions = sessions;
        this.tokenHasher = tokenHasher;
        this.security = properties.security();
        this.clock = clock;
    }

    public record CreatedSession(String rawToken, AuthenticationSession session, Duration cookieMaxAge) {
    }

    @Transactional
    public CreatedSession create(User user, ClientInfo client) {
        AppProperties.SessionTimeouts timeouts = timeoutsFor(user);
        Instant now = clock.instant();
        String raw = tokenHasher.newToken();
        AuthenticationSession s = new AuthenticationSession();
        s.setUser(user);
        s.setTokenHash(tokenHasher.sha256(raw));
        s.setCreatedAt(now);
        s.setLastSeenAt(now);
        s.setAbsoluteExpiresAt(now.plus(timeouts.absoluteTimeout()));
        s.setIdleExpiresAt(min(now.plus(timeouts.idleTimeout()), s.getAbsoluteExpiresAt()));
        s.setIpAddress(client.ipAddress());
        s.setUserAgent(client.userAgent());
        s.setBrowser(client.browser());
        s.setOperatingSystem(client.os());
        s.setDeviceCategory(client.deviceCategory());
        sessions.save(s);
        return new CreatedSession(raw, s, timeouts.absoluteTimeout());
    }

    /** Resolves a raw cookie token to a principal, extending the idle timeout. Empty if invalid or expired. */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        Instant now = clock.instant();
        return sessions.findForAuthentication(tokenHasher.sha256(rawToken))
                .filter(s -> s.isActive(now))
                .filter(s -> s.getUser().isEnabled() && !s.getUser().isLocked(now))
                .map(s -> {
                    if (Duration.between(s.getLastSeenAt(), now).compareTo(TOUCH_INTERVAL) >= 0) {
                        AppProperties.SessionTimeouts t = timeoutsFor(s.getUser());
                        s.setLastSeenAt(now);
                        s.setIdleExpiresAt(min(now.plus(t.idleTimeout()), s.getAbsoluteExpiresAt()));
                    }
                    return toPrincipal(s);
                });
    }

    @Transactional
    public void revoke(Long sessionId, RevokedReason reason, Long revokedBy) {
        sessions.findById(sessionId).ifPresent(s -> {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(clock.instant());
                s.setRevokedReason(reason);
                s.setRevokedByUserId(revokedBy);
            }
        });
    }

    /** Revokes a session by its public id; when {@code ownerId} is set the session must belong to that user. */
    @Transactional
    public void revokeByPublicId(UUID publicId, Long ownerId, RevokedReason reason, Long revokedBy) {
        AuthenticationSession s = sessions.findByPublicId(publicId)
                .filter(x -> ownerId == null || x.getUser().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Session"));
        if (s.getRevokedAt() == null) {
            s.setRevokedAt(clock.instant());
            s.setRevokedReason(reason);
            s.setRevokedByUserId(revokedBy);
        }
    }

    @Transactional
    public int revokeAll(Long userId, Long exceptSessionId, RevokedReason reason, Long revokedBy) {
        return sessions.revokeAllForUser(userId, exceptSessionId, reason, revokedBy, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<AuthenticationSession> activeSessions(Long userId) {
        return sessions.findActiveByUser(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public Page<AuthenticationSession> allActiveSessions(Pageable pageable) {
        return sessions.findAllActive(clock.instant(), pageable);
    }

    private AppProperties.SessionTimeouts timeoutsFor(User user) {
        return user.isStaffOrAdmin() ? security.staffSession() : security.customerSession();
    }

    static AuthenticatedUser toPrincipal(AuthenticationSession s) {
        User u = s.getUser();
        Set<String> roles = u.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = u.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(u.getId(), u.getPublicId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.isEmailVerified(), s.getId(), s.getPublicId(), roles, permissions);
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
