package com.org.ecommerce.analytics;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.org.ecommerce.config.AppProperties;

/**
 * Nightly maintenance: roll up yesterday's analytics into daily summary tables, then purge data
 * past its retention period. Deletes run in small batches, each in its own transaction, to keep
 * locks short and transaction-log growth bounded.
 */
@Component
public class DataMaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(DataMaintenanceJobs.class);
    private static final int BATCH = 5000;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppProperties.Retention retention;
    private final Clock clock;

    public DataMaintenanceJobs(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx, AppProperties properties,
                               Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.retention = properties.retention();
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
    public void nightly() {
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
        rollup(yesterday);
        purge();
    }

    /** Idempotent: re-running for a day replaces that day's rows. */
    public void rollup(LocalDate day) {
        Map<String, Object> p = Map.of(
                "day", java.sql.Date.valueOf(day),
                "from", Timestamp.from(day.atStartOfDay().toInstant(ZoneOffset.UTC)),
                "to", Timestamp.from(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM dbo.AnalyticsDailySummary WHERE StatDate = :day", p);
            jdbc.update("""
                    INSERT INTO dbo.AnalyticsDailySummary (StatDate, EventType, DeviceCategory, TrafficSource,
                        EventCount, UniqueVisitors, UniqueSessions)
                    SELECT :day, e.EventType, COALESCE(s.DeviceCategory, e.DeviceCategory, 'UNKNOWN'),
                           COALESCE(s.TrafficSource, 'DIRECT'), COUNT(*), COUNT(DISTINCT e.VisitorId),
                           COUNT(DISTINCT e.VisitorSessionId)
                    FROM dbo.AnalyticsEvents e LEFT JOIN dbo.VisitorSessions s ON s.Id = e.VisitorSessionId
                    WHERE e.OccurredAt >= :from AND e.OccurredAt < :to
                    GROUP BY e.EventType, COALESCE(s.DeviceCategory, e.DeviceCategory, 'UNKNOWN'),
                             COALESCE(s.TrafficSource, 'DIRECT')
                    """, p);
            jdbc.update("DELETE FROM dbo.AnalyticsDailyProductStats WHERE StatDate = :day", p);
            jdbc.update("""
                    WITH ev AS (
                      SELECT ProductId,
                             SUM(CASE WHEN EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END) AS Views,
                             COUNT(DISTINCT CASE WHEN EventType = 'PRODUCT_VIEW' THEN VisitorId END) AS UniqueViewers,
                             SUM(CASE WHEN EventType = 'ADD_TO_CART' THEN 1 ELSE 0 END) AS AddToCarts
                      FROM dbo.AnalyticsEvents
                      WHERE ProductId IS NOT NULL AND OccurredAt >= :from AND OccurredAt < :to
                      GROUP BY ProductId),
                    sales AS (
                      SELECT oi.ProductId, COUNT(DISTINCT oi.OrderId) AS Purchases, SUM(oi.Quantity) AS Units,
                             SUM(oi.LineTotal - oi.DiscountAmount) AS Revenue
                      FROM dbo.OrderItems oi JOIN dbo.Orders o ON o.Id = oi.OrderId
                      WHERE o.PaidAt >= :from AND o.PaidAt < :to
                      GROUP BY oi.ProductId)
                    INSERT INTO dbo.AnalyticsDailyProductStats (StatDate, ProductId, Views, UniqueViewers, AddToCarts,
                        Purchases, UnitsSold, Revenue)
                    SELECT :day, COALESCE(ev.ProductId, sales.ProductId), COALESCE(ev.Views, 0),
                           COALESCE(ev.UniqueViewers, 0), COALESCE(ev.AddToCarts, 0), COALESCE(sales.Purchases, 0),
                           COALESCE(sales.Units, 0), COALESCE(sales.Revenue, 0)
                    FROM ev FULL OUTER JOIN sales ON sales.ProductId = ev.ProductId
                    """, p);
        });
        log.info("Analytics rollup completed for {}", day);
    }

    public void purge() {
        // Order matters: events -> sessions -> consent/visitors (foreign keys).
        purge("dbo.AnalyticsEvents", "OccurredAt", retention.analyticsEvents(), "");
        purge("dbo.VisitorSessions", "LastActivityAt", retention.visitorData(),
                "AND NOT EXISTS (SELECT 1 FROM dbo.AnalyticsEvents e WHERE e.VisitorSessionId = t.Id)");
        purge("dbo.UserConsent", "RecordedAt", retention.visitorData(), "AND t.UserId IS NULL");
        purge("dbo.Visitors", "LastSeenAt", retention.visitorData(),
                "AND NOT EXISTS (SELECT 1 FROM dbo.VisitorSessions s WHERE s.VisitorId = t.Id) "
                        + "AND NOT EXISTS (SELECT 1 FROM dbo.AnalyticsEvents e WHERE e.VisitorId = t.Id) "
                        + "AND NOT EXISTS (SELECT 1 FROM dbo.UserConsent c WHERE c.VisitorId = t.Id)");
        purge("dbo.LoginHistory", "OccurredAt", retention.loginHistory(), "");
        purge("dbo.AuthenticationSessions", "AbsoluteExpiresAt", retention.authSessions(), "");
        purge("dbo.UserTokens", "ExpiresAt", retention.userTokens(), "");
        purge("dbo.AuditLogs", "OccurredAt", retention.auditLogs(), "");
        purge("dbo.PaymentWebhookEvents", "ReceivedAt", retention.webhookEvents(), "");
        // Abandoned anonymous carts (items cascade).
        purge("dbo.Carts", "UpdatedAt", retention.anonymousCarts(), "AND t.UserId IS NULL");
    }

    private void purge(String table, String column, Duration keep, String extraCondition) {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(keep));
        String sql = "DELETE TOP (" + BATCH + ") t FROM " + table + " t WHERE t." + column + " < :cutoff " + extraCondition;
        long total = 0;
        int deleted;
        do {
            Integer n = tx.execute(s -> jdbc.update(sql, Map.of("cutoff", cutoff)));
            deleted = n == null ? 0 : n;
            total += deleted;
        } while (deleted == BATCH);
        if (total > 0) {
            log.info("Retention: deleted {} rows from {}", total, table);
        }
    }
}
