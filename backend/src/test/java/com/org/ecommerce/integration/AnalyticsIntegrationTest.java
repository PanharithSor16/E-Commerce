package com.org.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.org.ecommerce.support.IntegrationTest;

class AnalyticsIntegrationTest extends IntegrationTest {

    @Test
    void duplicateEventsAreIgnored() {
        UUID visitor = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> batch = batch(visitor, List.of(event(eventId, "PAGE_VIEW", "/products")));
        Browser b = new Browser();
        assertThat(body(b.postJson("/api/v1/analytics/events", batch)).get("accepted").asInt()).isEqualTo(1);
        assertThat(body(b.postJson("/api/v1/analytics/events", batch)).get("accepted").asInt()).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AnalyticsEvents WHERE EventId = ?", Integer.class, eventId))
                .isEqualTo(1);
    }

    @Test
    void serverOnlyEventsSensitivePathsAndUnknownProductsAreRejected() {
        UUID visitor = UUID.randomUUID();
        Map<String, Object> productView = event(UUID.randomUUID(), "PRODUCT_VIEW", "/products/x");
        productView.put("productId", 99_999_999);
        MvcResult r = new Browser().postJson("/api/v1/analytics/events", batch(visitor, List.of(
                event(UUID.randomUUID(), "PURCHASE", "/checkout"),
                event(UUID.randomUUID(), "LOGIN", "/"),
                event(UUID.randomUUID(), "PAGE_VIEW", "/account/orders"),
                event(UUID.randomUUID(), "HACKED", "/"),
                productView)));
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        assertThat(body(r).get("accepted").asInt()).isEqualTo(0);
        assertThat(body(r).get("rejected").asInt()).isEqualTo(5);
    }

    @Test
    void urlsAreSanitizedAndCampaignAttributed() {
        long[] p = product(new BigDecimal("5.00"), 1);
        UUID visitor = UUID.randomUUID();
        UUID pv = UUID.randomUUID();
        Map<String, Object> e = event(pv, "PAGE_VIEW", "/products/" + p[0] + "?reset_token=abc&email=x@y.z");
        e.put("referrer", "https://www.google.com/search?q=private+query");
        e.put("utmSource", "newsletter");
        e.put("utmMedium", "email");
        Map<String, Object> view = event(UUID.randomUUID(), "PRODUCT_VIEW", "/products/" + p[0]);
        view.put("productId", p[0]);
        new Browser().postJson("/api/v1/analytics/events", batch(visitor, List.of(e, view)));

        Map<String, Object> row = jdbc.queryForMap("SELECT PagePath, Referrer FROM dbo.AnalyticsEvents WHERE EventId = ?", pv);
        assertThat(row.get("PagePath")).isEqualTo("/products/" + p[0]);
        assertThat(row.get("Referrer")).isEqualTo("https://www.google.com/search");
        assertThat(jdbc.queryForObject("""
                SELECT s.TrafficSource FROM dbo.VisitorSessions s JOIN dbo.Visitors v ON v.Id = s.VisitorId
                WHERE v.VisitorKey = ?""", String.class, visitor)).isEqualTo("EMAIL");
    }

    @Test
    void oversizedBatchesAreRejected() {
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            events.add(event(UUID.randomUUID(), "PAGE_VIEW", "/"));
        }
        assertThat(new Browser().postJson("/api/v1/analytics/events", batch(UUID.randomUUID(), events))
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void reportsUseDistinctVisitorsNotEventCounts() {
        UUID visitor = UUID.randomUUID();
        Browser b = new Browser();
        for (int i = 0; i < 3; i++) {
            b.postJson("/api/v1/analytics/events", batch(visitor, List.of(event(UUID.randomUUID(), "PAGE_VIEW", "/p" + i))));
        }
        MvcResult r = admin().get("/api/v1/admin/analytics/visitors?range=today");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(r).get("pageViews").asLong()).isGreaterThanOrEqualTo(body(r).get("uniqueVisitors").asLong() + 2);
    }

    private static Map<String, Object> batch(UUID visitor, List<Map<String, Object>> events) {
        return Map.of("visitorId", visitor, "sessionId", UUID.randomUUID(), "events", events);
    }

    private static Map<String, Object> event(UUID id, String type, String path) {
        Map<String, Object> m = new HashMap<>();
        m.put("eventId", id);
        m.put("type", type);
        m.put("path", path);
        return m;
    }
}
