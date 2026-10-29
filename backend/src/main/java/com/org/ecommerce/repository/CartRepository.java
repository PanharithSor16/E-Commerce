package com.org.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.org.ecommerce.entity.Cart;
import com.org.ecommerce.entity.Enums;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product", "coupon"})
    Optional<Cart> findByUserIdAndStatus(Long userId, Enums.CartStatus status);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product", "coupon"})
    Optional<Cart> findByCartTokenHashAndStatus(String cartTokenHash, Enums.CartStatus status);
}
