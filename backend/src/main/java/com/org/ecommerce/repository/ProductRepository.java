package com.org.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.Enums;
import com.org.ecommerce.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Images load lazily (batch fetch); fetching two List collections at once is not allowed. */
    @EntityGraph(attributePaths = {"category", "variants"})
    Optional<Product> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByIdAndStatus(Long id, Enums.ProductStatus status);

    Optional<Product> findBySlug(String slug);

    boolean existsBySku(String sku);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByStatusAndFeaturedTrueOrderByPublishedAtDesc(Enums.ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByStatusOrderByPublishedAtDesc(Enums.ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByStatusOrderBySoldCountDesc(Enums.ProductStatus status, Pageable pageable);

    @Modifying
    @Query("update Product p set p.soldCount = p.soldCount + :qty where p.id = :id")
    int incrementSoldCount(@Param("id") Long productId, @Param("qty") int quantity);

    @Modifying
    @Query("update Product p set p.soldCount = case when p.soldCount >= :qty then p.soldCount - :qty else 0 end where p.id = :id")
    int decrementSoldCount(@Param("id") Long productId, @Param("qty") int quantity);
}
