package com.org.ecommerce.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.admin.AdminDtos;
import com.org.ecommerce.entity.Enums.LoginStatus;
import com.org.ecommerce.entity.Role;
import com.org.ecommerce.mapper.OrderMapper;
import com.org.ecommerce.repository.AuthenticationSessionRepository;
import com.org.ecommerce.repository.CustomerOrderRepository;
import com.org.ecommerce.repository.LoginHistoryRepository;
import com.org.ecommerce.repository.ProductVariantRepository;
import com.org.ecommerce.repository.RefundRepository;
import com.org.ecommerce.repository.UserRepository;

/**
 * Admin analytics. Every query aggregates in SQL over a bounded [from, to) window using the indexes
 * defined in V1/V5, returning small result sets; raw events are never loaded into memory.
 * Revenue comes from Orders/Refunds only. Visitor counts are COUNT(DISTINCT VisitorId).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerOrderRepository orders;
    private final RefundRepository refunds;
    private final UserRepository users;
    private final ProductVariantRepository variants;
    private final AuthenticationSessionRepository authSessions;
    private final LoginHistoryRepository loginHistory;
    private final AppProperties properties;
    private final Clock clock;

    public AnalyticsReportService(NamedParameterJdbcTemplate jdbc, CustomerOrderRepository orders,
                                  RefundRepository refunds, UserRepository users, ProductVariantRepository variants,
                                  AuthenticationSessionRepository authSessions, LoginHistoryRepository loginHistory,
                                  AppProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.refunds = refunds;
        this.users = users;
        this.variants = variants;
        this.authSessions = authSessions;
        this.loginHistory = loginHistory;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ overview

    public ReportDtos.Overview overview(DateRange range) {
        var now = clock.instant();
        var activeSince = now.minus(properties.analytics().activeVisitorWindow());
        Long activeVisitors = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT VisitorId) FROM dbo.VisitorSessions WHERE LastActivityAt >= :since",
                Map.of("since", Timestamp.from(activeSince)), Long.class);
        return new ReportDtos.Overview(rangeDto(range), kpis(range), kpis(range.previous()),
                users.countByRole(Role.CUSTOMER), activeVisitors == null ? 0 : activeVisitors,
                authSessions.countActive(now), revenueTrend(range), bestSellers(range, 5),
                lowStock(10), orders.findTop10ByOrderByPlacedAtDesc().stream()
                        .map(o -> OrderMapper.toSummary(o, true)).toList(),
                funnel(range), sessionsBy("DeviceCategory", range));
    }

    ReportDtos.Kpis kpis(DateRange r) {
        ReportDtos.Money money = money(r);
        long placed = orders.countPlacedBetween(r.from(), r.to());
        long paid = orders.countPaidBetween(r.from(), r.to());
        BigDecimal aov = paid == 0 ? BigDecimal.ZERO
                : money.paidRevenue().divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_EVEN);
        Map<String, Object> traffic = jdbc.queryForMap("""
                SELECT
                  (SELECT COUNT(DISTINCT VisitorId) FROM dbo.AnalyticsEvents
                     WHERE OccurredAt >= :from AND OccurredAt < :to AND VisitorId IS NOT NULL) AS UniqueVisitors,
                  (SELECT COUNT(*) FROM dbo.VisitorSessions WHERE StartedAt >= :from AND StartedAt < :to) AS Sessions,
                  (SELECT COUNT(*) FROM dbo.AnalyticsEvents
                     WHERE EventType = 'PAGE_VIEW' AND OccurredAt >= :from AND OccurredAt < :to) AS PageViews,
                  (SELECT COUNT(*) FROM dbo.AnalyticsEvents
                     WHERE EventType = 'PRODUCT_VIEW' AND OccurredAt >= :from AND OccurredAt < :to) AS ProductViews,
                  (SELECT COUNT(DISTINCT VisitorSessionId) FROM dbo.AnalyticsEvents
                     WHERE EventType = 'PURCHASE' AND OccurredAt >= :from AND OccurredAt < :to) AS PurchaseSessions
                """, params(r));
        long sessions = num(traffic.get("Sessions"));
        long purchaseSessions = num(traffic.get("PurchaseSessions"));
        return new ReportDtos.Kpis(money, placed, paid, aov,
                users.countByRoleCreatedBetween(Role.CUSTOMER, r.from(), r.to()),
                num(traffic.get("UniqueVisitors")), sessions, num(traffic.get("PageViews")),
                num(traffic.get("ProductViews")), rate(purchaseSessions, sessions));
    }

    ReportDtos.Money money(DateRange r) {
        BigDecimal gross = orders.sumGrossBetween(r.from(), r.to());
        BigDecimal paid = orders.sumPaidBetween(r.from(), r.to());
        BigDecimal refunded = refunds.sumRefundedBetween(r.from(), r.to());
        return new ReportDtos.Money(scale(gross), scale(paid), scale(refunded), scale(paid.subtract(refunded)));
    }

    List<ReportDtos.DailyPoint> revenueTrend(DateRange r) {
        Map<LocalDate, ReportDtos.DailyPoint> byDay = new java.util.TreeMap<>();
        for (LocalDate d = day(r.from()); d.isBefore(day(r.to())); d = d.plusDays(1)) {
            byDay.put(d, new ReportDtos.DailyPoint(d, BigDecimal.ZERO.setScale(2), 0));
        }
        jdbc.query("""
                SELECT CAST(PaidAt AS date) AS D, SUM(GrandTotal) AS Revenue, COUNT(*) AS Orders
                FROM dbo.Orders WHERE PaidAt >= :from AND PaidAt < :to
                GROUP BY CAST(PaidAt AS date)
                """, params(r), rs -> {
            LocalDate d = rs.getDate("D").toLocalDate();
            byDay.put(d, new ReportDtos.DailyPoint(d, scale(rs.getBigDecimal("Revenue")), rs.getLong("Orders")));
        });
        return new ArrayList<>(byDay.values());
    }

    List<ReportDtos.BestSeller> bestSellers(DateRange r, int limit) {
        MapSqlParameterSource p = params(r).addValue("limit", limit);
        return jdbc.query("""
                SELECT TOP (:limit) oi.ProductId, MAX(oi.ProductName) AS Name, SUM(oi.Quantity) AS Units,
                       SUM(oi.LineTotal - oi.DiscountAmount) AS Revenue
                FROM dbo.OrderItems oi JOIN dbo.Orders o ON o.Id = oi.OrderId
                WHERE o.PaidAt >= :from AND o.PaidAt < :to
                GROUP BY oi.ProductId
                ORDER BY Units DESC, Revenue DESC
                """, p, (rs, i) -> new ReportDtos.BestSeller(rs.getLong("ProductId"), rs.getString("Name"),
                rs.getLong("Units"), scale(rs.getBigDecimal("Revenue"))));
    }

    public List<AdminDtos.LowStockItem> lowStock(int limit) {
        return variants.findLowStock(PageRequest.of(0, limit)).stream()
                .map(v -> new AdminDtos.LowStockItem(v.getProduct().getId(), v.getProduct().getName(), v.getId(),
                        v.getSku(), v.describe(), v.getStockQuantity(), v.getLowStockThreshold()))
                .toList();
    }

    // ------------------------------------------------------------------ funnel

    /**
     * Funnel over visitor sessions that started in the window. A session counts for a step if it has
     * at least one event of that type. PURCHASE events come from the trusted payment workflow.
     */
    public ReportDtos.Funnel funnel(DateRange r) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) AS Visits, SUM(f.PV) AS ProductViews, SUM(f.ATC) AS AddToCarts,
                       SUM(f.BC) AS Checkouts, SUM(f.PU) AS Purchases
                FROM (
                  SELECT s.Id,
                    MAX(CASE WHEN e.EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END) AS PV,
                    MAX(CASE WHEN e.EventType = 'ADD_TO_CART' THEN 1 ELSE 0 END) AS ATC,
                    MAX(CASE WHEN e.EventType = 'BEGIN_CHECKOUT' THEN 1 ELSE 0 END) AS BC,
                    MAX(CASE WHEN e.EventType = 'PURCHASE' THEN 1 ELSE 0 END) AS PU
                  FROM dbo.VisitorSessions s
                  LEFT JOIN dbo.AnalyticsEvents e ON e.VisitorSessionId = s.Id
                    AND e.EventType IN ('PRODUCT_VIEW', 'ADD_TO_CART', 'BEGIN_CHECKOUT', 'PURCHASE')
                  WHERE s.StartedAt >= :from AND s.StartedAt < :to
                  GROUP BY s.Id) f
                """, params(r));
        long[] counts = {num(row.get("Visits")), num(row.get("ProductViews")), num(row.get("AddToCarts")),
                num(row.get("Checkouts")), num(row.get("Purchases"))};
        String[] names = {"Visit", "Product view", "Add to cart", "Begin checkout", "Purchase"};
        List<ReportDtos.FunnelStep> steps = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            steps.add(new ReportDtos.FunnelStep(names[i], counts[i], rate(counts[i], counts[0]),
                    i == 0 ? 1.0 : rate(counts[i], counts[i - 1])));
        }
        double abandonment = counts[2] == 0 ? 0 : 1.0 - rate(counts[4], counts[2]);
        return new ReportDtos.Funnel(steps, rate(counts[4], counts[0]), Math.max(0, abandonment));
    }

    // ------------------------------------------------------------------ visitors

    public ReportDtos.Visitors visitors(DateRange r) {
        MapSqlParameterSource p = params(r);
        Map<LocalDate, long[]> days = new java.util.TreeMap<>();
        for (LocalDate d = day(r.from()); d.isBefore(day(r.to())); d = d.plusDays(1)) {
            days.put(d, new long[4]);
        }
        jdbc.query("""
                SELECT CAST(OccurredAt AS date) AS D,
                       SUM(CASE WHEN EventType = 'PAGE_VIEW' THEN 1 ELSE 0 END) AS PageViews,
                       COUNT(DISTINCT VisitorId) AS Visitors
                FROM dbo.AnalyticsEvents WHERE OccurredAt >= :from AND OccurredAt < :to
                GROUP BY CAST(OccurredAt AS date)
                """, p, rs -> {
            long[] v = days.computeIfAbsent(rs.getDate("D").toLocalDate(), k -> new long[4]);
            v[0] = rs.getLong("PageViews");
            v[1] = rs.getLong("Visitors");
        });
        jdbc.query("""
                SELECT CAST(StartedAt AS date) AS D, COUNT(*) AS Sessions FROM dbo.VisitorSessions
                WHERE StartedAt >= :from AND StartedAt < :to GROUP BY CAST(StartedAt AS date)
                """, p, rs -> {
            days.computeIfAbsent(rs.getDate("D").toLocalDate(), k -> new long[4])[2] = rs.getLong("Sessions");
        });
        jdbc.query("""
                SELECT CAST(FirstSeenAt AS date) AS D, COUNT(*) AS NewVisitors FROM dbo.Visitors
                WHERE FirstSeenAt >= :from AND FirstSeenAt < :to GROUP BY CAST(FirstSeenAt AS date)
                """, p, rs -> {
            days.computeIfAbsent(rs.getDate("D").toLocalDate(), k -> new long[4])[3] = rs.getLong("NewVisitors");
        });
        List<ReportDtos.VisitorDay> daily = days.entrySet().stream()
                .map(e -> new ReportDtos.VisitorDay(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT (SELECT COUNT(DISTINCT VisitorId) FROM dbo.AnalyticsEvents
                          WHERE OccurredAt >= :from AND OccurredAt < :to AND VisitorId IS NOT NULL) AS Visitors,
                       (SELECT COUNT(*) FROM dbo.VisitorSessions WHERE StartedAt >= :from AND StartedAt < :to) AS Sessions,
                       (SELECT COUNT(*) FROM dbo.AnalyticsEvents WHERE EventType = 'PAGE_VIEW'
                          AND OccurredAt >= :from AND OccurredAt < :to) AS PageViews,
                       (SELECT COUNT(*) FROM dbo.Visitors WHERE FirstSeenAt >= :from AND FirstSeenAt < :to) AS NewVisitors
                """, p);
        return new ReportDtos.Visitors(rangeDto(r), num(totals.get("Visitors")), num(totals.get("Sessions")),
                num(totals.get("PageViews")), num(totals.get("NewVisitors")), daily,
                sessionsBy("TrafficSource", r), sessionsBy("DeviceCategory", r), sessionsBy("Browser", r),
                topPages(r), sessionsBy("UtmCampaign", r), sessionsBy("ReferrerHost", r));
    }

    /** Sessions grouped by an allowlisted VisitorSessions column. */
    private List<ReportDtos.NamedCount> sessionsBy(String column, DateRange r) {
        if (!List.of("TrafficSource", "DeviceCategory", "Browser", "UtmCampaign", "ReferrerHost").contains(column)) {
            throw new IllegalArgumentException(column);
        }
        return jdbc.query("SELECT TOP (10) COALESCE(" + column + ", '(none)') AS Name, COUNT(*) AS C "
                        + "FROM dbo.VisitorSessions WHERE StartedAt >= :from AND StartedAt < :to "
                        + (column.startsWith("Utm") || column.equals("ReferrerHost") ? "AND " + column + " IS NOT NULL " : "")
                        + "GROUP BY " + column + " ORDER BY C DESC",
                params(r), (rs, i) -> new ReportDtos.NamedCount(rs.getString("Name"), rs.getLong("C")));
    }

    private List<ReportDtos.NamedCount> topPages(DateRange r) {
        return jdbc.query("""
                SELECT TOP (10) PagePath AS Name, COUNT(*) AS C FROM dbo.AnalyticsEvents
                WHERE EventType = 'PAGE_VIEW' AND OccurredAt >= :from AND OccurredAt < :to AND PagePath IS NOT NULL
                GROUP BY PagePath ORDER BY C DESC
                """, params(r), (rs, i) -> new ReportDtos.NamedCount(rs.getString("Name"), rs.getLong("C")));
    }

    // ------------------------------------------------------------------ products

    public PageResponse<ReportDtos.ProductPerformance> products(DateRange r, int page, int size) {
        int safeSize = Math.clamp(size, 1, PageResponse.MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        MapSqlParameterSource p = params(r).addValue("offset", safePage * safeSize).addValue("size", safeSize);
        String base = """
                WITH ev AS (
                  SELECT ProductId,
                         SUM(CASE WHEN EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END) AS Views,
                         COUNT(DISTINCT CASE WHEN EventType = 'PRODUCT_VIEW' THEN VisitorId END) AS UniqueViewers,
                         SUM(CASE WHEN EventType = 'ADD_TO_CART' THEN 1 ELSE 0 END) AS AddToCarts
                  FROM dbo.AnalyticsEvents
                  WHERE ProductId IS NOT NULL AND OccurredAt >= :from AND OccurredAt < :to
                  GROUP BY ProductId),
                sales AS (
                  SELECT oi.ProductId, SUM(oi.Quantity) AS Units, SUM(oi.LineTotal - oi.DiscountAmount) AS Revenue,
                         COUNT(DISTINCT oi.OrderId) AS Orders
                  FROM dbo.OrderItems oi JOIN dbo.Orders o ON o.Id = oi.OrderId
                  WHERE o.PaidAt >= :from AND o.PaidAt < :to
                  GROUP BY oi.ProductId)
                """;
        Long total = jdbc.queryForObject(base + """
                SELECT COUNT(*) FROM dbo.Products p
                LEFT JOIN ev ON ev.ProductId = p.Id LEFT JOIN sales s ON s.ProductId = p.Id
                WHERE ev.ProductId IS NOT NULL OR s.ProductId IS NOT NULL
                """, p, Long.class);
        List<ReportDtos.ProductPerformance> rows = jdbc.query(base + """
                SELECT p.Id, p.Name, p.Sku, COALESCE(ev.Views, 0) AS Views, COALESCE(ev.UniqueViewers, 0) AS UniqueViewers,
                       COALESCE(ev.AddToCarts, 0) AS AddToCarts, COALESCE(s.Units, 0) AS Units,
                       COALESCE(s.Revenue, 0) AS Revenue, COALESCE(s.Orders, 0) AS Orders
                FROM dbo.Products p
                LEFT JOIN ev ON ev.ProductId = p.Id LEFT JOIN sales s ON s.ProductId = p.Id
                WHERE ev.ProductId IS NOT NULL OR s.ProductId IS NOT NULL
                ORDER BY Views DESC, Revenue DESC, p.Id
                OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, p, (rs, i) -> new ReportDtos.ProductPerformance(rs.getLong("Id"), rs.getString("Name"),
                rs.getString("Sku"), rs.getLong("Views"), rs.getLong("UniqueViewers"), rs.getLong("AddToCarts"),
                rs.getLong("Units"), scale(rs.getBigDecimal("Revenue")), rate(rs.getLong("Orders"), rs.getLong("UniqueViewers"))));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, safePage, safeSize, t, (int) Math.ceil(t / (double) safeSize));
    }

    // ------------------------------------------------------------------ sales

    public ReportDtos.Sales sales(DateRange r) {
        Map<LocalDate, ReportDtos.SalesDay> days = new java.util.TreeMap<>();
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        for (LocalDate d = day(r.from()); d.isBefore(day(r.to())); d = d.plusDays(1)) {
            days.put(d, new ReportDtos.SalesDay(d, zero, zero, zero, 0, 0));
        }
        MapSqlParameterSource p = params(r);
        jdbc.query("""
                SELECT CAST(PlacedAt AS date) AS D, SUM(GrandTotal) AS Gross, COUNT(*) AS Orders
                FROM dbo.Orders WHERE PlacedAt >= :from AND PlacedAt < :to AND Status <> 'CANCELLED'
                GROUP BY CAST(PlacedAt AS date)
                """, p, rs -> {
            LocalDate d = rs.getDate("D").toLocalDate();
            ReportDtos.SalesDay old = days.getOrDefault(d, new ReportDtos.SalesDay(d, zero, zero, zero, 0, 0));
            days.put(d, new ReportDtos.SalesDay(d, scale(rs.getBigDecimal("Gross")), old.paidRevenue(), old.refunded(),
                    rs.getLong("Orders"), old.paidOrders()));
        });
        jdbc.query("""
                SELECT CAST(PaidAt AS date) AS D, SUM(GrandTotal) AS Paid, COUNT(*) AS Orders
                FROM dbo.Orders WHERE PaidAt >= :from AND PaidAt < :to GROUP BY CAST(PaidAt AS date)
                """, p, rs -> {
            LocalDate d = rs.getDate("D").toLocalDate();
            ReportDtos.SalesDay old = days.getOrDefault(d, new ReportDtos.SalesDay(d, zero, zero, zero, 0, 0));
            days.put(d, new ReportDtos.SalesDay(d, old.grossOrderValue(), scale(rs.getBigDecimal("Paid")), old.refunded(),
                    old.orders(), rs.getLong("Orders")));
        });
        jdbc.query("""
                SELECT CAST(CompletedAt AS date) AS D, SUM(Amount) AS Refunded FROM dbo.Refunds
                WHERE Status = 'SUCCEEDED' AND CompletedAt >= :from AND CompletedAt < :to
                GROUP BY CAST(CompletedAt AS date)
                """, p, rs -> {
            LocalDate d = rs.getDate("D").toLocalDate();
            ReportDtos.SalesDay old = days.getOrDefault(d, new ReportDtos.SalesDay(d, zero, zero, zero, 0, 0));
            days.put(d, new ReportDtos.SalesDay(d, old.grossOrderValue(), old.paidRevenue(),
                    scale(rs.getBigDecimal("Refunded")), old.orders(), old.paidOrders()));
        });
        List<ReportDtos.NamedCount> byStatus = jdbc.query("""
                SELECT Status AS Name, COUNT(*) AS C FROM dbo.Orders WHERE PlacedAt >= :from AND PlacedAt < :to
                GROUP BY Status ORDER BY C DESC
                """, p, (rs, i) -> new ReportDtos.NamedCount(rs.getString("Name"), rs.getLong("C")));
        List<ReportDtos.BestSeller> categories = jdbc.query("""
                SELECT TOP (10) c.Id, c.Name, SUM(oi.Quantity) AS Units, SUM(oi.LineTotal - oi.DiscountAmount) AS Revenue
                FROM dbo.OrderItems oi JOIN dbo.Orders o ON o.Id = oi.OrderId
                JOIN dbo.Products pr ON pr.Id = oi.ProductId JOIN dbo.Categories c ON c.Id = pr.CategoryId
                WHERE o.PaidAt >= :from AND o.PaidAt < :to
                GROUP BY c.Id, c.Name ORDER BY Revenue DESC
                """, p, (rs, i) -> new ReportDtos.BestSeller(rs.getLong("Id"), rs.getString("Name"), rs.getLong("Units"),
                scale(rs.getBigDecimal("Revenue"))));
        ReportDtos.Money money = money(r);
        long placed = orders.countPlacedBetween(r.from(), r.to());
        long paid = orders.countPaidBetween(r.from(), r.to());
        BigDecimal aov = paid == 0 ? zero : money.paidRevenue().divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_EVEN);
        return new ReportDtos.Sales(rangeDto(r), money, placed, paid, aov, new ArrayList<>(days.values()), byStatus, categories);
    }

    // ------------------------------------------------------------------ logins

    /** Login attempts (LoginHistory) are reported separately from active authenticated sessions. */
    public ReportDtos.Logins logins(DateRange r) {
        MapSqlParameterSource p = params(r);
        Map<LocalDate, long[]> days = new java.util.TreeMap<>();
        for (LocalDate d = day(r.from()); d.isBefore(day(r.to())); d = d.plusDays(1)) {
            days.put(d, new long[2]);
        }
        jdbc.query("""
                SELECT CAST(OccurredAt AS date) AS D,
                       SUM(CASE WHEN Status = 'SUCCESS' THEN 1 ELSE 0 END) AS S,
                       SUM(CASE WHEN Status = 'FAILURE' THEN 1 ELSE 0 END) AS F
                FROM dbo.LoginHistory WHERE OccurredAt >= :from AND OccurredAt < :to
                GROUP BY CAST(OccurredAt AS date)
                """, p, rs -> {
            long[] v = days.computeIfAbsent(rs.getDate("D").toLocalDate(), k -> new long[2]);
            v[0] = rs.getLong("S");
            v[1] = rs.getLong("F");
        });
        List<ReportDtos.NamedCount> reasons = jdbc.query("""
                SELECT FailureReason AS Name, COUNT(*) AS C FROM dbo.LoginHistory
                WHERE Status = 'FAILURE' AND OccurredAt >= :from AND OccurredAt < :to
                GROUP BY FailureReason ORDER BY C DESC
                """, p, (rs, i) -> new ReportDtos.NamedCount(rs.getString("Name"), rs.getLong("C")));
        List<ReportDtos.NamedCount> devices = jdbc.query("""
                SELECT COALESCE(DeviceCategory, 'UNKNOWN') AS Name, COUNT(*) AS C FROM dbo.LoginHistory
                WHERE Status = 'SUCCESS' AND OccurredAt >= :from AND OccurredAt < :to
                GROUP BY DeviceCategory ORDER BY C DESC
                """, p, (rs, i) -> new ReportDtos.NamedCount(rs.getString("Name"), rs.getLong("C")));
        Long uniqueUsers = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT UserId) FROM dbo.LoginHistory
                WHERE Status = 'SUCCESS' AND OccurredAt >= :from AND OccurredAt < :to
                """, p, Long.class);
        List<AdminDtos.RepeatedFailure> repeated = loginHistory.findRepeatedFailures(r.from(), 3, PageRequest.of(0, 10))
                .stream().map(v -> new AdminDtos.RepeatedFailure(v.getEmailHash(), v.getEmailMasked(), v.getUserId(),
                        v.getFailures(), v.getLastAttemptAt(), v.getDistinctIps())).toList();
        return new ReportDtos.Logins(rangeDto(r),
                loginHistory.countByStatusBetween(LoginStatus.SUCCESS, r.from(), r.to()),
                loginHistory.countByStatusBetween(LoginStatus.FAILURE, r.from(), r.to()),
                uniqueUsers == null ? 0 : uniqueUsers, authSessions.countActive(clock.instant()),
                days.entrySet().stream().map(e -> new ReportDtos.LoginDay(e.getKey(), e.getValue()[0], e.getValue()[1])).toList(),
                reasons, devices, repeated);
    }

    // ------------------------------------------------------------------ helpers

    private static MapSqlParameterSource params(DateRange r) {
        return new MapSqlParameterSource().addValue("from", Timestamp.from(r.from())).addValue("to", Timestamp.from(r.to()));
    }

    private static ReportDtos.Range rangeDto(DateRange r) {
        return new ReportDtos.Range(r.from(), r.to());
    }

    private static LocalDate day(java.time.Instant i) {
        return LocalDate.ofInstant(i, java.time.ZoneOffset.UTC);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 10000.0 / whole) / 10000.0;
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_EVEN);
    }
}
