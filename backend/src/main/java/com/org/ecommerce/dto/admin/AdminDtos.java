package com.org.ecommerce.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.org.ecommerce.entity.Enums.DiscountType;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record CustomerSummary(Long id, UUID publicId, String email, String firstName, String lastName,
                                  boolean enabled, boolean emailVerified, boolean locked, Set<String> roles,
                                  Instant lastLoginAt, Instant createdAt) {
    }

    public record CustomerDetail(Long id, UUID publicId, String email, String firstName, String lastName,
                                 String phoneNumber, boolean enabled, boolean emailVerified, boolean locked,
                                 Instant lockedUntil, int failedLoginCount, Set<String> roles, Instant lastLoginAt,
                                 Instant createdAt, Instant deactivatedAt, long orderCount, int activeSessions) {
    }

    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 32) @Pattern(regexp = "^[0-9+()\\-\\s]*$") String phoneNumber) {
    }

    public record SetEnabledRequest(boolean enabled, @Size(max = 255) String reason) {
    }

    public record SetRolesRequest(@NotEmpty Set<@Pattern(regexp = "ROLE_(CUSTOMER|STAFF|ADMIN)") String> roles) {
    }

    public record LoginHistoryEntry(Long id, Long userId, String emailMasked, String status, String failureReason,
                                    Instant occurredAt, String ipAddress, String browser, String browserVersion,
                                    String operatingSystem, String deviceCategory, UUID sessionId) {
    }

    public record RepeatedFailure(String emailHash, String emailMasked, Long userId, long failures,
                                  Instant lastAttemptAt, long distinctIps) {
    }

    public record AdminSession(UUID id, Long userId, String userEmail, Instant createdAt, Instant lastSeenAt,
                               Instant expiresAt, String ipAddress, String browser, String operatingSystem,
                               String deviceCategory) {
    }

    public record AuditEntry(Long id, Long actorUserId, String action, String entityType, String entityId,
                             String outcome, String details, String ipAddress, Instant occurredAt) {
    }

    public record CouponResponse(Long id, String code, String description, DiscountType discountType,
                                 BigDecimal discountValue, BigDecimal minOrderAmount, BigDecimal maxDiscountAmount,
                                 Instant startsAt, Instant endsAt, Integer usageLimit, Integer perUserLimit,
                                 int timesUsed, boolean active) {
    }

    public record CouponRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
            @Size(max = 255) String description,
            @NotNull DiscountType discountType,
            @NotNull @DecimalMin("0.00") @DecimalMax("1000000") @Digits(integer = 13, fraction = 2) BigDecimal discountValue,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal minOrderAmount,
            @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal maxDiscountAmount,
            Instant startsAt,
            Instant endsAt,
            @Min(1) Integer usageLimit,
            @Min(1) Integer perUserLimit,
            boolean active) {
    }

    public record ShippingMethodRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal fee,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal freeShippingThreshold,
            Integer estimatedDaysMin,
            Integer estimatedDaysMax,
            boolean active) {
    }

    public record ShippingMethodAdmin(Long id, String code, String name, String description, BigDecimal fee,
                                      BigDecimal freeShippingThreshold, Integer estimatedDaysMin,
                                      Integer estimatedDaysMax, boolean active) {
    }

    public record LowStockItem(Long productId, String productName, Long variantId, String sku, String variant,
                               int stockQuantity, int lowStockThreshold) {
    }

    public record SettingsResponse(String paymentProvider, String currency, BigDecimal taxRate,
                                   boolean requireVerifiedEmailForCheckout, String privacyPolicyVersion,
                                   List<ShippingMethodAdmin> shippingMethods) {
    }
}
