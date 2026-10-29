package com.org.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.org.ecommerce.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
}
