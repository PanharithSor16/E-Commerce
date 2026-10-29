package com.org.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.org.ecommerce.repository.CouponRepository;
import com.org.ecommerce.repository.ProductVariantRepository;
import com.org.ecommerce.support.IntegrationTest;

/** Repository queries that carry business guarantees, tested against real SQL Server. */
class RepositoryIntegrationTest extends IntegrationTest {

    @Autowired
    ProductVariantRepository variants;
    @Autowired
    CouponRepository coupons;
    @Autowired
    TransactionTemplate tx;

    @Test
    void concurrentStockReservationsNeverOversell() throws Exception {
        long[] p = product(new BigDecimal("1.00"), 5);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            attempts.add(() -> tx.execute(s -> variants.decrementStock(p[1], 1)));
        }
        int succeeded = 0;
        for (Future<Integer> f : pool.invokeAll(attempts)) {
            succeeded += f.get();
        }
        pool.shutdown();
        assertThat(succeeded).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT StockQuantity FROM dbo.ProductVariants WHERE Id = ?", Integer.class, p[1]))
                .isZero();
    }

    @Test
    void couponUsageLimitIsEnforcedAtomically() {
        jdbc.update("INSERT INTO dbo.Coupons (Code, DiscountType, DiscountValue, UsageLimit) VALUES ('ONCE-ONLY-REPO', 'FIXED_AMOUNT', 1, 1)");
        Long id = jdbc.queryForObject("SELECT Id FROM dbo.Coupons WHERE Code = 'ONCE-ONLY-REPO'", Long.class);
        Integer first = tx.execute(s -> coupons.incrementUsage(id));
        Integer second = tx.execute(s -> coupons.incrementUsage(id));
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void lowStockQueryReturnsVariantsAtOrBelowThreshold() {
        long[] p = product(new BigDecimal("1.00"), 2);
        assertThat(variants.findLowStock(org.springframework.data.domain.PageRequest.of(0, 500)))
                .anyMatch(v -> v.getId() == p[1]);
    }
}
