package com.org.ecommerce.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.catalog.CatalogDtos;
import com.org.ecommerce.entity.Category;
import com.org.ecommerce.entity.Enums.ProductStatus;
import com.org.ecommerce.entity.Product;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.mapper.CatalogMapper;
import com.org.ecommerce.repository.CategoryRepository;
import com.org.ecommerce.repository.ProductRepository;

/** Read-only storefront catalog: only ACTIVE products and active categories are visible. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    public record ProductQuery(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, String sort,
                               int page, int size) {
    }

    public List<CatalogDtos.CategoryResponse> categories() {
        return categories.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(CatalogMapper::toCategory).toList();
    }

    public CatalogDtos.CategoryResponse category(Long id) {
        return categories.findByIdAndActiveTrue(id).map(CatalogMapper::toCategory)
                .orElseThrow(() -> ApiException.notFound("Category"));
    }

    public PageResponse<CatalogDtos.ProductSummary> search(ProductQuery query) {
        Specification<Product> spec = (root, q, cb) -> cb.equal(root.get("status"), ProductStatus.ACTIVE);
        if (query.q() != null && !query.q().isBlank()) {
            String term = "%" + escapeLike(query.q().trim().toLowerCase(Locale.ROOT)) + "%";
            String sku = query.q().trim();
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), term, '\\'),
                    cb.like(cb.lower(root.get("brand")), term, '\\'),
                    cb.like(cb.lower(root.get("shortDescription")), term, '\\'),
                    cb.equal(root.get("sku"), sku)));
        }
        if (query.categoryId() != null) {
            List<Long> ids = categoryWithChildren(query.categoryId());
            spec = spec.and((root, q, cb) -> root.get("category").get("id").in(ids));
        }
        if (query.minPrice() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("effectivePrice"), query.minPrice()));
        }
        if (query.maxPrice() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("effectivePrice"), query.maxPrice()));
        }
        // Fetch the category in the same query for content queries (not for the count query).
        spec = spec.and((root, q, cb) -> {
            if (q != null && q.getResultType() != Long.class && q.getResultType() != long.class) {
                root.fetch("category", JoinType.INNER);
            }
            return cb.conjunction();
        });
        Page<Product> page = products.findAll(spec, PageResponse.pageable(query.page(), query.size(), sort(query.sort())));
        return PageResponse.of(page, CatalogMapper::toSummary);
    }

    public CatalogDtos.ProductDetail product(Long id) {
        Product p = products.findDetailedById(id)
                .filter(x -> x.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product"));
        return CatalogMapper.toDetail(p);
    }

    public CatalogDtos.HomeResponse home() {
        PageRequest eight = PageRequest.of(0, 8);
        return new CatalogDtos.HomeResponse(
                products.findByStatusAndFeaturedTrueOrderByPublishedAtDesc(ProductStatus.ACTIVE, eight).stream()
                        .map(CatalogMapper::toSummary).toList(),
                products.findByStatusOrderByPublishedAtDesc(ProductStatus.ACTIVE, eight).stream()
                        .map(CatalogMapper::toSummary).toList(),
                products.findByStatusOrderBySoldCountDesc(ProductStatus.ACTIVE, eight).stream()
                        .map(CatalogMapper::toSummary).toList(),
                categories());
    }

    private List<Long> categoryWithChildren(Long categoryId) {
        List<Category> all = categories.findByActiveTrueOrderBySortOrderAscNameAsc();
        List<Long> result = new ArrayList<>();
        result.add(categoryId);
        // Breadth-first over the (small) category tree.
        for (int i = 0; i < result.size(); i++) {
            Long parent = result.get(i);
            all.stream().filter(c -> c.getParent() != null && c.getParent().getId().equals(parent))
                    .map(Category::getId).forEach(result::add);
        }
        return result;
    }

    static Sort sort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id").descending());
        }
        return switch (sort) {
            case "price_asc" -> Sort.by("effectivePrice").ascending().and(Sort.by("id"));
            case "price_desc" -> Sort.by("effectivePrice").descending().and(Sort.by("id"));
            case "popular" -> Sort.by("soldCount").descending().and(Sort.by("id"));
            case "name" -> Sort.by("name").ascending();
            default -> Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id").descending());
        };
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
    }
}
