package com.org.ecommerce.controller.admin;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.analytics.AnalyticsReportService;
import com.org.ecommerce.analytics.DateRange;
import com.org.ecommerce.analytics.ReportDtos;
import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.exception.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reports take either {@code range} (today, 7d, 30d, this_month, last_month) or {@code from}/{@code to}
 * ISO dates (inclusive). All windows are UTC days.
 */
@Tag(name = "Admin - Analytics")
@Validated
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsReportService reports;
    private final AuditService audit;
    private final Clock clock;

    public AdminAnalyticsController(AnalyticsReportService reports, AuditService audit, Clock clock) {
        this.reports = reports;
        this.audit = audit;
        this.clock = clock;
    }

    @Operation(summary = "Dashboard overview with previous-period comparison")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @GetMapping("/overview")
    public ReportDtos.Overview overview(@Parameter(description = "today|7d|30d|this_month|last_month") @RequestParam(required = false) String range,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.overview(DateRange.resolve(range, from, to, clock));
    }

    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @GetMapping("/visitors")
    public ReportDtos.Visitors visitors(@RequestParam(required = false) String range,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.visitors(DateRange.resolve(range, from, to, clock));
    }

    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @GetMapping("/products")
    public PageResponse<ReportDtos.ProductPerformance> products(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.products(DateRange.resolve(range, from, to, clock), page, size);
    }

    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @GetMapping("/conversion")
    public ReportDtos.Funnel conversion(@RequestParam(required = false) String range,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.funnel(DateRange.resolve(range, from, to, clock));
    }

    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @GetMapping("/sales")
    public ReportDtos.Sales sales(@RequestParam(required = false) String range,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.sales(DateRange.resolve(range, from, to, clock));
    }

    @Operation(summary = "Login attempts report (distinct from active sessions)")
    @PreAuthorize("hasAuthority('SECURITY_LOG_READ')")
    @GetMapping("/logins")
    public ReportDtos.Logins logins(@RequestParam(required = false) String range,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.logins(DateRange.resolve(range, from, to, clock));
    }

    @Operation(summary = "CSV export: report = sales | products | visitors")
    @PreAuthorize("hasAuthority('ANALYTICS_EXPORT')")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam String report,
                                         @RequestParam(required = false) String range,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange r = DateRange.resolve(range, from, to, clock);
        StringBuilder csv = new StringBuilder();
        switch (report) {
            case "sales" -> {
                csv.append("date,gross_order_value,paid_revenue,refunded,orders,paid_orders\n");
                reports.sales(r).daily().forEach(d -> csv.append(d.date()).append(',').append(d.grossOrderValue())
                        .append(',').append(d.paidRevenue()).append(',').append(d.refunded()).append(',')
                        .append(d.orders()).append(',').append(d.paidOrders()).append('\n'));
            }
            case "products" -> {
                csv.append("product_id,name,sku,views,unique_viewers,add_to_carts,units_sold,revenue,view_to_purchase_rate\n");
                reports.products(r, 0, 100).content().forEach(p -> csv.append(p.productId()).append(',')
                        .append(cell(p.name())).append(',').append(cell(p.sku())).append(',').append(p.views()).append(',')
                        .append(p.uniqueViewers()).append(',').append(p.addToCarts()).append(',').append(p.unitsSold())
                        .append(',').append(p.revenue()).append(',').append(p.viewToPurchaseRate()).append('\n'));
            }
            case "visitors" -> {
                csv.append("date,page_views,unique_visitors,sessions,new_visitors\n");
                reports.visitors(r).daily().forEach(d -> csv.append(d.date()).append(',').append(d.pageViews())
                        .append(',').append(d.uniqueVisitors()).append(',').append(d.sessions()).append(',')
                        .append(d.newVisitors()).append('\n'));
            }
            default -> throw ApiException.badRequest("INVALID_REPORT", "report must be sales, products or visitors");
        }
        audit.record("ANALYTICS_EXPORTED", "Report", report, Map.of("from", r.from().toString(), "to", r.to().toString()));
        String filename = report + "-" + LocalDate.ofInstant(r.from(), java.time.ZoneOffset.UTC) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Quotes a CSV cell and neutralizes spreadsheet formula injection (=, +, -, @). */
    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
