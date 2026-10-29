package com.org.ecommerce.service.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Development/test provider. Payments stay PENDING until the dev-only mock-confirm endpoint
 * completes them server-side, which exercises the same success/failure code paths as webhooks.
 */
public class MockPaymentProvider implements PaymentProvider {

    public static final String NAME = "MOCK";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CreatedPayment create(PaymentRequest request) {
        return new CreatedPayment("mock_pi_" + UUID.nameUUIDFromBytes(request.reference().getBytes()).toString().replace("-", ""),
                null, Status.PENDING, null);
    }

    @Override
    public ProviderPayment fetch(String providerPaymentId) {
        return new ProviderPayment(providerPaymentId, Status.PENDING, null, null, null, null, null);
    }

    @Override
    public RefundOutcome refund(String providerPaymentId, BigDecimal amount, String idempotencyKey) {
        return new RefundOutcome("mock_re_" + UUID.randomUUID().toString().replace("-", ""), true, null);
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(String payload, String signatureHeader) {
        throw new InvalidSignatureException("The mock provider does not accept webhooks");
    }
}
