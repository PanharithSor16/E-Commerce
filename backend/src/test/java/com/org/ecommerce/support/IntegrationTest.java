package com.org.ecommerce.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.org.ecommerce.TestcontainersConfiguration;
import com.org.ecommerce.security.RateLimiter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base class for full-stack tests: real security filter chain, real SQL Server (Testcontainers),
 * real Flyway migrations. Helpers keep cookies and the CSRF token like a browser would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "Admin_Test_Passw0rd!";
    protected static final String PASSWORD = "Integration-Test-Pass-1";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimits() {
        rateLimiter.reset();
    }

    /** A cookie jar + CSRF token, standing in for one browser. */
    protected final class Browser {
        private final Map<String, Cookie> cookies = new java.util.HashMap<>();

        public Browser() {
            get("/api/v1/auth/csrf");
        }

        public MvcResult perform(MockHttpServletRequestBuilder request) {
            try {
                if (!cookies.isEmpty()) {
                    request.cookie(cookies.values().toArray(Cookie[]::new));
                }
                Cookie xsrf = cookies.get("XSRF-TOKEN");
                if (xsrf != null) {
                    request.header("X-XSRF-TOKEN", xsrf.getValue());
                }
                MvcResult result = mvc.perform(request).andReturn();
                for (Cookie c : result.getResponse().getCookies()) {
                    if (c.getMaxAge() == 0) {
                        cookies.remove(c.getName());
                    } else {
                        cookies.put(c.getName(), c);
                    }
                }
                return result;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public MvcResult postJson(String url, Object body) {
            return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
        }

        public MvcResult postJson(String url, Object body, String idempotencyKey) {
            return perform(post(url).contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey).content(json.writeValueAsString(body)));
        }

        public MvcResult get(String url) {
            return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
        }

        public void setCookie(String name, String value) {
            cookies.put(name, new Cookie(name, value));
        }

        public boolean hasCookie(String name) {
            return cookies.containsKey(name);
        }
    }

    protected JsonNode body(MvcResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected Browser admin() {
        Browser b = new Browser();
        int status = b.postJson("/api/v1/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .getResponse().getStatus();
        if (status != 200) {
            throw new IllegalStateException("Admin login failed: " + status);
        }
        return b;
    }

    protected Browser customer() {
        Browser b = new Browser();
        String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        int status = b.postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD,
                "firstName", "Test", "lastName", "Customer")).getResponse().getStatus();
        if (status != 201) {
            throw new IllegalStateException("Registration failed: " + status);
        }
        return b;
    }

    /** Creates an ACTIVE product with one default variant; returns {productId, variantId}. */
    protected long[] product(BigDecimal price, int stock) {
        Browser admin = admin();
        Long categoryId = jdbc.queryForObject("SELECT TOP 1 Id FROM dbo.Categories ORDER BY Id", Long.class);
        if (categoryId == null) {
            throw new IllegalStateException("no category");
        }
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult r = admin.postJson("/api/v1/admin/products", Map.of("categoryId", categoryId, "name", "Product " + sku,
                "sku", sku, "basePrice", price, "status", "ACTIVE", "featured", false, "initialStock", stock));
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Product creation failed: " + r.getResponse().getStatus());
        }
        JsonNode p = body(r);
        return new long[]{p.get("id").asLong(), p.get("variants").get(0).get("id").asLong()};
    }

    @BeforeEach
    void ensureCategory() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Categories", Integer.class);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO dbo.Categories (Name, Slug) VALUES (N'Test', 'test')");
        }
    }

    protected static Map<String, Object> address() {
        return Map.of("recipientName", "Test Customer", "line1", "1 Test Street", "city", "Testville",
                "countryCode", "US");
    }

    protected static Map<String, Object> checkout(String couponCode) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("shippingAddress", address());
        m.put("billingSameAsShipping", true);
        m.put("shippingMethodCode", "STANDARD");
        if (couponCode != null) {
            m.put("couponCode", couponCode);
        }
        return m;
    }
}
