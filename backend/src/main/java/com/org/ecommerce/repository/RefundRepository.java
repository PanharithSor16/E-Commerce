package com.org.ecommerce.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByCustomerOrderIdOrderByIdAsc(Long orderId);

    @Query("""
            select coalesce(sum(r.amount), 0) from Refund r
            where r.status = com.org.ecommerce.entity.Enums.RefundStatus.SUCCEEDED
              and r.completedAt >= :from and r.completedAt < :to
            """)
    BigDecimal sumRefundedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
