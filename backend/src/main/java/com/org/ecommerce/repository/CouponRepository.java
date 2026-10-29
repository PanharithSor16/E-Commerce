package com.org.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /** Atomically consumes one use; returns 0 when the global usage limit has been reached. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Coupon c set c.timesUsed = c.timesUsed + 1, c.version = c.version + 1
            where c.id = :id and (c.usageLimit is null or c.timesUsed < c.usageLimit)
            """)
    int incrementUsage(@Param("id") Long couponId);

    @Modifying(flushAutomatically = true)
    @Query("update Coupon c set c.timesUsed = c.timesUsed - 1 where c.id = :id and c.timesUsed > 0")
    int decrementUsage(@Param("id") Long couponId);
}
