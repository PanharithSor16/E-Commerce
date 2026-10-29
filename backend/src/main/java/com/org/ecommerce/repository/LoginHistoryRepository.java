package com.org.ecommerce.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.LoginHistory;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long>, JpaSpecificationExecutor<LoginHistory> {

    /** Groups of repeated failures (by submitted email) since a point in time, most failures first. */
    @Query("""
            select h.emailHash as emailHash, max(h.emailMasked) as emailMasked, max(h.userId) as userId,
                   count(h) as failures, max(h.occurredAt) as lastAttemptAt,
                   count(distinct h.ipAddress) as distinctIps
            from LoginHistory h
            where h.status = com.org.ecommerce.entity.Enums.LoginStatus.FAILURE and h.occurredAt >= :since
            group by h.emailHash
            having count(h) >= :threshold
            order by count(h) desc
            """)
    List<RepeatedFailureView> findRepeatedFailures(@Param("since") Instant since, @Param("threshold") long threshold,
                                                   Pageable pageable);

    @Query("""
            select count(h) from LoginHistory h
            where h.status = :status and h.occurredAt >= :from and h.occurredAt < :to
            """)
    long countByStatusBetween(@Param("status") com.org.ecommerce.entity.Enums.LoginStatus status,
                              @Param("from") Instant from, @Param("to") Instant to);

    interface RepeatedFailureView {
        String getEmailHash();

        String getEmailMasked();

        Long getUserId();

        long getFailures();

        Instant getLastAttemptAt();

        long getDistinctIps();
    }
}
