package com.org.ecommerce.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.org.ecommerce.security.TokenHasher;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stripe PaymentIntents over the REST API (no SDK dependency). Webhooks are verified with the
 * Stripe-Signature scheme: HMAC-SHA256(secret, timestamp + "." + payload), 5-minute tolerance.
 */
public class StripePaymentProvider implements PaymentProvider {

    public static final String NAME = "STRIPE";
    private static final long TOLERANCE_SECONDS = 300;

    private final RestClient http;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StripePaymentProvider(String secretKey, String webhookSecret, ObjectMapper objectMapper, Clock clock) {
        this.http = RestClient.builder()
                .baseUrl("https://api.stripe.com/v1")
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build();
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CreatedPayment create(PaymentRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", toMinorUnits(request.amount()));
        form.add("currency", request.currency().toLowerCase(Locale.ROOT));
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("metadata[order_number]", request.orderNumber());
        JsonNode pi = post("/payment_intents", form, request.reference());
        return new CreatedPayment(pi.path("id").asString(), pi.path("client_secret").asString(), status(pi), null);
    }

    @Override
    public ProviderPayment fetch(String providerPaymentId) {
        try {
            String body = http.get().uri("/payment_intents/{id}", providerPaymentId).retrieve().body(String.class);
            return toPayment(objectMapper.readTree(body));
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Stripe request failed with status " + e.getStatusCode().value());
        }
    }

    @Override
    public RefundOutcome refund(String providerPaymentId, BigDecimal amount, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", providerPaymentId);
        form.add("amount", toMinorUnits(amount));
        try {
            JsonNode refund = post("/refunds", form, idempotencyKey);
            String status = refund.path("status").asString();
            return new RefundOutcome(refund.path("id").asString(),
                    "succeeded".equals(status) || "pending".equals(status), null);
        } catch (IllegalStateException e) {
            return new RefundOutcome(null, false, "The payment provider rejected the refund.");
        }
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(String payload, String signatureHeader) {
        verifySignature(payload, signatureHeader);
        JsonNode event = objectMapper.readTree(payload);
        String type = event.path("type").asString();
        if (!type.startsWith("payment_intent.")) {
            return Optional.empty();
        }
        return Optional.of(new WebhookEvent(event.path("id").asString(), type, toPayment(event.path("data").path("object"))));
    }

    void verifySignature(String payload, String header) {
        if (webhookSecret == null || webhookSecret.isBlank() || header == null) {
            throw new InvalidSignatureException("Missing webhook signature");
        }
        long timestamp = -1;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException ignored) {
                    // handled below
                }
            } else if (kv[0].equals("v1") && v1 == null) {
                v1 = kv[1];
            }
        }
        if (timestamp < 0 || v1 == null) {
            throw new InvalidSignatureException("Malformed webhook signature");
        }
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > TOLERANCE_SECONDS) {
            throw new InvalidSignatureException("Webhook timestamp outside tolerance");
        }
        String expected = TokenHasher.hmacSha256Hex(webhookSecret.getBytes(StandardCharsets.UTF_8), timestamp + "." + payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), v1.getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidSignatureException("Invalid webhook signature");
        }
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        try {
            String body = http.post().uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException e) {
            // Never log the response body: it can echo request data.
            throw new IllegalStateException("Stripe request failed with status " + e.getStatusCode().value());
        }
    }

    private ProviderPayment toPayment(JsonNode pi) {
        JsonNode error = pi.path("last_payment_error");
        JsonNode card = error.path("payment_method").path("card");
        return new ProviderPayment(pi.path("id").asString(), status(pi), pi.path("client_secret").asString(null),
                error.path("code").asString(null), error.path("message").asString(null),
                card.path("brand").asString(null), card.path("last4").asString(null));
    }

    private static Status status(JsonNode pi) {
        return switch (pi.path("status").asString("")) {
            case "succeeded" -> Status.SUCCEEDED;
            case "requires_action", "requires_confirmation" -> Status.REQUIRES_ACTION;
            case "canceled" -> Status.CANCELLED;
            case "requires_payment_method" -> pi.path("last_payment_error").isMissingNode()
                    || pi.path("last_payment_error").isNull() ? Status.PENDING : Status.FAILED;
            default -> Status.PENDING;
        };
    }

    /** Amount in the smallest currency unit (cents). Assumes a 2-decimal currency. */
    static String toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toBigIntegerExact().toString();
    }
}
