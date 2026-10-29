package com.org.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.org.ecommerce.entity.ShippingMethod;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {

    List<ShippingMethod> findByActiveTrueOrderBySortOrderAsc();

    Optional<ShippingMethod> findByCodeAndActiveTrue(String code);
}
