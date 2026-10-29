package com.org.ecommerce.dto.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.org.ecommerce.entity.Enums.InventoryReason;
import com.org.ecommerce.entity.Enums.ProductStatus;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---------- Public responses ----------

    public record CategoryResponse(Long id, String name, String slug, String description, String imageUrl,
                                   Long parentId, int sortOrder, boolean active) {
    }

    public record ProductSummary(Long id, String name, String slug, String sku, String brand, String shortDescription,
                                 BigDecimal price, BigDecimal basePrice, BigDecimal discountPrice, String currency,
                                 String imageUrl, Long categoryId, String categoryName, boolean inStock,
                                 boolean featured, Instant publishedAt) {
    }

    public record VariantResponse(Long id, String sku, String size, String color, BigDecimal price, boolean inStock,
                                  boolean lowStock, int availableQuantity) {
    }

    public record ImageResponse(Long id, String url, String altText, int sortOrder, boolean primary, Long variantId) {
    }

    public record ProductDetail(Long id, String name, String slug, String sku, String brand, String shortDescription,
                                String description, BigDecimal price, BigDecimal basePrice, BigDecimal discountPrice,
                                String currency, CategoryResponse category, List<VariantResponse> variants,
                                List<ImageResponse> images, boolean inStock, Instant publishedAt) {
    }

    public record HomeResponse(List<ProductSummary> featured, List<ProductSummary> newArrivals,
                               List<ProductSummary> bestSellers, List<CategoryResponse> categories) {
    }

    // ---------- Admin ----------

    public record AdminVariant(Long id, String sku, String size, String color, BigDecimal priceOverride,
                               BigDecimal effectivePrice, int stockQuantity, int lowStockThreshold, boolean active) {
    }

    public record AdminProduct(Long id, String name, String slug, String sku, String brand, String shortDescription,
                               String description, BigDecimal basePrice, BigDecimal discountPrice, String currency,
                               ProductStatus status, boolean featured, int soldCount, Long categoryId,
                               String categoryName, List<AdminVariant> variants, List<ImageResponse> images,
                               int totalStock, Instant createdAt, Instant updatedAt, Instant publishedAt) {
    }

    public record ProductUpsertRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 220) @Pattern(regexp = "^[a-z0-9-]*$", message = "may contain lower-case letters, digits and hyphens") String slug,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain letters, digits, '.', '_' and '-'") String sku,
            @Size(max = 100) String brand,
            @Size(max = 500) String shortDescription,
            @Size(max = 20000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 13, fraction = 2) BigDecimal basePrice,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal discountPrice,
            @NotNull ProductStatus status,
            boolean featured,
            /** Only used on create: initial default variant stock when no variants are supplied. */
            @PositiveOrZero Integer initialStock,
            @Valid List<VariantUpsertRequest> variants) {
    }

    public record VariantUpsertRequest(
            Long id,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku,
            @Size(max = 50) String size,
            @Size(max = 50) String color,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal priceOverride,
            @Min(0) @Max(100000) Integer lowStockThreshold,
            Boolean active,
            /** Only used when creating a variant. */
            @PositiveOrZero @Max(1000000) Integer initialStock) {
    }

    public record StockAdjustmentRequest(
            @NotNull @Min(-1000000) @Max(1000000) Integer change,
            @NotNull InventoryReason reason,
            @Size(max = 255) String note) {
    }

    public record InventoryEntry(Long id, int changeQuantity, int quantityAfter, InventoryReason reason, Long orderId,
                                 String note, Long createdByUserId, Instant createdAt) {
    }

    public record CategoryUpsertRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 140) @Pattern(regexp = "^[a-z0-9-]*$") String slug,
            @Size(max = 1000) String description,
            @Size(max = 1024) String imageUrl,
            Long parentId,
            @Min(0) @Max(100000) int sortOrder,
            boolean active) {
    }

    public record ImageUpdateRequest(@Size(max = 255) String altText, @Min(0) @Max(1000) Integer sortOrder,
                                     Boolean primary) {
    }
}
