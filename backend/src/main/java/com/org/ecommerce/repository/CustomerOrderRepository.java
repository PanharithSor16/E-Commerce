package com.org.ecommerce.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.CustomerOrder;
import com.org.ecommerce.entity.Enums;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    /** Ownership-scoped lookup: customers can only ever load their own orders. */
    @EntityGraph(attributePaths = {"items"})
    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"items", "user"})
    Optional<CustomerOrder> findDetailedById(Long id);

    Page<CustomerOrder> findByUserId(Long userId, Pageable pageable);

    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("""
            select o from CustomerOrder o
            where o.paymentStatus in :statuses and o.status = com.org.ecommerce.entity.Enums.OrderStatus.PENDING
              and o.placedAt < :cutoff
            """)
    List<CustomerOrder> findUnpaidBefore(@Param("statuses") List<Enums.PaymentStatus> statuses,
                                         @Param("cutoff") Instant cutoff, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<CustomerOrder> findTop10ByOrderByPlacedAtDesc();

    // ---- Dashboard aggregates (authoritative order data, never client events) ----

    @Query("""
            select count(o) from CustomerOrder o
            where o.placedAt >= :from and o.placedAt < :to
              and o.status <> com.org.ecommerce.entity.Enums.OrderStatus.CANCELLED
            """)
    long countPlacedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(o.grandTotal), 0) from CustomerOrder o
            where o.placedAt >= :from and o.placedAt < :to
              and o.status <> com.org.ecommerce.entity.Enums.OrderStatus.CANCELLED
            """)
    BigDecimal sumGrossBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from CustomerOrder o where o.paidAt >= :from and o.paidAt < :to")
    long countPaidBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(o.grandTotal), 0) from CustomerOrder o where o.paidAt >= :from and o.paidAt < :to")
    BigDecimal sumPaidBetween(@Param("from") Instant from, @Param("to") Instant to);
}
