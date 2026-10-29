package com.org.ecommerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.entity.AuthenticationSession;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.repository.AuthenticationSessionRepository;
import com.org.ecommerce.security.TokenHasher;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Mock
    AuthenticationSessionRepository repository;

    SessionService service;

    @BeforeEach
    void setUp() {
        AppProperties.SessionTimeouts customer = new AppProperties.SessionTimeouts(Duration.ofDays(7), Duration.ofDays(30));
        AppProperties.SessionTimeouts staff = new AppProperties.SessionTimeouts(Duration.ofMinutes(30), Duration.ofHours(12));
        AppProperties.Security security = new AppProperties.Security(false, "sid",
                "dGVzdC1vbmx5LWVtYWlsLWhhc2gta2V5LTMyLWJ5dGVzISE=", customer, staff,
                new AppProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofMinutes(15)), 20, 120,
                Duration.ofMinutes(30), Duration.ofHours(24), false);
        AppProperties props = new AppProperties("http://localhost:3000", null, security, null, null, null,
                new AppProperties.Pricing(BigDecimal.ZERO), null, null, null);
        service = new SessionService(repository, new TokenHasher(props), props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void activeSessionAuthenticatesAndSlidesIdleExpiry() {
        AuthenticationSession s = session(NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(20)));
        when(repository.findForAuthentication(anyString())).thenReturn(Optional.of(s));

        assertThat(service.authenticate("token")).isPresent();
        assertThat(s.getLastSeenAt()).isEqualTo(NOW);
        assertThat(s.getIdleExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void idleExpiredSessionIsRejected() {
        AuthenticationSession s = session(NOW.minus(Duration.ofDays(8)), NOW.minusSeconds(1), NOW.plus(Duration.ofDays(20)));
        when(repository.findForAuthentication(anyString())).thenReturn(Optional.of(s));
        assertThat(service.authenticate("token")).isEmpty();
    }

    @Test
    void absoluteExpiryWinsOverSlidingExpiry() {
        AuthenticationSession s = session(NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(1)), NOW.plusSeconds(60));
        when(repository.findForAuthentication(anyString())).thenReturn(Optional.of(s));
        assertThat(service.authenticate("token")).isPresent();
        assertThat(s.getIdleExpiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void revokedSessionOrDisabledUserIsRejected() {
        AuthenticationSession revoked = session(NOW, NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2)));
        revoked.setRevokedAt(NOW.minusSeconds(5));
        when(repository.findForAuthentication(anyString())).thenReturn(Optional.of(revoked));
        assertThat(service.authenticate("token")).isEmpty();

        AuthenticationSession disabled = session(NOW, NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2)));
        disabled.getUser().setEnabled(false);
        when(repository.findForAuthentication(anyString())).thenReturn(Optional.of(disabled));
        assertThat(service.authenticate("token")).isEmpty();
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findForAuthentication(anyString())).thenReturn(Optional.empty());
        assertThat(service.authenticate("nope")).isEmpty();
    }

    private static AuthenticationSession session(Instant lastSeen, Instant idle, Instant absolute) {
        User u = new User();
        u.setEmail("a@test.local");
        u.setFirstName("A");
        u.setLastName("B");
        AuthenticationSession s = new AuthenticationSession();
        s.setUser(u);
        s.setLastSeenAt(lastSeen);
        s.setIdleExpiresAt(idle);
        s.setAbsoluteExpiresAt(absolute);
        return s;
    }
}
