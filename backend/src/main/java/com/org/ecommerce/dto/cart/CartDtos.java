package com.org.ecommerce.dto.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddItemRequest(@NotNull @Positive Long variantId, @Min(1) @Max(99) int quantity) {
    }

    public record UpdateItemRequest(@Min(1) @Max(99) int quantity) {
    }

    public record ApplyCouponRequest(@NotBlank @Size(max = 40) String code) {
    }

    public record CartItemResponse(Long id, Long variantId, Long productId, String productName, String productSlug,
                                   String variantDescription, String sku, String imageUrl, BigDecimal unitPrice,
                                   int quantity, BigDecimal lineTotal, boolean available, int availableQuantity) {
    }

    /** All amounts are computed by the server from current catalog data. */
    public record CartResponse(UUID id, List<CartItemResponse> items, int itemCount, String couponCode,
                               BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingEstimate,
                               BigDecimal taxTotal, BigDecimal total, String currency, List<String> warnings) {
    }

    public record ShippingMethodResponse(String code, String name, String description, BigDecimal fee,
                                         BigDecimal freeShippingThreshold, Integer estimatedDaysMin,
                                         Integer estimatedDaysMax) {
    }
}
