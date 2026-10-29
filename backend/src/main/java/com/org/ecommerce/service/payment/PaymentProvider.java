package com.org.ecommerce.service.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Abstraction over the payment provider. Card data never touches this application: the browser
 * talks to the provider directly (e.g. Stripe Elements, or the ABA PayWay hosted checkout page)
 * using the client secret or signed checkout form returned here.
 */
public interface PaymentProvider {

    String name();

    CreatedPayment create(PaymentRequest request);

    /** Server-to-server status lookup (used to confirm a payment without trusting the browser). */
    ProviderPayment fetch(String providerPaymentId);

    /**
     * Returns what the browser needs to continue an attempt that is still open, or empty when the
     * provider cannot continue it and a new attempt must be created (the old one is closed first).
     */
    default Optional<CreatedPayment> resume(String providerPaymentId, ProviderPayment current, PaymentRequest request) {
        return Optional.of(new CreatedPayment(providerPaymentId, current.clientSecret(), current.status(), null));
    }

    RefundOutcome refund(String providerPaymentId, BigDecimal amount, String idempotencyKey);

    /** Verifies the webhook signature and parses it; empty if the event type is irrelevant. Throws if forged. */
    Optional<WebhookEvent> parseWebhook(String payload, String signatureHeader);

    /**
     * What the provider needs to take a payment. {@code reference} is unique per attempt and doubles
     * as the provider idempotency key; {@code returnUrl} is where the browser comes back to.
     */
    record PaymentRequest(String orderNumber, BigDecimal amount, String currency, String reference,
                          String returnUrl, Instant expiresAt, Payer payer) {
    }

    record Payer(String firstName, String lastName, String email, String phone) {
    }

    /** {@code checkout} is set by hosted-page providers: the browser POSTs these fields to the provider. */
    record CreatedPayment(String providerPaymentId, String clientSecret, Status status, Checkout checkout) {
    }

    record Checkout(String actionUrl, Map<String, String> fields) {
    }

    record ProviderPayment(String providerPaymentId, Status status, String clientSecret, String failureCode,
                           String failureMessage, String cardBrand, String cardLast4) {
    }

    record RefundOutcome(String providerRefundId, boolean succeeded, String failureMessage) {
    }

    record WebhookEvent(String eventId, String type, ProviderPayment payment) {
    }

    enum Status { PENDING, REQUIRES_ACTION, SUCCEEDED, FAILED, CANCELLED }

    class InvalidSignatureException extends RuntimeException {
        public InvalidSignatureException(String message) {
            super(message);
        }
    }
}
