package com.org.ecommerce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.analytics.AnalyticsContext;
import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.CustomerOrder;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.service.OrderService;
import com.org.ecommerce.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Customer orders. The user is always taken from the session; IDs of other users' orders return 404. */
@Tag(name = "Orders")
@Validated
@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Price the current cart for a shipping method and optional coupon")
    @PostMapping("/checkout/quote")
    public OrderDtos.QuoteResponse quote(@Valid @RequestBody OrderDtos.QuoteRequest body) {
        return orderService.quote(CurrentUser.requireId(), body);
    }

    @Operation(summary = "Place an order from the cart",
            description = "Requires an Idempotency-Key header; retries with the same key return the same order.")
    @PostMapping("/orders")
    public ResponseEntity<OrderDtos.OrderDetail> place(@Valid @RequestBody OrderDtos.CheckoutRequest body,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                       HttpServletRequest request) {
        Long userId = CurrentUser.requireId();
        CustomerOrder order = orderService.placeOrder(userId, body, key, AnalyticsContext.from(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.mineDetail(userId, order.getId()));
    }

    @GetMapping("/orders")
    public PageResponse<OrderDtos.OrderSummary> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return orderService.mineList(CurrentUser.requireId(),
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "placedAt")));
    }

    @GetMapping("/orders/{id}")
    public OrderDtos.OrderDetail get(@PathVariable Long id) {
        return orderService.mineDetail(CurrentUser.requireId(), id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderDtos.OrderDetail cancel(@PathVariable Long id, @Valid @RequestBody(required = false) OrderDtos.CancelRequest body) {
        Long userId = CurrentUser.requireId();
        orderService.cancelMine(userId, id, body == null ? null : body.reason());
        return orderService.mineDetail(userId, id);
    }

    @Operation(summary = "Start (or resume) payment for an order")
    @PostMapping("/orders/{id}/payments")
    public OrderDtos.PaymentStartResponse startPayment(@PathVariable Long id) {
        return paymentService.start(CurrentUser.requireId(), id);
    }

    @Operation(summary = "Re-check payment status with the provider (server-to-server)")
    @PostMapping("/orders/{id}/payments/refresh")
    public OrderDtos.OrderDetail refreshPayment(@PathVariable Long id) {
        Long userId = CurrentUser.requireId();
        paymentService.refreshFromProvider(userId, id);
        return orderService.mineDetail(userId, id);
    }

    @Operation(summary = "Development only: complete a MOCK payment as success or failure")
    @PostMapping("/orders/{id}/payments/mock-confirm")
    public OrderDtos.OrderDetail mockConfirm(@PathVariable Long id, @RequestBody OrderDtos.MockPaymentRequest body) {
        Long userId = CurrentUser.requireId();
        paymentService.mockComplete(userId, id, body.success());
        return orderService.mineDetail(userId, id);
    }

}
