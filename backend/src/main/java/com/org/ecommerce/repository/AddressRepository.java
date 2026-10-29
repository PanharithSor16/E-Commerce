package com.org.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.isDefaultShipping = false where a.user.id = :userId")
    void clearDefaultShipping(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.isDefaultBilling = false where a.user.id = :userId")
    void clearDefaultBilling(@Param("userId") Long userId);
}
