package com.org.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.org.ecommerce.analytics.AnalyticsEventService;
import com.org.ecommerce.entity.Enums.EventType;
import com.org.ecommerce.support.IntegrationTest;

import tools.jackson.databind.JsonNode;

class CheckoutIntegrationTest extends IntegrationTest {

    @Autowired
    AnalyticsEventService analytics;

    @Test
    void invalidProductIdIsRejected() {
        MvcResult r = new Browser().postJson("/api/v1/cart/items", Map.of("variantId", 99_999_999, "quantity", 1));
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void insufficientInventoryIsRejectedAtCartAndAtCheckout() {
        long[] p = product(new BigDecimal("10.00"), 3);
        Browser b = customer();
        assertThat(b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 4)).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 3)).getResponse().getStatus())
                .isEqualTo(200);
        // Someone else buys the stock in the meantime.
        jdbc.update("UPDATE dbo.ProductVariants SET StockQuantity = 1 WHERE Id = ?", p[1]);
        MvcResult order = b.postJson("/api/v1/orders", checkout(null), key());
        assertThat(order.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(order).get("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
        // Nothing was written: stock unchanged, no order.
        assertThat(jdbc.queryForObject("SELECT StockQuantity FROM dbo.ProductVariants WHERE Id = ?", Integer.class, p[1]))
                .isEqualTo(1);
    }

    @Test
    void invalidCouponIsRejected() {
        long[] p = product(new BigDecimal("10.00"), 5);
        Browser b = customer();
        b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 1));
        MvcResult r = b.postJson("/api/v1/cart/coupon", Map.of("code", "DOES-NOT-EXIST"));
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(r).get("code").asString()).isEqualTo("INVALID_COUPON");
        assertThat(b.postJson("/api/v1/orders", checkout("DOES-NOT-EXIST"), key()).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void totalsAreComputedServerSideIgnoringClientPrices() {
        long[] p = product(new BigDecimal("25.00"), 5);
        Browser b = customer();
        b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 2));
        Map<String, Object> req = new HashMap<>(checkout(null));
        req.put("grandTotal", "0.01");      // unknown fields are ignored
        req.put("paymentStatus", "PAID");
        MvcResult r = b.postJson("/api/v1/orders", req, key());
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode o = body(r);
        assertThat(o.get("subtotal").asString()).isEqualTo("50.00");
        assertThat(o.get("shippingTotal").asString()).isEqualTo("5.00");
        assertThat(o.get("grandTotal").asString()).isEqualTo("55.00");
        assertThat(o.get("paymentStatus").asString()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT StockQuantity FROM dbo.ProductVariants WHERE Id = ?", Integer.class, p[1]))
                .isEqualTo(3);
    }

    @Test
    void retriedCheckoutWithSameIdempotencyKeyReturnsSameOrder() {
        long[] p = product(new BigDecimal("12.00"), 5);
        Browser b = customer();
        b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 1));
        String key = key();
        JsonNode first = body(b.postJson("/api/v1/orders", checkout(null), key));
        JsonNode second = body(b.postJson("/api/v1/orders", checkout(null), key));
        assertThat(second.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(jdbc.queryForObject("SELECT StockQuantity FROM dbo.ProductVariants WHERE Id = ?", Integer.class, p[1]))
                .isEqualTo(4); // stock taken once
    }

    @Test
    void customerCannotAccessAnotherCustomersOrder() {
        long[] p = product(new BigDecimal("9.00"), 5);
        Browser owner = customer();
        owner.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 1));
        long orderId = body(owner.postJson("/api/v1/orders", checkout(null), key())).get("id").asLong();

        Browser intruder = customer();
        assertThat(intruder.get("/api/v1/orders/" + orderId).getResponse().getStatus()).isEqualTo(404);
        assertThat(intruder.perform(post("/api/v1/orders/" + orderId + "/cancel")).getResponse().getStatus()).isEqualTo(404);
        assertThat(intruder.perform(post("/api/v1/orders/" + orderId + "/payments")).getResponse().getStatus()).isEqualTo(404);
        assertThat(owner.get("/api/v1/orders/" + orderId).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void paymentFailureThenSuccess_andPurchaseIsRecordedExactlyOnce() {
        long[] p = product(new BigDecimal("40.00"), 5);
        Browser b = customer();
        UUID visitor = UUID.randomUUID();
        b.setCookie("vid", visitor.toString());
        b.setCookie("vsid", UUID.randomUUID().toString());
        b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 2));
        long orderId = body(b.postJson("/api/v1/orders", checkout(null), key())).get("id").asLong();

        assertThat(b.perform(post("/api/v1/orders/" + orderId + "/payments")).getResponse().getStatus()).isEqualTo(200);
        JsonNode failed = body(b.postJson("/api/v1/orders/" + orderId + "/payments/mock-confirm", Map.of("success", false)));
        assertThat(failed.get("paymentStatus").asString()).isEqualTo("FAILED");
        assertThat(failed.get("status").asString()).isEqualTo("PENDING");

        b.perform(post("/api/v1/orders/" + orderId + "/payments"));
        JsonNode paid = body(b.postJson("/api/v1/orders/" + orderId + "/payments/mock-confirm", Map.of("success", true)));
        assertThat(paid.get("paymentStatus").asString()).isEqualTo("PAID");
        assertThat(paid.get("status").asString()).isEqualTo("CONFIRMED");

        // A second confirmation and repeated server events must not create another purchase.
        assertThat(b.postJson("/api/v1/orders/" + orderId + "/payments/mock-confirm", Map.of("success", true))
                .getResponse().getStatus()).isEqualTo(422);
        analytics.recordServerEvent(EventType.PURCHASE, visitor, null, null, orderId);
        analytics.recordServerEvent(EventType.PURCHASE, null, null, null, orderId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AnalyticsEvents WHERE EventType = 'PURCHASE' AND OrderId = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT VisitorId FROM dbo.AnalyticsEvents WHERE EventType = 'PURCHASE' AND OrderId = ?",
                Long.class, orderId)).isNotNull(); // attributed via the consented visitor captured at checkout
        assertThat(b.perform(post("/api/v1/orders/" + orderId + "/payments")).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void cancellingAnUnpaidOrderRestoresStockAndCouponUse() {
        long[] p = product(new BigDecimal("30.00"), 5);
        String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Browser admin = admin();
        assertThat(admin.postJson("/api/v1/admin/coupons", Map.of("code", code, "discountType", "FIXED_AMOUNT",
                "discountValue", 5, "active", true, "usageLimit", 1)).getResponse().getStatus()).isEqualTo(201);

        Browser b = customer();
        b.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 2));
        JsonNode order = body(b.postJson("/api/v1/orders", checkout(code), key()));
        assertThat(order.get("discountTotal").asString()).isEqualTo("5.00");
        assertThat(jdbc.queryForObject("SELECT TimesUsed FROM dbo.Coupons WHERE Code = ?", Integer.class, code)).isEqualTo(1);

        JsonNode cancelled = body(b.perform(post("/api/v1/orders/" + order.get("id").asLong() + "/cancel")));
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT StockQuantity FROM dbo.ProductVariants WHERE Id = ?", Integer.class, p[1]))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT TimesUsed FROM dbo.Coupons WHERE Code = ?", Integer.class, code)).isEqualTo(0);
    }

    @Test
    void anonymousCartIsMergedIntoAccountOnLogin() {
        long[] p = product(new BigDecimal("15.00"), 10);
        String email = "merge" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        Browser reg = new Browser();
        reg.postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "firstName", "M", "lastName", "G"));
        reg.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 1));

        Browser anon = new Browser();
        anon.postJson("/api/v1/cart/items", Map.of("variantId", p[1], "quantity", 2));
        assertThat(anon.hasCookie("cart")).isTrue();
        anon.postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(anon.hasCookie("cart")).isFalse();
        assertThat(body(anon.get("/api/v1/cart")).get("itemCount").asInt()).isEqualTo(3);
    }

    private static String key() {
        return "test-" + UUID.randomUUID();
    }
}
