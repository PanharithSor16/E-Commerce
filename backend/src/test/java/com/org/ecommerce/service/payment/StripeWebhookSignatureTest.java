package com.org.ecommerce.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.org.ecommerce.security.TokenHasher;

import tools.jackson.databind.json.JsonMapper;

class StripeWebhookSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private final StripePaymentProvider provider = new StripePaymentProvider("sk_test", SECRET,
            JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    private static final String PAYLOAD = """
            {"id":"evt_1","type":"payment_intent.succeeded","data":{"object":{"id":"pi_1","status":"succeeded"}}}""";

    @Test
    void validSignatureIsAcceptedAndParsed() {
        var event = provider.parseWebhook(PAYLOAD, header(NOW.getEpochSecond(), sign(NOW.getEpochSecond(), PAYLOAD)));
        assertThat(event).isPresent();
        assertThat(event.get().eventId()).isEqualTo("evt_1");
        assertThat(event.get().payment().providerPaymentId()).isEqualTo("pi_1");
        assertThat(event.get().payment().status()).isEqualTo(PaymentProvider.Status.SUCCEEDED);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String sig = sign(NOW.getEpochSecond(), PAYLOAD);
        String tampered = PAYLOAD.replace("pi_1", "pi_2");
        assertThatThrownBy(() -> provider.parseWebhook(tampered, header(NOW.getEpochSecond(), sig)))
                .isInstanceOf(PaymentProvider.InvalidSignatureException.class);
    }

    @Test
    void oldTimestampIsRejected() {
        long old = NOW.getEpochSecond() - 600;
        assertThatThrownBy(() -> provider.parseWebhook(PAYLOAD, header(old, sign(old, PAYLOAD))))
                .isInstanceOf(PaymentProvider.InvalidSignatureException.class);
    }

    @Test
    void missingOrMalformedHeaderIsRejected() {
        assertThatThrownBy(() -> provider.parseWebhook(PAYLOAD, null))
                .isInstanceOf(PaymentProvider.InvalidSignatureException.class);
        assertThatThrownBy(() -> provider.parseWebhook(PAYLOAD, "garbage"))
                .isInstanceOf(PaymentProvider.InvalidSignatureException.class);
    }

    @Test
    void amountsAreConvertedToMinorUnits() {
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("179.98"))).isEqualTo("17998");
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("5"))).isEqualTo("500");
    }

    private static String sign(long t, String payload) {
        return TokenHasher.hmacSha256Hex(SECRET.getBytes(StandardCharsets.UTF_8), t + "." + payload);
    }

    private static String header(long t, String v1) {
        return "t=" + t + ",v1=" + v1;
    }
}
