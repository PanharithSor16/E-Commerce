package com.org.ecommerce.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.analytics.AnalyticsContext;
import com.org.ecommerce.analytics.AnalyticsEventService;
import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.auth.AuthDtos;
import com.org.ecommerce.entity.Enums.ConsentSource;
import com.org.ecommerce.entity.Enums.ConsentType;
import com.org.ecommerce.entity.Enums.EventType;
import com.org.ecommerce.entity.Enums.LoginFailureReason;
import com.org.ecommerce.entity.Enums.RevokedReason;
import com.org.ecommerce.entity.Enums.TokenType;
import com.org.ecommerce.entity.Role;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.RoleRepository;
import com.org.ecommerce.repository.UserRepository;
import com.org.ecommerce.security.AuthenticatedUser;
import com.org.ecommerce.security.ClientInfo;
import com.org.ecommerce.security.TokenHasher;

/**
 * Registration, login, logout, password reset and email verification.
 * <p>
 * Login deliberately runs without an outer transaction: every failed attempt (and the lockout
 * counter) must be committed even though the request ends with 401.
 */
@Service
public class AuthService {

    /** Same message for unknown email, wrong password and locked account: no account enumeration. */
    static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final LoginHistoryService loginHistory;
    private final UserTokenService tokenService;
    private final EmailService emailService;
    private final ConsentService consentService;
    private final AnalyticsEventService analytics;
    private final AuditService audit;
    private final AppProperties.Security security;
    private final Clock clock;
    /** Used to equalize timing when the account does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       SessionService sessionService, LoginHistoryService loginHistory, UserTokenService tokenService,
                       EmailService emailService, ConsentService consentService, AnalyticsEventService analytics,
                       AuditService audit, AppProperties properties, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.loginHistory = loginHistory;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.consentService = consentService;
        this.analytics = analytics;
        this.audit = audit;
        this.security = properties.security();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    public record LoginResult(User user, SessionService.CreatedSession session) {
    }

    @Transactional
    public LoginResult register(AuthDtos.RegisterRequest req, ClientInfo client, AnalyticsContext analyticsContext) {
        String email = TokenHasher.normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPasswordChangedAt(clock.instant());
        user.getRoles().add(roles.findByName(Role.CUSTOMER).orElseThrow());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }

        consentService.recordForUser(user.getId(), ConsentType.ANALYTICS, req.analyticsConsent(), ConsentSource.REGISTRATION);
        consentService.recordForUser(user.getId(), ConsentType.MARKETING, req.marketingConsent(), ConsentSource.REGISTRATION);

        String token = tokenService.issue(user, TokenType.EMAIL_VERIFICATION, security.emailVerificationTtl());
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), token);

        SessionService.CreatedSession session = sessionService.create(user, client);
        audit.record("USER_REGISTERED", "User", user.getId(), Map.of());
        // These use their own transactions and reference the new user row: run them after commit.
        Long userId = user.getId();
        var sessionPublicId = session.session().getPublicId();
        AfterCommit.run(() -> {
            loginHistory.recordSuccess(userId, email, client, sessionPublicId);
            analytics.recordServerEvent(EventType.REGISTER, analyticsContext.visitorKey(), analyticsContext.sessionKey(),
                    userId, null);
        });
        return new LoginResult(user, session);
    }

    public LoginResult login(AuthDtos.LoginRequest req, ClientInfo client, AnalyticsContext analyticsContext) {
        String email = TokenHasher.normalizeEmail(req.email());
        Instant now = clock.instant();
        User user = users.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            loginHistory.recordFailure(null, email, LoginFailureReason.UNKNOWN_ACCOUNT, client);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            passwordEncoder.matches(req.password(), dummyHash);
            loginHistory.recordFailure(user.getId(), email, LoginFailureReason.ACCOUNT_LOCKED, client);
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    INVALID_CREDENTIALS + " If you have made several attempts, wait a few minutes and try again.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginHistory.recordFailure(user.getId(), email, LoginFailureReason.BAD_CREDENTIALS, client);
            registerFailure(user, now);
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    INVALID_CREDENTIALS);
        }
        // Password is correct from here on: it is safe to tell the owner why login is refused.
        if (!user.isEnabled()) {
            loginHistory.recordFailure(user.getId(), email, LoginFailureReason.ACCOUNT_DISABLED, client);
            throw ApiException.forbidden("This account has been deactivated. Please contact support.");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);

        SessionService.CreatedSession session = sessionService.create(user, client);
        loginHistory.recordSuccess(user.getId(), email, client, session.session().getPublicId());
        analytics.recordServerEvent(EventType.LOGIN, analyticsContext.visitorKey(), analyticsContext.sessionKey(),
                user.getId(), null);
        return new LoginResult(user, session);
    }

    /** Locks the account once the number of failures inside the window reaches the limit. */
    private void registerFailure(User user, Instant now) {
        long failures = loginHistory.recentFailures(user.getId(), now.minus(security.lockout().window()));
        user.setFailedLoginCount((int) Math.min(failures, Integer.MAX_VALUE));
        if (failures >= security.lockout().maxFailures()) {
            user.setLockedUntil(now.plus(security.lockout().duration()));
            audit.recordOutcome("ACCOUNT_LOCKED", "User", user.getId(),
                    Map.of("failures", failures, "lockMinutes", security.lockout().duration().toMinutes()),
                    com.org.ecommerce.entity.Enums.AuditOutcome.SUCCESS, null);
        }
        users.save(user);
    }

    public void logout(AuthenticatedUser principal, AnalyticsContext analyticsContext) {
        sessionService.revoke(principal.sessionId(), RevokedReason.LOGOUT, principal.userId());
        analytics.recordServerEvent(EventType.LOGOUT, analyticsContext.visitorKey(), analyticsContext.sessionKey(),
                principal.userId(), null);
    }

    /** Always succeeds from the caller's point of view, whether or not the account exists. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmail(TokenHasher.normalizeEmail(rawEmail))
                .filter(User::isEnabled)
                .ifPresent(user -> {
                    String token = tokenService.issue(user, TokenType.PASSWORD_RESET, security.passwordResetTtl());
                    emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), token);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = tokenService.redeem(rawToken, TokenType.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(clock.instant());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        sessionService.revokeAll(user.getId(), null, RevokedReason.PASSWORD_CHANGED, user.getId());
        audit.record("PASSWORD_RESET", "User", user.getId(), Map.of());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        User user = tokenService.redeem(rawToken, TokenType.EMAIL_VERIFICATION);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(clock.instant());
            audit.record("EMAIL_VERIFIED", "User", user.getId(), Map.of());
        }
    }

    @Transactional
    public void resendVerification(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.isEmailVerified()) {
            return;
        }
        String token = tokenService.issue(user, TokenType.EMAIL_VERIFICATION, security.emailVerificationTtl());
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), token);
    }

    /** Changes the password and signs out every other session. */
    @Transactional
    public void changePassword(AuthenticatedUser principal, String currentPassword, String newPassword) {
        User user = users.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(clock.instant());
        sessionService.revokeAll(user.getId(), principal.sessionId(), RevokedReason.PASSWORD_CHANGED, user.getId());
        audit.record("PASSWORD_CHANGED", "User", user.getId(), Map.of());
    }
}
