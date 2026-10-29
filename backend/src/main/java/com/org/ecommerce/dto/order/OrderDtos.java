package com.org.ecommerce.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.org.ecommerce.entity.Enums.OrderStatus;
import com.org.ecommerce.entity.Enums.PaymentStatus;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record AddressInput(
            @NotBlank @Size(max = 200) String recipientName,
            @Size(max = 32) @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "must be a valid phone number") String phoneNumber,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter ISO country code") String countryCode) {
    }

    public record AddressResponse(Long id, String label, String recipientName, String phoneNumber, String line1,
                                  String line2, String city, String region, String postalCode, String countryCode,
                                  boolean defaultShipping, boolean defaultBilling) {
    }

    public record SavedAddressRequest(
            @Size(max = 50) String label,
            @NotNull @Valid AddressInput address,
            boolean defaultShipping,
            boolean defaultBilling) {
    }

    /** Prices, totals and payment status are never part of the request: the server computes them. */
    public record CheckoutRequest(
            Long shippingAddressId,
            @Valid AddressInput shippingAddress,
            boolean billingSameAsShipping,
            @Valid AddressInput billingAddress,
            @NotBlank @Size(max = 32) String shippingMethodCode,
            @Size(max = 40) String couponCode,
            @Size(max = 500) String note,
            boolean saveAddress) {
    }

    public record QuoteRequest(@NotBlank @Size(max = 32) String shippingMethodCode, @Size(max = 40) String couponCode) {
    }

    public record QuoteResponse(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingTotal,
                                BigDecimal taxTotal, BigDecimal grandTotal, String currency, String couponCode,
                                int itemCount) {
    }

    public record OrderItemResponse(Long id, Long productId, Long variantId, String productName,
                                    String variantDescription, String sku, BigDecimal unitPrice, int quantity,
                                    BigDecimal discountAmount, BigDecimal lineTotal) {
    }

    public record OrderAddressResponse(String type, String recipientName, String phoneNumber, String line1,
                                       String line2, String city, String region, String postalCode,
                                       String countryCode) {
    }

    public record StatusHistoryResponse(String statusType, String fromStatus, String toStatus, String note,
                                        Instant changedAt) {
    }

    public record PaymentResponse(Long id, String provider, String status, BigDecimal amount, String currency,
                                  String cardBrand, String cardLast4, String failureMessage, Instant createdAt,
                                  Instant completedAt) {
    }

    public record RefundResponse(Long id, BigDecimal amount, String reason, String status, Instant createdAt,
                                 Instant completedAt) {
    }

    public record OrderSummary(Long id, String orderNumber, OrderStatus status, PaymentStatus paymentStatus,
                               BigDecimal grandTotal, BigDecimal refundedTotal, String currency, int itemCount,
                               Instant placedAt, String customerEmail, String customerName) {
    }

    public record OrderDetail(Long id, String orderNumber, OrderStatus status, PaymentStatus paymentStatus,
                              String currency, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingTotal,
                              BigDecimal taxTotal, BigDecimal grandTotal, BigDecimal refundedTotal, String couponCode,
                              String shippingMethodName, String customerNote, List<OrderItemResponse> items,
                              List<OrderAddressResponse> addresses, List<StatusHistoryResponse> history,
                              List<PaymentResponse> payments, List<RefundResponse> refunds, Instant placedAt,
                              Instant paidAt, Instant shippedAt, Instant deliveredAt, Instant cancelledAt,
                              String cancelReason, boolean cancellable, String customerEmail, String customerName) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    /** {@code checkout} is set for hosted-page providers (ABA PayWay): the browser POSTs {@code fields} to {@code actionUrl}. */
    public record PaymentStartResponse(Long paymentId, String provider, String status, String clientSecret,
                                       String publishableKeyHint, PaymentCheckout checkout) {
    }

    public record PaymentCheckout(String actionUrl, java.util.Map<String, String> fields) {
    }

    public record MockPaymentRequest(boolean success) {
    }

    public record FulfillmentUpdateRequest(@NotNull OrderStatus status, @Size(max = 500) String note) {
    }

    public record RefundRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 255) String reason) {
    }
}
