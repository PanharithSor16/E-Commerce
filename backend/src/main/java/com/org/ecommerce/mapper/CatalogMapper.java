package com.org.ecommerce.mapper;

import java.util.Comparator;
import java.util.List;

import com.org.ecommerce.dto.catalog.CatalogDtos;
import com.org.ecommerce.entity.Category;
import com.org.ecommerce.entity.InventoryTransaction;
import com.org.ecommerce.entity.Product;
import com.org.ecommerce.entity.ProductImage;
import com.org.ecommerce.entity.ProductVariant;

public final class CatalogMapper {

    /** Public responses never reveal exact stock above this number. */
    private static final int MAX_VISIBLE_QUANTITY = 20;

    private CatalogMapper() {
    }

    public static CatalogDtos.CategoryResponse toCategory(Category c) {
        return new CatalogDtos.CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(),
                c.getParent() == null ? null : c.getParent().getId(), c.getSortOrder(), c.isActive());
    }

    public static CatalogDtos.ProductSummary toSummary(Product p) {
        boolean inStock = p.getVariants().stream().anyMatch(v -> v.isActive() && v.getStockQuantity() > 0);
        return new CatalogDtos.ProductSummary(p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getBrand(),
                p.getShortDescription(), p.currentPrice(), p.getBasePrice(), p.getDiscountPrice(), p.getCurrency(),
                primaryImageUrl(p), p.getCategory().getId(), p.getCategory().getName(), inStock, p.isFeatured(),
                p.getPublishedAt());
    }

    public static CatalogDtos.ProductDetail toDetail(Product p) {
        List<CatalogDtos.VariantResponse> variants = p.getVariants().stream()
                .filter(ProductVariant::isActive)
                .map(v -> new CatalogDtos.VariantResponse(v.getId(), v.getSku(), v.getSize(), v.getColor(), v.unitPrice(),
                        v.getStockQuantity() > 0, v.getStockQuantity() > 0 && v.getStockQuantity() <= v.getLowStockThreshold(),
                        Math.min(v.getStockQuantity(), MAX_VISIBLE_QUANTITY)))
                .toList();
        return new CatalogDtos.ProductDetail(p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getBrand(),
                p.getShortDescription(), p.getDescription(), p.currentPrice(), p.getBasePrice(), p.getDiscountPrice(),
                p.getCurrency(), toCategory(p.getCategory()), variants, images(p),
                variants.stream().anyMatch(CatalogDtos.VariantResponse::inStock), p.getPublishedAt());
    }

    public static CatalogDtos.AdminProduct toAdmin(Product p) {
        List<CatalogDtos.AdminVariant> variants = p.getVariants().stream()
                .map(v -> new CatalogDtos.AdminVariant(v.getId(), v.getSku(), v.getSize(), v.getColor(),
                        v.getPriceOverride(), v.unitPrice(), v.getStockQuantity(), v.getLowStockThreshold(), v.isActive()))
                .toList();
        int totalStock = p.getVariants().stream().mapToInt(ProductVariant::getStockQuantity).sum();
        return new CatalogDtos.AdminProduct(p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getBrand(),
                p.getShortDescription(), p.getDescription(), p.getBasePrice(), p.getDiscountPrice(), p.getCurrency(),
                p.getStatus(), p.isFeatured(), p.getSoldCount(), p.getCategory().getId(), p.getCategory().getName(),
                variants, images(p), totalStock, p.getCreatedAt(), p.getUpdatedAt(), p.getPublishedAt());
    }

    public static CatalogDtos.InventoryEntry toInventory(InventoryTransaction t) {
        return new CatalogDtos.InventoryEntry(t.getId(), t.getChangeQuantity(), t.getQuantityAfter(), t.getReason(),
                t.getOrderId(), t.getNote(), t.getCreatedByUserId(), t.getCreatedAt());
    }

    public static String primaryImageUrl(Product p) {
        return p.getImages().stream()
                .min(Comparator.comparing((ProductImage i) -> !i.isPrimary()).thenComparing(ProductImage::getSortOrder))
                .map(ProductImage::getUrl)
                .orElse(null);
    }

    private static List<CatalogDtos.ImageResponse> images(Product p) {
        return p.getImages().stream()
                .map(i -> new CatalogDtos.ImageResponse(i.getId(), i.getUrl(), i.getAltText(), i.getSortOrder(),
                        i.isPrimary(), i.getVariantId()))
                .toList();
    }
}
