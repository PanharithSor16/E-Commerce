package com.org.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Query("select v from ProductVariant v join fetch v.product p where v.id = :id")
    Optional<ProductVariant> findWithProductById(@Param("id") Long id);

    boolean existsBySku(String sku);

    /**
     * Atomically reserves stock. Returns 0 when there is not enough stock, so concurrent
     * checkouts can never oversell (the CHECK constraint is a second safety net).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ProductVariant v set v.stockQuantity = v.stockQuantity - :qty, v.version = v.version + 1
            where v.id = :id and v.active = true and v.stockQuantity >= :qty
            """)
    int decrementStock(@Param("id") Long variantId, @Param("qty") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update ProductVariant v set v.stockQuantity = v.stockQuantity + :qty, v.version = v.version + 1 where v.id = :id")
    int incrementStock(@Param("id") Long variantId, @Param("qty") int quantity);

    @Query("select v.stockQuantity from ProductVariant v where v.id = :id")
    int currentStock(@Param("id") Long variantId);

    @Query("""
            select v from ProductVariant v join fetch v.product p
            where v.active = true and p.status <> com.org.ecommerce.entity.Enums.ProductStatus.ARCHIVED
              and v.stockQuantity <= v.lowStockThreshold
            order by v.stockQuantity asc
            """)
    List<ProductVariant> findLowStock(Pageable pageable);
}
