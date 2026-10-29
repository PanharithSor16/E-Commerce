package com.org.ecommerce.controller;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.cart.CartDtos;
import com.org.ecommerce.dto.catalog.CatalogDtos;
import com.org.ecommerce.repository.ShippingMethodRepository;
import com.org.ecommerce.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Catalog")
@Validated
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;
    private final ShippingMethodRepository shippingMethods;

    public CatalogController(CatalogService catalog, ShippingMethodRepository shippingMethods) {
        this.catalog = catalog;
        this.shippingMethods = shippingMethods;
    }

    @Operation(summary = "Homepage sections: featured, new arrivals, best sellers, categories")
    @GetMapping("/products/home")
    public CatalogDtos.HomeResponse home() {
        return catalog.home();
    }

    @Operation(summary = "Search and filter active products",
            description = "sort: newest (default), price_asc, price_desc, popular, name")
    @GetMapping("/products")
    public PageResponse<CatalogDtos.ProductSummary> products(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return catalog.search(new CatalogService.ProductQuery(q, categoryId, minPrice, maxPrice, sort, page, size));
    }

    @Operation(summary = "Product details (active products only)")
    @GetMapping("/products/{id}")
    public ResponseEntity<CatalogDtos.ProductDetail> product(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(catalog.product(id));
    }

    @GetMapping("/categories")
    public List<CatalogDtos.CategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/categories/{id}")
    public CatalogDtos.CategoryResponse category(@PathVariable Long id) {
        return catalog.category(id);
    }

    @Operation(summary = "Products in a category (including sub-categories)")
    @GetMapping("/categories/{id}/products")
    public PageResponse<CatalogDtos.ProductSummary> categoryProducts(
            @PathVariable Long id,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        catalog.category(id); // 404 for unknown/inactive categories
        return catalog.search(new CatalogService.ProductQuery(null, id, minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/shipping-methods")
    public List<CartDtos.ShippingMethodResponse> shippingMethods() {
        return shippingMethods.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(m -> new CartDtos.ShippingMethodResponse(m.getCode(), m.getName(), m.getDescription(), m.getFee(),
                        m.getFreeShippingThreshold(), m.getEstimatedDaysMin(), m.getEstimatedDaysMax()))
                .toList();
    }
}
