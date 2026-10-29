package com.org.ecommerce.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application configuration bound from the {@code app.*} namespace.
 * Secrets arrive through environment variables only.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String publicBaseUrl,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Security security,
        BootstrapAdmin bootstrapAdmin,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Payment payment,
        @Valid @NotNull Pricing pricing,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Analytics analytics,
        @Valid @NotNull Retention retention) {

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Security(
            boolean cookieSecure,
            @NotBlank String sessionCookieName,
            @NotBlank String emailHashKey,
            @NotNull SessionTimeouts customerSession,
            @NotNull SessionTimeouts staffSession,
            @NotNull Lockout lockout,
            int authRateLimitPerMinute,
            int analyticsRateLimitPerMinute,
            @NotNull Duration passwordResetTtl,
            @NotNull Duration emailVerificationTtl,
            boolean requireVerifiedEmailForCheckout) {
    }

    public record SessionTimeouts(@NotNull Duration idleTimeout, @NotNull Duration absoluteTimeout) {
    }

    public record Lockout(int maxFailures, @NotNull Duration window, @NotNull Duration duration) {
    }

    public record BootstrapAdmin(String email, String password) {
    }

    public record Mail(@NotBlank String from) {
    }

    public record Payment(
            @NotBlank String provider,
            @NotBlank String currency,
            String stripeSecretKey,
            String stripeWebhookSecret,
            @NotNull Duration unpaidOrderTimeout,
            @Valid @NotNull PayWay payway) {
    }

    /** ABA PayWay. {@code callbackUrl} defaults to {@code <public-base-url>/api/v1/payments/webhooks/payway}. */
    public record PayWay(
            String merchantId,
            String apiKey,
            String rsaPublicKey,
            @NotBlank String baseUrl,
            String callbackUrl,
            String paymentOption) {
    }

    public record Pricing(@NotNull BigDecimal taxRate) {
    }

    public record Storage(@NotBlank String uploadDir, @NotBlank String publicPath) {
    }

    public record Analytics(
            @NotNull Duration sessionTimeout,
            @NotNull Duration activeVisitorWindow,
            @NotBlank String privacyPolicyVersion) {
    }

    public record Retention(
            @NotNull Duration analyticsEvents,
            @NotNull Duration visitorData,
            @NotNull Duration loginHistory,
            @NotNull Duration authSessions,
            @NotNull Duration userTokens,
            @NotNull Duration auditLogs,
            @NotNull Duration webhookEvents,
            @NotNull Duration anonymousCarts) {
    }
}
