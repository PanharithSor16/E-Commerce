package com.org.ecommerce.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class PayWayPaymentProviderTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final String BASE = "https://checkout-sandbox.payway.com.kh";
    private static final String CHECK = BASE + "/api/payment-gateway/v1/payments/check-transaction-2";
    private static final String CLOSE = BASE + "/api/payment-gateway/v1/payments/close-transaction";
    private static final String CALLBACK = "https://shop.example.com/api/v1/payments/webhooks/payway";

    private MockRestServiceServer server;
    private PayWayPaymentProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new PayWayPaymentProvider("ec000002", "test-api-key", null, BASE + "/", CALLBACK, "",
                builder, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PaymentProvider.PaymentRequest request() {
        return new PaymentProvider.PaymentRequest("ORD-20260601-1", new BigDecimal("179.98"), "USD", "pay-42-1",
                "https://shop.example.com/checkout/payment/42", NOW.plusSeconds(30 * 60),
                new PaymentProvider.Payer("Ada", "Lovelace", "ada@example.com", "012 345 678"));
    }

    @Test
    void checkoutFormIsSignedInPayWayFieldOrder() {
        var created = provider.create(request());

        assertThat(created.providerPaymentId()).isEqualTo("pay-42-1");
        assertThat(created.status()).isEqualTo(PaymentProvider.Status.PENDING);
        var checkout = created.checkout();
        assertThat(checkout.actionUrl()).isEqualTo(BASE + "/api/payment-gateway/v1/payments/purchase");
        assertThat(checkout.fields())
                .containsEntry("req_time", "20260601120000")
                .containsEntry("amount", "179.98")
                .containsEntry("phone", "012345678")
                .containsEntry("lifetime", "30")
                .containsEntry("continue_success_url", "https://shop.example.com/checkout/payment/42?redirect_status=payway")
                .doesNotContainKey("payment_option"); // blank fields are neither sent nor hashed
        // Reference value computed independently with the aba-payway-sdk npm package (generatePurchaseHash).
        assertThat(checkout.fields().get("hash")).isEqualTo(
                "ZbW+fqLGI6IHypaOt1LJA5BP765m+fgx8hfabnU44nxnNZ8h7nvpPPRVAqcq96Ao53cnJ2s0HwzjquOMG4INmw==");
    }

    @Test
    void invalidTranIdIsRejected() {
        var r = request();
        var bad = new PaymentProvider.PaymentRequest(r.orderNumber(), r.amount(), r.currency(), "pay_42_1_way_too_long_x",
                r.returnUrl(), r.expiresAt(), r.payer());
        assertThatThrownBy(() -> provider.create(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchUsesSignedCheckTransactionAndMapsStatus() {
        server.expect(requestTo(CHECK)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tran_id").value("pay-42-1"))
                .andExpect(jsonPath("$.hash").value(
                        "F8y1RnzUeaySDpaV542ckt8Eer3mi7HmcOTeWXsCd2p/KEOA7qb1YwH9IfQ0xcebcJ520vESqZnUiqvcsLuJQg=="))
                .andRespond(withSuccess(approved(), MediaType.APPLICATION_JSON));

        var payment = provider.fetch("pay-42-1");

        assertThat(payment.status()).isEqualTo(PaymentProvider.Status.SUCCEEDED);
        server.verify();
    }

    @Test
    void pushbackIsConfirmedServerToServer() {
        // The pushback claims success, but PayWay says the payment was declined: the lookup wins.
        server.expect(requestTo(CHECK)).andRespond(withSuccess("""
                {"data":{"payment_status_code":3,"payment_status":"DECLINED"},"status":{"code":"00","tran_id":"pay-42-1"}}""",
                MediaType.APPLICATION_JSON));

        var event = provider.parseWebhook("{\"tran_id\":\"pay-42-1\",\"apv\":\"123456\",\"status\":0}", null);

        assertThat(event).isPresent();
        assertThat(event.get().eventId()).isEqualTo("pay-42-1:FAILED");
        assertThat(event.get().payment().status()).isEqualTo(PaymentProvider.Status.FAILED);
    }

    @Test
    void formEncodedPushbackIsAccepted() {
        server.expect(requestTo(CHECK)).andRespond(withSuccess(approved(), MediaType.APPLICATION_JSON));

        var event = provider.parseWebhook("tran_id=pay-42-1&status=0", null);

        assertThat(event).map(e -> e.payment().status()).contains(PaymentProvider.Status.SUCCEEDED);
    }

    @Test
    void pushbackWithoutValidTranIdIsRejected() {
        assertThatThrownBy(() -> provider.parseWebhook("{}", null))
                .isInstanceOf(PaymentProvider.InvalidSignatureException.class);
        assertThatThrownBy(() -> provider.parseWebhook("{\"tran_id\":\"../../etc\"}", null))
                .isInstanceOf(PaymentProvider.InvalidSignatureException.class);
    }

    @Test
    void unsubmittedAttemptIsResumedWithSameTranId() {
        server.expect(requestTo(CHECK)).andRespond(withSuccess("""
                {"status":{"code":"6","message":"Transaction not found"}}""", MediaType.APPLICATION_JSON));

        var pending = new PaymentProvider.ProviderPayment("pay-42-1", PaymentProvider.Status.PENDING, null, null, null, null, null);
        var resumed = provider.resume("pay-42-1", pending, request());

        assertThat(resumed).map(r -> r.checkout().fields().get("tran_id")).contains("pay-42-1");
    }

    @Test
    void submittedOpenAttemptIsClosedInsteadOfResumed() {
        server.expect(requestTo(CHECK)).andRespond(withSuccess("""
                {"data":{"payment_status_code":2,"payment_status":"PENDING"},"status":{"code":"00"}}""",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(CLOSE)).andRespond(withSuccess("{\"status\":{\"code\":\"00\"}}", MediaType.APPLICATION_JSON));

        var pending = new PaymentProvider.ProviderPayment("pay-42-1", PaymentProvider.Status.PENDING, null, null, null, null, null);

        assertThat(provider.resume("pay-42-1", pending, request())).isEmpty();
        server.verify();
    }

    @Test
    void refundWithoutRsaKeyFailsSafely() {
        var outcome = provider.refund("pay-42-1", new BigDecimal("10.00"), "refund-1");
        assertThat(outcome.succeeded()).isFalse();
    }

    @Test
    void amountsUseCurrencyPrecision() {
        assertThat(PayWayPaymentProvider.formatAmount(new BigDecimal("5"), "USD")).isEqualTo("5.00");
        assertThat(PayWayPaymentProvider.formatAmount(new BigDecimal("4000.00"), "KHR")).isEqualTo("4000");
    }

    private static String approved() {
        return """
                {"data":{"payment_status_code":0,"payment_status":"APPROVED","total_amount":179.98,"apv":"123456"},
                 "status":{"code":"00","message":"Success!","tran_id":"pay-42-1"}}""";
    }
}
