package com.org.ecommerce.service;

import java.text.Normalizer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.dto.catalog.CatalogDtos;
import com.org.ecommerce.entity.Category;
import com.org.ecommerce.entity.Enums.ProductStatus;
import com.org.ecommerce.entity.Product;
import com.org.ecommerce.entity.ProductImage;
import com.org.ecommerce.entity.ProductVariant;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.CategoryRepository;
import com.org.ecommerce.repository.ProductImageRepository;
import com.org.ecommerce.repository.ProductRepository;
import com.org.ecommerce.repository.ProductVariantRepository;
import com.org.ecommerce.security.CurrentUser;

/** Product, variant, image and category management for staff with CATALOG_WRITE. */
@Service
@Transactional
public class AdminCatalogService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final CategoryRepository categories;
    private final InventoryService inventory;
    private final ImageStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public AdminCatalogService(ProductRepository products, ProductVariantRepository variants,
                               ProductImageRepository images, CategoryRepository categories,
                               InventoryService inventory, ImageStorageService storage, AuditService audit, Clock clock) {
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.categories = categories;
        this.inventory = inventory;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ products

    @Transactional(readOnly = true)
    public Page<Product> list(String q, ProductStatus status, Long categoryId, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String term = "%" + CatalogService.escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), term, '\\'),
                    cb.like(cb.lower(root.get("sku")), term, '\\')));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        return products.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public com.org.ecommerce.dto.PageResponse<CatalogDtos.AdminProduct> listView(String q, ProductStatus status,
            Long categoryId, Pageable pageable) {
        return com.org.ecommerce.dto.PageResponse.of(list(q, status, categoryId, pageable),
                com.org.ecommerce.mapper.CatalogMapper::toAdmin);
    }

    /** Mapped inside a fresh read-only transaction so lazy images load and bulk stock updates are visible. */
    @Transactional(readOnly = true)
    public CatalogDtos.AdminProduct view(Long id) {
        return com.org.ecommerce.mapper.CatalogMapper.toAdmin(get(id));
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Product"));
    }

    public Product create(CatalogDtos.ProductUpsertRequest req) {
        if (products.existsBySku(req.sku())) {
            throw ApiException.conflict("SKU_TAKEN", "A product with this SKU already exists.");
        }
        Product p = new Product();
        apply(p, req);
        p.setSlug(uniqueProductSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : slugify(req.name()), null));
        products.save(p);

        Long userId = CurrentUser.idOrNull();
        if (req.variants() == null || req.variants().isEmpty()) {
            // Every product has at least one variant; a "default" one has no size/color.
            ProductVariant v = new ProductVariant();
            v.setProduct(p);
            v.setSku(p.getSku());
            int stock = req.initialStock() == null ? 0 : req.initialStock();
            v.setStockQuantity(stock);
            p.getVariants().add(v);
            variants.saveAndFlush(v);
            inventory.initial(v.getId(), stock, userId);
        } else {
            for (CatalogDtos.VariantUpsertRequest vr : req.variants()) {
                addVariant(p, vr);
            }
        }
        audit.record("PRODUCT_CREATED", "Product", p.getId(), Map.of("sku", p.getSku()));
        return p;
    }

    public Product update(Long id, CatalogDtos.ProductUpsertRequest req) {
        Product p = get(id);
        if (!p.getSku().equals(req.sku()) && products.existsBySku(req.sku())) {
            throw ApiException.conflict("SKU_TAKEN", "A product with this SKU already exists.");
        }
        var oldPrice = p.getBasePrice();
        var oldDiscount = p.getDiscountPrice();
        apply(p, req);
        if (req.slug() != null && !req.slug().isBlank() && !req.slug().equals(p.getSlug())) {
            p.setSlug(uniqueProductSlug(req.slug(), p.getId()));
        }
        if (oldPrice.compareTo(p.getBasePrice()) != 0 || !Objects.equals(oldDiscount, p.getDiscountPrice())) {
            audit.record("PRODUCT_PRICE_CHANGED", "Product", p.getId(), Map.of(
                    "oldBase", oldPrice.toPlainString(), "newBase", p.getBasePrice().toPlainString(),
                    "oldDiscount", String.valueOf(oldDiscount), "newDiscount", String.valueOf(p.getDiscountPrice())));
        }
        audit.record("PRODUCT_UPDATED", "Product", p.getId(), Map.of("status", p.getStatus().name()));
        return p;
    }

    /** Products are archived rather than deleted: orders and analytics keep referencing them. */
    public void archive(Long id) {
        Product p = get(id);
        p.setStatus(ProductStatus.ARCHIVED);
        p.setArchivedAt(clock.instant());
        p.setFeatured(false);
        audit.record("PRODUCT_ARCHIVED", "Product", id, Map.of());
    }

    private void apply(Product p, CatalogDtos.ProductUpsertRequest req) {
        Category category = categories.findById(req.categoryId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Category does not exist."));
        if (req.discountPrice() != null && req.discountPrice().compareTo(req.basePrice()) >= 0) {
            throw ApiException.badRequest("INVALID_DISCOUNT", "Discount price must be lower than the base price.");
        }
        p.setCategory(category);
        p.setName(req.name().trim());
        p.setSku(req.sku().trim());
        p.setBrand(blankToNull(req.brand()));
        p.setShortDescription(stripTags(req.shortDescription()));
        p.setDescription(stripTags(req.description()));
        p.setBasePrice(req.basePrice());
        p.setDiscountPrice(req.discountPrice());
        p.setFeatured(req.featured());
        if (req.status() == ProductStatus.ACTIVE && p.getPublishedAt() == null) {
            p.setPublishedAt(clock.instant());
        }
        if (req.status() != ProductStatus.ARCHIVED) {
            p.setArchivedAt(null);
        } else if (p.getArchivedAt() == null) {
            p.setArchivedAt(clock.instant());
        }
        p.setStatus(req.status());
    }

    // ------------------------------------------------------------------ variants

    public ProductVariant addVariant(Long productId, CatalogDtos.VariantUpsertRequest req) {
        return addVariant(get(productId), req);
    }

    private ProductVariant addVariant(Product p, CatalogDtos.VariantUpsertRequest req) {
        if (variants.existsBySku(req.sku())) {
            throw ApiException.conflict("SKU_TAKEN", "A variant with SKU " + req.sku() + " already exists.");
        }
        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        applyVariant(v, req);
        int stock = req.initialStock() == null ? 0 : req.initialStock();
        v.setStockQuantity(stock);
        p.getVariants().add(v);
        variants.saveAndFlush(v);
        inventory.initial(v.getId(), stock, CurrentUser.idOrNull());
        audit.record("VARIANT_CREATED", "ProductVariant", v.getId(), Map.of("sku", v.getSku()));
        return v;
    }

    public ProductVariant updateVariant(Long productId, Long variantId, CatalogDtos.VariantUpsertRequest req) {
        ProductVariant v = variants.findWithProductById(variantId)
                .filter(x -> x.getProduct().getId().equals(productId))
                .orElseThrow(() -> ApiException.notFound("Variant"));
        if (!v.getSku().equals(req.sku()) && variants.existsBySku(req.sku())) {
            throw ApiException.conflict("SKU_TAKEN", "A variant with SKU " + req.sku() + " already exists.");
        }
        applyVariant(v, req);
        audit.record("VARIANT_UPDATED", "ProductVariant", v.getId(), Map.of("sku", v.getSku()));
        return v;
    }

    private static void applyVariant(ProductVariant v, CatalogDtos.VariantUpsertRequest req) {
        v.setSku(req.sku().trim());
        v.setSize(blankToNull(req.size()));
        v.setColor(blankToNull(req.color()));
        v.setPriceOverride(req.priceOverride());
        if (req.lowStockThreshold() != null) {
            v.setLowStockThreshold(req.lowStockThreshold());
        }
        if (req.active() != null) {
            v.setActive(req.active());
        }
    }

    public int adjustStock(Long productId, Long variantId, CatalogDtos.StockAdjustmentRequest req) {
        variants.findWithProductById(variantId)
                .filter(x -> x.getProduct().getId().equals(productId))
                .orElseThrow(() -> ApiException.notFound("Variant"));
        int after = inventory.adjust(variantId, req.change(), req.reason(), req.note(), CurrentUser.idOrNull());
        audit.record("STOCK_ADJUSTED", "ProductVariant", variantId,
                Map.of("change", req.change(), "reason", req.reason().name(), "quantityAfter", after));
        return after;
    }

    // ------------------------------------------------------------------ images

    public ProductImage addImage(Long productId, MultipartFile file, String altText, Long variantId) {
        Product p = get(productId);
        if (variantId != null && p.getVariants().stream().noneMatch(v -> v.getId().equals(variantId))) {
            throw ApiException.badRequest("INVALID_VARIANT", "Variant does not belong to this product.");
        }
        if (p.getImages().size() >= 20) {
            throw ApiException.unprocessable("TOO_MANY_IMAGES", "A product can have at most 20 images.");
        }
        ImageStorageService.StoredImage stored = storage.store(file);
        ProductImage img = new ProductImage();
        img.setProduct(p);
        img.setVariantId(variantId);
        img.setUrl(stored.url());
        img.setStorageKey(stored.key());
        img.setContentType(stored.contentType());
        img.setSizeBytes(stored.sizeBytes());
        img.setAltText(altText == null ? p.getName() : altText.substring(0, Math.min(altText.length(), 255)));
        img.setSortOrder(p.getImages().size());
        img.setPrimary(p.getImages().stream().noneMatch(ProductImage::isPrimary));
        p.getImages().add(img);
        images.save(img);
        audit.record("PRODUCT_IMAGE_ADDED", "Product", productId, Map.of("imageId", String.valueOf(img.getId())));
        return img;
    }

    public ProductImage updateImage(Long productId, Long imageId, CatalogDtos.ImageUpdateRequest req) {
        Product p = get(productId);
        ProductImage img = images.findByIdAndProductId(imageId, productId).orElseThrow(() -> ApiException.notFound("Image"));
        if (req.altText() != null) {
            img.setAltText(req.altText());
        }
        if (req.sortOrder() != null) {
            img.setSortOrder(req.sortOrder());
        }
        if (Boolean.TRUE.equals(req.primary()) && !img.isPrimary()) {
            p.getImages().forEach(i -> i.setPrimary(false));
            images.flush(); // clear the old primary before setting the new one (filtered unique index)
            img.setPrimary(true);
        }
        return img;
    }

    public void deleteImage(Long productId, Long imageId) {
        Product p = get(productId);
        ProductImage img = images.findByIdAndProductId(imageId, productId).orElseThrow(() -> ApiException.notFound("Image"));
        boolean wasPrimary = img.isPrimary();
        p.getImages().remove(img);
        images.flush();
        storage.delete(img.getStorageKey());
        if (wasPrimary && !p.getImages().isEmpty()) {
            p.getImages().get(0).setPrimary(true);
        }
        audit.record("PRODUCT_IMAGE_DELETED", "Product", productId, Map.of("imageId", String.valueOf(imageId)));
    }

    // ------------------------------------------------------------------ categories

    @Transactional(readOnly = true)
    public List<Category> allCategories() {
        return categories.findAllByOrderBySortOrderAscNameAsc();
    }

    public Category createCategory(CatalogDtos.CategoryUpsertRequest req) {
        Category c = new Category();
        applyCategory(c, req);
        c.setSlug(uniqueCategorySlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : slugify(req.name()), null));
        categories.save(c);
        audit.record("CATEGORY_CREATED", "Category", c.getId(), Map.of("name", c.getName()));
        return c;
    }

    public Category updateCategory(Long id, CatalogDtos.CategoryUpsertRequest req) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        applyCategory(c, req);
        if (req.slug() != null && !req.slug().isBlank() && !req.slug().equals(c.getSlug())) {
            c.setSlug(uniqueCategorySlug(req.slug(), id));
        }
        audit.record("CATEGORY_UPDATED", "Category", id, Map.of("name", c.getName()));
        return c;
    }

    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (categories.countProducts(id) > 0 || categories.countChildren(id) > 0) {
            throw ApiException.conflict("CATEGORY_IN_USE",
                    "Category still has products or sub-categories. Deactivate it instead.");
        }
        categories.delete(c);
        audit.record("CATEGORY_DELETED", "Category", id, Map.of());
    }

    private void applyCategory(Category c, CatalogDtos.CategoryUpsertRequest req) {
        Category parent = null;
        if (req.parentId() != null) {
            parent = categories.findById(req.parentId())
                    .orElseThrow(() -> ApiException.badRequest("INVALID_PARENT", "Parent category does not exist."));
            // Prevent cycles: walk up from the new parent.
            for (Category x = parent; x != null; x = x.getParent()) {
                if (c.getId() != null && x.getId().equals(c.getId())) {
                    throw ApiException.badRequest("INVALID_PARENT", "A category cannot be its own ancestor.");
                }
            }
        }
        c.setParent(parent);
        c.setName(req.name().trim());
        c.setDescription(stripTags(req.description()));
        c.setImageUrl(blankToNull(req.imageUrl()));
        c.setSortOrder(req.sortOrder());
        c.setActive(req.active());
    }

    // ------------------------------------------------------------------ helpers

    /** Returns base, base-2, base-3 ... whichever is free (or already owned by selfId). */
    private String uniqueProductSlug(String base, Long selfId) {
        String slug = base;
        for (int i = 2; ; i++) {
            var owner = products.findBySlug(slug).map(Product::getId);
            if (owner.isEmpty() || owner.get().equals(selfId)) {
                return slug;
            }
            slug = base + "-" + i;
        }
    }

    private String uniqueCategorySlug(String base, Long selfId) {
        String slug = base;
        for (int i = 2; ; i++) {
            var owner = categories.findBySlug(slug).map(Category::getId);
            if (owner.isEmpty() || owner.get().equals(selfId)) {
                return slug;
            }
            slug = base + "-" + i;
        }
    }

    static String slugify(String input) {
        String s = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isEmpty()) {
            s = "item";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** Catalog text is plain text; any markup is removed on write (React escapes on render as well). */
    static String stripTags(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.replaceAll("<[^>]*>", "").trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
