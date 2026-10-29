package com.org.ecommerce.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.org.ecommerce.dto.admin.AdminDtos;
import com.org.ecommerce.dto.order.OrderDtos;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record Range(Instant from, Instant to) {
    }

    /** Financial figures are kept separate on purpose; see docs/ARCHITECTURE.md metric definitions. */
    public record Money(BigDecimal grossOrderValue, BigDecimal paidRevenue, BigDecimal refunded, BigDecimal netRevenue) {
    }

    public record Kpis(Money money, long ordersPlaced, long paidOrders, BigDecimal averageOrderValue,
                       long newCustomers, long uniqueVisitors, long sessions, long pageViews, long productViews,
                       double conversionRate) {
    }

    public record DailyPoint(LocalDate date, BigDecimal revenue, long orders) {
    }

    public record NamedCount(String name, long count) {
    }

    public record BestSeller(Long productId, String name, long unitsSold, BigDecimal revenue) {
    }

    public record FunnelStep(String step, long sessions, double rateFromStart, double rateFromPrevious) {
    }

    public record Funnel(List<FunnelStep> steps, double conversionRate, double cartAbandonmentEstimate) {
    }

    public record Overview(Range range, Kpis current, Kpis previous, long totalCustomers, long activeVisitors,
                           long activeSessions, List<DailyPoint> trend, List<BestSeller> bestSellers,
                           List<AdminDtos.LowStockItem> lowStock, List<OrderDtos.OrderSummary> recentOrders,
                           Funnel funnel, List<NamedCount> devices) {
    }

    public record VisitorDay(LocalDate date, long pageViews, long uniqueVisitors, long sessions, long newVisitors) {
    }

    public record Visitors(Range range, long uniqueVisitors, long sessions, long pageViews, long newVisitors,
                           List<VisitorDay> daily, List<NamedCount> trafficSources, List<NamedCount> devices,
                           List<NamedCount> browsers, List<NamedCount> topPages, List<NamedCount> campaigns,
                           List<NamedCount> referrers) {
    }

    public record ProductPerformance(Long productId, String name, String sku, long views, long uniqueViewers,
                                     long addToCarts, long unitsSold, BigDecimal revenue, double viewToPurchaseRate) {
    }

    public record SalesDay(LocalDate date, BigDecimal grossOrderValue, BigDecimal paidRevenue, BigDecimal refunded,
                           long orders, long paidOrders) {
    }

    public record Sales(Range range, Money money, long ordersPlaced, long paidOrders, BigDecimal averageOrderValue,
                        List<SalesDay> daily, List<NamedCount> ordersByStatus, List<BestSeller> topCategories) {
    }

    public record LoginDay(LocalDate date, long successes, long failures) {
    }

    public record Logins(Range range, long successes, long failures, long uniqueUsers, long activeSessions,
                         List<LoginDay> daily, List<NamedCount> failureReasons, List<NamedCount> devices,
                         List<AdminDtos.RepeatedFailure> repeatedFailures) {
    }
}
