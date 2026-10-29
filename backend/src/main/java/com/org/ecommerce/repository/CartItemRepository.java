package com.org.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.org.ecommerce.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
