package com.org.ecommerce.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.org.ecommerce.analytics.AnalyticsEventService;
import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.CustomerOrder;
import com.org.ecommerce.entity.Enums.AddressType;
import com.org.ecommerce.entity.Enums.EventType;
import com.org.ecommerce.entity.Enums.InventoryReason;
import com.org.ecommerce.entity.Enums.OrderStatus;
import com.org.ecommerce.entity.Enums.PaymentStatus;
import com.org.ecommerce.entity.Enums.PaymentTxStatus;
import com.org.ecommerce.entity.Enums.RefundStatus;
import com.org.ecommerce.entity.Enums.StatusType;
import com.org.ecommerce.entity.Enums.WebhookStatus;
import com.org.ecommerce.entity.OrderAddress;
import com.org.ecommerce.entity.OrderItem;
import com.org.ecommerce.entity.Payment;
import com.org.ecommerce.entity.PaymentWebhookEvent;
import com.org.ecommerce.entity.Refund;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.CustomerOrderRepository;
import com.org.ecommerce.repository.PaymentRepository;
import com.org.ecommerce.repository.PaymentWebhookEventRepository;
import com.org.ecommerce.repository.ProductRepository;
import com.org.ecommerce.repository.RefundRepository;
import com.org.ecommerce.service.payment.MockPaymentProvider;
import com.org.ecommerce.service.payment.PaymentProvider;

/**
 * Payment lifecycle. A payment is only ever marked successful from a verified provider signal
 * (signed webhook or server-to-server lookup), never from a browser redirect. Success handling is
 * idempotent, and the PURCHASE analytics event is emitted after commit, once per order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProvider provider;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentWebhookEventRepository webhookEvents;
    private final CustomerOrderRepository orders;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final CouponService couponService;
    private final AnalyticsEventService analytics;
    private final EmailService emailService;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final AppProperties properties;
    private final Clock clock;

    public PaymentService(PaymentProvider provider, PaymentRepository payments, RefundRepository refunds,
                          PaymentWebhookEventRepository webhookEvents, CustomerOrderRepository orders,
                          ProductRepository products, InventoryService inventory, CouponService couponService,
                          AnalyticsEventService analytics, EmailService emailService, AuditService audit,
                          TransactionTemplate tx, AppProperties properties, Clock clock) {
        this.provider = provider;
        this.payments = payments;
        this.refunds = refunds;
        this.webhookEvents = webhookEvents;
        this.orders = orders;
        this.products = products;
        this.inventory = inventory;
        this.couponService = couponService;
        this.analytics = analytics;
        this.emailService = emailService;
        this.audit = audit;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    public String providerName() {
        return provider.name();
    }

    /** Creates (or reuses) a provider payment for the customer's order and returns what the browser needs. */
    @Transactional
    public OrderDtos.PaymentStartResponse start(Long userId, Long orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.unprocessable("ORDER_CANCELLED", "This order has been cancelled.");
        }
        if (isPaid(order)) {
            throw ApiException.unprocessable("ALREADY_PAID", "This order has already been paid.");
        }
        List<Payment> existing = payments.findByCustomerOrderIdOrderByIdDesc(orderId);
        Payment open = existing.stream().filter(PaymentService::isOpen).findFirst().orElse(null);
        if (open != null && !open.getProvider().equals(provider.name())) {
            open.setStatus(PaymentTxStatus.CANCELLED); // provider was switched: that attempt cannot complete here
            open = null;
        }
        if (open != null) {
            PaymentProvider.ProviderPayment current = provider.fetch(open.getProviderPaymentId());
            apply(open, current); // picks up an outcome whose webhook has not arrived yet
            if (open.getStatus() == PaymentTxStatus.SUCCEEDED) {
                return new OrderDtos.PaymentStartResponse(open.getId(), open.getProvider(), open.getStatus().name(),
                        null, null, null);
            }
            if (isOpen(open)) {
                var resumed = provider.resume(open.getProviderPaymentId(), current,
                        paymentRequest(order, open.getIdempotencyKey()));
                if (resumed.isPresent()) {
                    return startResponse(open, resumed.get());
                }
                open.setStatus(PaymentTxStatus.CANCELLED); // closed at the provider; replaced below
                open.setCompletedAt(clock.instant());
            }
        }
        // A new attempt (e.g. after a failure) gets a new idempotency key.
        String key = "pay-" + order.getId() + "-" + (existing.size() + 1);
        PaymentProvider.CreatedPayment created = provider.create(paymentRequest(order, key));
        Payment p = new Payment();
        p.setCustomerOrder(order);
        p.setProvider(provider.name());
        p.setProviderPaymentId(created.providerPaymentId());
        p.setAmount(order.getGrandTotal());
        p.setCurrency(order.getCurrency());
        p.setIdempotencyKey(key);
        p.setStatus(map(created.status()));
        payments.save(p);
        if (order.getPaymentStatus() == PaymentStatus.FAILED) {
            order.setPaymentStatus(PaymentStatus.PENDING);
        }
        return startResponse(p, created);
    }

    private PaymentProvider.PaymentRequest paymentRequest(CustomerOrder order, String reference) {
        String phone = order.getAddresses().stream()
                .filter(a -> a.getAddressType() == AddressType.BILLING || a.getAddressType() == AddressType.SHIPPING)
                .map(OrderAddress::getPhoneNumber)
                .filter(n -> n != null && !n.isBlank())
                .findFirst().orElse(order.getUser().getPhoneNumber());
        PaymentProvider.Payer payer = new PaymentProvider.Payer(order.getUser().getFirstName(),
                order.getUser().getLastName(), order.getUser().getEmail(), phone);
        String returnUrl = properties.publicBaseUrl().replaceAll("/+$", "") + "/checkout/payment/" + order.getId();
        Instant expiresAt = order.getPlacedAt().plus(properties.payment().unpaidOrderTimeout());
        return new PaymentProvider.PaymentRequest(order.getOrderNumber(), order.getGrandTotal(), order.getCurrency(),
                reference, returnUrl, expiresAt, payer);
    }

    private static OrderDtos.PaymentStartResponse startResponse(Payment p, PaymentProvider.CreatedPayment created) {
        OrderDtos.PaymentCheckout checkout = created.checkout() == null ? null
                : new OrderDtos.PaymentCheckout(created.checkout().actionUrl(), created.checkout().fields());
        return new OrderDtos.PaymentStartResponse(p.getId(), p.getProvider(), p.getStatus().name(),
                created.clientSecret(), null, checkout);
    }

    private static boolean isOpen(Payment p) {
        return p.getStatus() == PaymentTxStatus.PENDING || p.getStatus() == PaymentTxStatus.REQUIRES_ACTION;
    }

    private static boolean isPaid(CustomerOrder order) {
        return order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || order.getPaymentStatus() == PaymentStatus.REFUNDED;
    }

    /**
     * Server-to-server confirmation the storefront can call after the provider redirect. It asks the
     * provider for the real status; the browser's claim is never trusted.
     */
    @Transactional
    public CustomerOrder refreshFromProvider(Long userId, Long orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order"));
        refreshOpenPayment(orderId);
        return order;
    }

    /** Applies the provider's current status to the order's latest open payment (not for MOCK). */
    private void refreshOpenPayment(Long orderId) {
        payments.findFirstByCustomerOrderIdOrderByIdDesc(orderId)
                .filter(PaymentService::isOpen)
                .filter(p -> p.getProvider().equals(provider.name()) && !MockPaymentProvider.NAME.equals(p.getProvider()))
                .ifPresent(p -> apply(p, provider.fetch(p.getProviderPaymentId())));
    }

    /** Development only: simulates the provider's success/failure webhook for the MOCK provider. */
    @Transactional
    public void mockComplete(Long userId, Long orderId, boolean success) {
        if (!MockPaymentProvider.NAME.equals(provider.name())) {
            throw ApiException.notFound("Endpoint");
        }
        orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order"));
        Payment p = payments.findFirstByCustomerOrderIdOrderByIdDesc(orderId)
                .filter(x -> x.getStatus() == PaymentTxStatus.PENDING)
                .orElseThrow(() -> ApiException.unprocessable("NO_PENDING_PAYMENT", "Start the payment first."));
        apply(p, new PaymentProvider.ProviderPayment(p.getProviderPaymentId(),
                success ? PaymentProvider.Status.SUCCEEDED : PaymentProvider.Status.FAILED, null,
                success ? null : "card_declined", success ? null : "Your card was declined.",
                success ? "visa" : null, success ? "4242" : null));
    }

    /** Handles a provider webhook. Each provider event id is processed at most once. */
    public void handleWebhook(String payload, String signature) {
        PaymentProvider.WebhookEvent event;
        try {
            event = provider.parseWebhook(payload, signature).orElse(null);
        } catch (PaymentProvider.InvalidSignatureException e) {
            log.warn("Rejected payment webhook: {}", e.getMessage());
            throw ApiException.badRequest("INVALID_SIGNATURE", "Invalid signature.");
        }
        if (event == null) {
            return;
        }
        PaymentWebhookEvent record;
        try {
            record = tx.execute(s -> {
                PaymentWebhookEvent w = new PaymentWebhookEvent();
                w.setProvider(provider.name());
                w.setProviderEventId(event.eventId());
                w.setEventType(event.type());
                return webhookEvents.saveAndFlush(w);
            });
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Duplicate webhook {} ignored", event.eventId());
            return;
        }
        try {
            tx.executeWithoutResult(s -> {
                Payment p = payments.findByProviderAndProviderPaymentId(provider.name(), event.payment().providerPaymentId())
                        .orElse(null);
                PaymentWebhookEvent w = webhookEvents.findById(record.getId()).orElseThrow();
                if (p == null) {
                    w.setStatus(WebhookStatus.IGNORED);
                } else {
                    apply(p, event.payment());
                    w.setStatus(WebhookStatus.PROCESSED);
                }
                w.setProcessedAt(clock.instant());
            });
        } catch (RuntimeException e) {
            log.error("Failed to process webhook {}", event.eventId(), e);
            tx.executeWithoutResult(s -> webhookEvents.findById(record.getId()).ifPresent(w -> {
                w.setStatus(WebhookStatus.FAILED);
                w.setErrorMessage("Processing error");
            }));
            throw e; // non-2xx makes the provider retry
        }
    }

    /** Applies a verified provider status to the payment and its order. Idempotent. */
    @Transactional(propagation = Propagation.MANDATORY)
    void apply(Payment p, PaymentProvider.ProviderPayment status) {
        if (p.getStatus() == PaymentTxStatus.SUCCEEDED) {
            return; // already processed: duplicate signal
        }
        CustomerOrder order = p.getCustomerOrder();
        switch (status.status()) {
            case SUCCEEDED -> markSucceeded(p, order, status);
            case FAILED -> {
                p.setStatus(PaymentTxStatus.FAILED);
                p.setFailureCode(truncate(status.failureCode(), 64));
                p.setFailureMessage(truncate(status.failureMessage(), 255));
                p.setCompletedAt(clock.instant());
                if (order.getPaymentStatus() == PaymentStatus.PENDING) {
                    order.setPaymentStatus(PaymentStatus.FAILED);
                    order.recordStatus(StatusType.PAYMENT, PaymentStatus.PENDING.name(), PaymentStatus.FAILED.name(),
                            null, "Payment failed");
                }
            }
            case CANCELLED -> p.setStatus(PaymentTxStatus.CANCELLED);
            case REQUIRES_ACTION -> p.setStatus(PaymentTxStatus.REQUIRES_ACTION);
            case PENDING -> {
                // nothing to do
            }
        }
    }

    private void markSucceeded(Payment p, CustomerOrder order, PaymentProvider.ProviderPayment status) {
        Instant now = clock.instant();
        p.setStatus(PaymentTxStatus.SUCCEEDED);
        p.setCompletedAt(now);
        p.setCardBrand(truncate(status.cardBrand(), 20));
        p.setCardLast4(status.cardLast4() != null && status.cardLast4().matches("\\d{4}") ? status.cardLast4() : null);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Paid after cancellation (e.g. expiry raced with payment): refund immediately.
            log.warn("Payment succeeded for cancelled order {}; refunding", order.getOrderNumber());
            PaymentProvider.RefundOutcome r = provider.refund(p.getProviderPaymentId(), p.getAmount(), "late-" + p.getId());
            audit.recordOutcome("LATE_PAYMENT_REFUNDED", "Order", order.getId(), Map.of("succeeded", r.succeeded()),
                    r.succeeded() ? com.org.ecommerce.entity.Enums.AuditOutcome.SUCCESS
                            : com.org.ecommerce.entity.Enums.AuditOutcome.FAILURE, null);
            return;
        }
        if (isPaid(order)) {
            // Another attempt was paid too (e.g. two open checkout pages): refund this one. It is not
            // recorded as SUCCEEDED because an order has at most one successful payment.
            log.warn("Duplicate payment for order {}; refunding", order.getOrderNumber());
            PaymentProvider.RefundOutcome r = provider.refund(p.getProviderPaymentId(), p.getAmount(), "dup-" + p.getId());
            p.setStatus(PaymentTxStatus.CANCELLED);
            p.setFailureCode("duplicate_payment");
            p.setFailureMessage(r.succeeded() ? "Duplicate payment refunded" : "Duplicate payment: refund failed");
            audit.recordOutcome("DUPLICATE_PAYMENT_REFUNDED", "Order", order.getId(), Map.of("succeeded", r.succeeded()),
                    r.succeeded() ? com.org.ecommerce.entity.Enums.AuditOutcome.SUCCESS
                            : com.org.ecommerce.entity.Enums.AuditOutcome.FAILURE, null);
            return;
        }
        String fromPayment = order.getPaymentStatus().name();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(now);
        order.recordStatus(StatusType.PAYMENT, fromPayment, PaymentStatus.PAID.name(), null, "Payment received");
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.recordStatus(StatusType.FULFILLMENT, OrderStatus.PENDING.name(), OrderStatus.CONFIRMED.name(), null,
                    "Order confirmed");
        }
        for (OrderItem item : order.getItems()) {
            products.incrementSoldCount(item.getProduct().getId(), item.getQuantity());
        }
        Long orderId = order.getId();
        var visitorKey = order.getAnalyticsVisitorKey();
        var sessionKey = order.getAnalyticsSessionKey();
        Long userId = order.getUser().getId();
        String email = order.getUser().getEmail();
        String firstName = order.getUser().getFirstName();
        String orderNumber = order.getOrderNumber();
        String total = order.getGrandTotal().toPlainString() + " " + order.getCurrency();
        AfterCommit.run(() -> {
            analytics.recordServerEvent(EventType.PURCHASE, visitorKey, sessionKey, userId, orderId);
            emailService.sendOrderConfirmation(email, firstName, orderNumber, total);
        });
    }

    /** Refunds part or all of a paid order through the provider. */
    @Transactional(propagation = Propagation.REQUIRED)
    public Refund refund(CustomerOrder order, BigDecimal amount, String reason, Long actorId, String idempotencyKey) {
        if (order.getPaymentStatus() != PaymentStatus.PAID && order.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw ApiException.unprocessable("NOT_REFUNDABLE", "Only paid orders can be refunded.");
        }
        BigDecimal refundable = order.getGrandTotal().subtract(order.getRefundedTotal());
        if (amount.signum() <= 0 || amount.compareTo(refundable) > 0) {
            throw ApiException.unprocessable("INVALID_REFUND_AMOUNT", "Refund amount must be between 0.01 and " + refundable + ".");
        }
        Payment payment = payments.findFirstByCustomerOrderIdAndStatus(order.getId(), PaymentTxStatus.SUCCEEDED)
                .orElseThrow(() -> ApiException.unprocessable("NO_PAYMENT", "No successful payment found for this order."));
        String key = "refund-" + order.getId() + "-" + idempotencyKey;
        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setCustomerOrder(order);
        refund.setAmount(amount);
        refund.setReason(truncate(reason, 255));
        refund.setRequestedByUserId(actorId);
        refund.setIdempotencyKey(truncate(key, 64));
        try {
            refunds.saveAndFlush(refund);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE_REFUND", "This refund request has already been submitted.");
        }

        PaymentProvider.RefundOutcome outcome = provider.refund(payment.getProviderPaymentId(), amount, refund.getIdempotencyKey());
        refund.setProviderRefundId(outcome.providerRefundId());
        if (!outcome.succeeded()) {
            refund.setStatus(RefundStatus.FAILED);
            audit.recordOutcome("REFUND_FAILED", "Order", order.getId(), Map.of("amount", amount.toPlainString()),
                    com.org.ecommerce.entity.Enums.AuditOutcome.FAILURE, actorId);
            throw ApiException.unprocessable("REFUND_FAILED", "The payment provider could not process the refund.");
        }
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setCompletedAt(clock.instant());
        order.setRefundedTotal(order.getRefundedTotal().add(amount));
        String from = order.getPaymentStatus().name();
        boolean full = order.getRefundedTotal().compareTo(order.getGrandTotal()) == 0;
        order.setPaymentStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        order.recordStatus(StatusType.PAYMENT, from, order.getPaymentStatus().name(), actorId,
                "Refunded " + amount.toPlainString() + (reason == null ? "" : ": " + reason));
        audit.record("ORDER_REFUNDED", "Order", order.getId(), Map.of("amount", amount.toPlainString(), "full", full));
        return refund;
    }

    @Transactional(readOnly = true)
    public List<Payment> paymentsFor(Long orderId) {
        return payments.findByCustomerOrderIdOrderByIdDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<Refund> refundsFor(Long orderId) {
        return refunds.findByCustomerOrderIdOrderByIdAsc(orderId);
    }

    /**
     * Cancels orders that stayed unpaid past the timeout, returning their stock and coupon uses.
     * Each order is handled in its own transaction so one failure does not block the rest.
     */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void expireUnpaidOrders() {
        Instant cutoff = clock.instant().minus(properties.payment().unpaidOrderTimeout());
        List<Long> ids = tx.execute(s -> orders.findUnpaidBefore(List.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
                cutoff, PageRequest.of(0, 100)).stream().map(CustomerOrder::getId).toList());
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> orders.findById(id).ifPresent(o -> {
                    if (o.getStatus() != OrderStatus.PENDING || o.getPaymentStatus() == PaymentStatus.PAID) {
                        return;
                    }
                    // The customer may have paid without the webhook reaching us (e.g. no public callback URL).
                    refreshOpenPayment(o.getId());
                    if (o.getPaymentStatus() == PaymentStatus.PAID) {
                        return;
                    }
                    couponService.release(o.getId());
                    for (OrderItem item : o.getItems()) {
                        inventory.restore(item.getVariant().getId(), item.getQuantity(), o.getId(),
                                InventoryReason.ORDER_CANCELLED);
                    }
                    String fromPayment = o.getPaymentStatus().name();
                    o.setPaymentStatus(PaymentStatus.CANCELLED);
                    o.setStatus(OrderStatus.CANCELLED);
                    o.setCancelledAt(clock.instant());
                    o.setCancelReason("Payment not received in time");
                    o.recordStatus(StatusType.PAYMENT, fromPayment, PaymentStatus.CANCELLED.name(), null, "Payment timeout");
                    o.recordStatus(StatusType.FULFILLMENT, OrderStatus.PENDING.name(), OrderStatus.CANCELLED.name(), null,
                            "Payment not received in time");
                }));
            } catch (RuntimeException e) {
                log.error("Failed to expire order {}", id, e);
            }
        }
    }


    private static PaymentTxStatus map(PaymentProvider.Status s) {
        return switch (s) {
            case PENDING -> PaymentTxStatus.PENDING;
            case REQUIRES_ACTION -> PaymentTxStatus.REQUIRES_ACTION;
            case SUCCEEDED -> PaymentTxStatus.SUCCEEDED;
            case FAILED -> PaymentTxStatus.FAILED;
            case CANCELLED -> PaymentTxStatus.CANCELLED;
        };
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
