package com.org.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.org.ecommerce.support.IntegrationTest;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginAndMeSucceed_andLoginIsRecorded() {
        String email = "auth" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        Browser b = new Browser();
        MvcResult reg = b.postJson("/api/v1/auth/register",
                Map.of("email", email.toUpperCase(), "password", PASSWORD, "firstName", "Ann", "lastName", "Lee"));
        assertThat(reg.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(reg).get("email").asString()).isEqualTo(email); // normalized
        assertThat(b.hasCookie("sid")).isTrue();
        assertThat(reg.getResponse().getHeader("Set-Cookie")).contains("HttpOnly").contains("SameSite=Lax");

        Browser other = new Browser();
        assertThat(other.postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(body(other.get("/api/v1/auth/me")).get("roles").toString()).contains("ROLE_CUSTOMER");

        Integer successes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.LoginHistory h JOIN dbo.Users u ON u.Id = h.UserId
                WHERE u.Email = ? AND h.Status = 'SUCCESS'""", Integer.class, email);
        assertThat(successes).isEqualTo(2); // registration auto-login + explicit login
    }

    @Test
    void failedLoginIsGenericAndRecordedWithoutSecrets() {
        Browser b = customer();
        String email = body(b.get("/api/v1/auth/me")).get("email").asString();
        Browser attacker = new Browser();
        MvcResult wrong = attacker.postJson("/api/v1/auth/login", Map.of("email", email, "password", "not-the-password"));
        MvcResult unknown = attacker.postJson("/api/v1/auth/login",
                Map.of("email", "nobody-" + UUID.randomUUID() + "@test.local", "password", "whatever-password"));
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(wrong).get("detail").asString()).isEqualTo(body(unknown).get("detail").asString());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT TOP 1 h.FailureReason, h.EmailMasked, h.EmailHash FROM dbo.LoginHistory h
                JOIN dbo.Users u ON u.Id = h.UserId WHERE u.Email = ? AND h.Status = 'FAILURE' ORDER BY h.Id DESC""", email);
        assertThat(row.get("FailureReason")).isEqualTo("BAD_CREDENTIALS");
        assertThat((String) row.get("EmailMasked")).doesNotContain(email).contains("***@");
        assertThat((String) row.get("EmailHash")).hasSize(64).doesNotContain("@");
    }

    @Test
    void accountLocksAfterRepeatedFailures() {
        Browser b = customer();
        String email = body(b.get("/api/v1/auth/me")).get("email").asString();
        Browser attacker = new Browser();
        for (int i = 0; i < 5; i++) {
            attacker.postJson("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password-" + i));
        }
        // Even the correct password is refused while locked.
        MvcResult locked = attacker.postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT CASE WHEN LockedUntil > SYSUTCDATETIME() THEN 1 ELSE 0 END FROM dbo.Users WHERE Email = ?",
                Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.LoginHistory h JOIN dbo.Users u ON u.Id = h.UserId
                WHERE u.Email = ? AND h.FailureReason = 'ACCOUNT_LOCKED'""", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void expiredSessionIsRejected() {
        Browser b = customer();
        assertThat(b.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(200);
        String email = body(b.get("/api/v1/auth/me")).get("email").asString();
        jdbc.update("""
                UPDATE s SET IdleExpiresAt = DATEADD(minute, -1, SYSUTCDATETIME())
                FROM dbo.AuthenticationSessions s JOIN dbo.Users u ON u.Id = s.UserId WHERE u.Email = ?""", email);
        MvcResult after = b.get("/api/v1/auth/me");
        assertThat(after.getResponse().getStatus()).isEqualTo(401);
        assertThat(b.hasCookie("sid")).isFalse(); // stale cookie is cleared
    }

    @Test
    void logoutRevokesTheSession() {
        Browser b = customer();
        assertThat(b.perform(post("/api/v1/auth/logout")).getResponse().getStatus()).isEqualTo(204);
        assertThat(b.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void userCanRevokeOtherSessions() {
        String email = "multi" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        Browser phone = new Browser();
        phone.postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "firstName", "M", "lastName", "D"));
        Browser laptop = new Browser();
        laptop.postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(body(laptop.get("/api/v1/account/sessions")).size()).isEqualTo(2);

        laptop.perform(post("/api/v1/account/sessions/revoke-others"));
        assertThat(phone.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);
        assertThat(laptop.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@test.local\",\"password\":\"x\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(r.getResponse().getContentAsString()).contains("CSRF_INVALID");
    }

    @Test
    void validationErrorsAreReportedPerField() {
        MvcResult r = new Browser().postJson("/api/v1/auth/register",
                Map.of("email", "not-an-email", "password", "short", "firstName", "", "lastName", "X"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        String fields = body(r).get("errors").toString();
        assertThat(fields).contains("email").contains("password").contains("firstName");
        assertThat(body(r).get("traceId").asString()).isNotBlank();
    }

    @Test
    void invalidResetTokenIsRejected() {
        MvcResult r = new Browser().postJson("/api/v1/auth/reset-password",
                Map.of("token", "made-up-token", "newPassword", "Brand-New-Password-1"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(r).get("code").asString()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherAccountExists() {
        Browser b = new Browser();
        assertThat(b.postJson("/api/v1/auth/forgot-password", Map.of("email", "nobody@test.local"))
                .getResponse().getStatus()).isEqualTo(202);
        assertThat(b.postJson("/api/v1/auth/forgot-password", Map.of("email", ADMIN_EMAIL))
                .getResponse().getStatus()).isEqualTo(202);
    }
}
