package com.org.ecommerce.controller.admin;

import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.Enums.OrderStatus;
import com.org.ecommerce.entity.Enums.PaymentStatus;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.service.OrderService;
import com.org.ecommerce.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - Orders")
@Validated
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public AdminOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PreAuthorize("hasAuthority('ORDER_READ')")
    @GetMapping
    public PageResponse<OrderDtos.OrderSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var filter = new OrderService.AdminFilter(q, status, paymentStatus,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        return orderService.adminList(filter, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "placedAt")));
    }

    @PreAuthorize("hasAuthority('ORDER_READ')")
    @GetMapping("/{id}")
    public OrderDtos.OrderDetail get(@PathVariable Long id) {
        return orderService.adminDetail(id);
    }

    @Operation(summary = "Advance fulfillment status (CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED)")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    @PostMapping("/{id}/status")
    public OrderDtos.OrderDetail updateStatus(@PathVariable Long id, @Valid @RequestBody OrderDtos.FulfillmentUpdateRequest body) {
        orderService.updateFulfillment(id, body.status(), body.note(), CurrentUser.requireId());
        return orderService.adminDetail(id);
    }

    @Operation(summary = "Cancel an order (paid orders are refunded in full)")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    @PostMapping("/{id}/cancel")
    public OrderDtos.OrderDetail cancel(@PathVariable Long id, @Valid @RequestBody(required = false) OrderDtos.CancelRequest body) {
        orderService.cancelByStaff(id, body == null ? null : body.reason(), CurrentUser.requireId());
        return orderService.adminDetail(id);
    }

    @Operation(summary = "Refund part or all of a paid order", description = "Requires an Idempotency-Key header.")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    @PostMapping("/{id}/refunds")
    public OrderDtos.OrderDetail refund(@PathVariable Long id, @Valid @RequestBody OrderDtos.RefundRequest body,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (key == null || !key.matches("^[A-Za-z0-9-]{8,40}$")) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "An Idempotency-Key header is required.");
        }
        orderService.refund(id, body.amount(), body.reason(), CurrentUser.requireId(), key);
        return orderService.adminDetail(id);
    }

}
