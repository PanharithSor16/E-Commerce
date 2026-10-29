package com.org.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    Optional<Category> findByIdAndActiveTrue(Long id);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select count(p) from Product p where p.category.id = :id")
    long countProducts(@Param("id") Long categoryId);

    @Query("select count(c) from Category c where c.parent.id = :id")
    long countChildren(@Param("id") Long categoryId);
}
