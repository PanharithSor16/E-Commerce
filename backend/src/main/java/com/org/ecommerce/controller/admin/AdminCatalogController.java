package com.org.ecommerce.controller.admin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.catalog.CatalogDtos;
import com.org.ecommerce.entity.Enums.ProductStatus;
import com.org.ecommerce.mapper.CatalogMapper;
import com.org.ecommerce.repository.InventoryTransactionRepository;
import com.org.ecommerce.service.AdminCatalogService;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - Catalog")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final AdminCatalogService service;
    private final InventoryTransactionRepository ledger;

    public AdminCatalogController(AdminCatalogService service, InventoryTransactionRepository ledger) {
        this.service = service;
        this.ledger = ledger;
    }

    // ---------------------------------------------------------------- products

    @PreAuthorize("hasAuthority('CATALOG_READ')")
    @GetMapping("/products")
    public PageResponse<CatalogDtos.AdminProduct> products(@RequestParam(required = false) String q,
                                                           @RequestParam(required = false) ProductStatus status,
                                                           @RequestParam(required = false) Long categoryId,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listView(q, status, categoryId,
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @PreAuthorize("hasAuthority('CATALOG_READ')")
    @GetMapping("/products/{id}")
    public CatalogDtos.AdminProduct product(@PathVariable Long id) {
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.AdminProduct create(@Valid @RequestBody CatalogDtos.ProductUpsertRequest body) {
        Long id = service.create(body).getId();
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PutMapping("/products/{id}")
    public CatalogDtos.AdminProduct update(@PathVariable Long id, @Valid @RequestBody CatalogDtos.ProductUpsertRequest body) {
        service.update(id, body);
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) {
        service.archive(id);
    }

    // ---------------------------------------------------------------- variants & stock

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PostMapping("/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.AdminProduct addVariant(@PathVariable Long id, @Valid @RequestBody CatalogDtos.VariantUpsertRequest body) {
        service.addVariant(id, body);
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PutMapping("/products/{id}/variants/{variantId}")
    public CatalogDtos.AdminProduct updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                                                  @Valid @RequestBody CatalogDtos.VariantUpsertRequest body) {
        service.updateVariant(id, variantId, body);
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    @PostMapping("/products/{id}/variants/{variantId}/stock")
    public CatalogDtos.AdminProduct adjustStock(@PathVariable Long id, @PathVariable Long variantId,
                                                @Valid @RequestBody CatalogDtos.StockAdjustmentRequest body) {
        service.adjustStock(id, variantId, body);
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_READ')")
    @GetMapping("/variants/{variantId}/inventory")
    public PageResponse<CatalogDtos.InventoryEntry> inventory(@PathVariable Long variantId,
                                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(ledger.findByVariantIdOrderByCreatedAtDesc(variantId,
                PageResponse.pageable(page, size, Sort.unsorted())), CatalogMapper::toInventory);
    }

    // ---------------------------------------------------------------- images

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.AdminProduct uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                @RequestParam(required = false) String altText,
                                                @RequestParam(required = false) Long variantId) {
        service.addImage(id, file, altText, variantId);
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PatchMapping("/products/{id}/images/{imageId}")
    public CatalogDtos.AdminProduct updateImage(@PathVariable Long id, @PathVariable Long imageId,
                                                @Valid @RequestBody CatalogDtos.ImageUpdateRequest body) {
        service.updateImage(id, imageId, body);
        return service.view(id);
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @DeleteMapping("/products/{id}/images/{imageId}")
    public CatalogDtos.AdminProduct deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        service.deleteImage(id, imageId);
        return service.view(id);
    }

    // ---------------------------------------------------------------- categories

    @PreAuthorize("hasAuthority('CATALOG_READ')")
    @GetMapping("/categories")
    public List<CatalogDtos.CategoryResponse> categories() {
        return service.allCategories().stream().map(CatalogMapper::toCategory).toList();
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.CategoryResponse createCategory(@Valid @RequestBody CatalogDtos.CategoryUpsertRequest body) {
        return CatalogMapper.toCategory(service.createCategory(body));
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @PutMapping("/categories/{id}")
    public CatalogDtos.CategoryResponse updateCategory(@PathVariable Long id,
                                                       @Valid @RequestBody CatalogDtos.CategoryUpsertRequest body) {
        return CatalogMapper.toCategory(service.updateCategory(id, body));
    }

    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
    }
}
