package com.org.ecommerce.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.org.ecommerce.service.payment.MockPaymentProvider;
import com.org.ecommerce.service.payment.PayWayPaymentProvider;
import com.org.ecommerce.service.payment.PaymentProvider;
import com.org.ecommerce.service.payment.StripePaymentProvider;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class PaymentConfig {

    @Bean
    PaymentProvider paymentProvider(AppProperties properties, ObjectMapper objectMapper, Clock clock) {
        AppProperties.Payment p = properties.payment();
        if (StripePaymentProvider.NAME.equalsIgnoreCase(p.provider())) {
            if (p.stripeSecretKey() == null || p.stripeSecretKey().isBlank()
                    || p.stripeWebhookSecret() == null || p.stripeWebhookSecret().isBlank()) {
                throw new IllegalStateException("STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET are required for PAYMENT_PROVIDER=STRIPE");
            }
            return new StripePaymentProvider(p.stripeSecretKey(), p.stripeWebhookSecret(), objectMapper, clock);
        }
        if (PayWayPaymentProvider.NAME.equalsIgnoreCase(p.provider())) {
            AppProperties.PayWay pw = p.payway();
            if (pw.merchantId() == null || pw.merchantId().isBlank() || pw.apiKey() == null || pw.apiKey().isBlank()) {
                throw new IllegalStateException("PAYWAY_MERCHANT_ID and PAYWAY_API_KEY are required for PAYMENT_PROVIDER=PAYWAY");
            }
            String callbackUrl = pw.callbackUrl() == null || pw.callbackUrl().isBlank()
                    ? properties.publicBaseUrl().replaceAll("/+$", "") + "/api/v1/payments/webhooks/payway"
                    : pw.callbackUrl();
            return new PayWayPaymentProvider(pw.merchantId(), pw.apiKey(), pw.rsaPublicKey(), pw.baseUrl(), callbackUrl,
                    pw.paymentOption(), RestClient.builder(), objectMapper, clock);
        }
        return new MockPaymentProvider();
    }
}
