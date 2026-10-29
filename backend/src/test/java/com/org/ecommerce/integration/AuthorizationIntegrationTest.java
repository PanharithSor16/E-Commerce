package com.org.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.org.ecommerce.support.IntegrationTest;

/** Backend authorization is enforced regardless of what the frontend shows. */
class AuthorizationIntegrationTest extends IntegrationTest {

    @Test
    void anonymousUsersCannotReachAdminOrAccountEndpoints() throws Exception {
        assertThat(mvc.perform(get("/api/v1/admin/products")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/v1/orders")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/v1/account/sessions")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void customersAreForbiddenFromAdminEndpoints() {
        Browser customer = customer();
        assertThat(customer.get("/api/v1/admin/products").getResponse().getStatus()).isEqualTo(403);
        assertThat(customer.get("/api/v1/admin/login-history").getResponse().getStatus()).isEqualTo(403);
        assertThat(customer.get("/api/v1/admin/analytics/overview").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void staffPermissionsAreRestricted() {
        Browser staff = staff();
        // Allowed: catalog, orders, analytics
        assertThat(staff.get("/api/v1/admin/products").getResponse().getStatus()).isEqualTo(200);
        assertThat(staff.get("/api/v1/admin/orders").getResponse().getStatus()).isEqualTo(200);
        assertThat(staff.get("/api/v1/admin/analytics/overview?range=7d").getResponse().getStatus()).isEqualTo(200);
        // Denied: security logs, sessions, role assignment, exports, refunds
        assertThat(staff.get("/api/v1/admin/login-history").getResponse().getStatus()).isEqualTo(403);
        assertThat(staff.get("/api/v1/admin/sessions").getResponse().getStatus()).isEqualTo(403);
        assertThat(staff.get("/api/v1/admin/analytics/export?report=sales").getResponse().getStatus()).isEqualTo(403);
        assertThat(staff.postJson("/api/v1/admin/orders/1/refunds", Map.of("amount", 1), "refund-key-123")
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(staff.perform(put("/api/v1/admin/users/1/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ROLE_ADMIN\"]}")).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void adminCanAccessSecurityLogs() {
        Browser admin = admin();
        assertThat(admin.get("/api/v1/admin/login-history").getResponse().getStatus()).isEqualTo(200);
        assertThat(admin.get("/api/v1/admin/sessions").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void roleChangeTakesEffectOnlyAfterReLogin_andRevokesSessions() {
        Browser target = customer();
        Long userId = jdbc.queryForObject("SELECT Id FROM dbo.Users WHERE Email = ?", Long.class,
                body(target.get("/api/v1/auth/me")).get("email").asString());
        Browser admin = admin();
        assertThat(admin.perform(put("/api/v1/admin/users/" + userId + "/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ROLE_STAFF\"]}")).getResponse().getStatus()).isEqualTo(200);
        assertThat(target.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);
    }

    private Browser staff() {
        String email = "staff" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        Browser b = new Browser();
        b.postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "firstName", "S", "lastName", "T"));
        jdbc.update("""
                DELETE ur FROM dbo.UserRoles ur JOIN dbo.Users u ON u.Id = ur.UserId WHERE u.Email = ?;
                INSERT INTO dbo.UserRoles (UserId, RoleId)
                SELECT u.Id, r.Id FROM dbo.Users u CROSS JOIN dbo.Roles r WHERE u.Email = ? AND r.Name = 'ROLE_STAFF';
                """, email, email);
        Browser staff = new Browser();
        staff.postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        return staff;
    }
}
