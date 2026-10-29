package com.org.ecommerce.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.analytics.AnalyticsContext;
import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.Address;
import com.org.ecommerce.entity.Cart;
import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.CustomerOrder;
import com.org.ecommerce.entity.Enums.AddressType;
import com.org.ecommerce.entity.Enums.CartStatus;
import com.org.ecommerce.entity.Enums.InventoryReason;
import com.org.ecommerce.entity.Enums.OrderStatus;
import com.org.ecommerce.entity.Enums.PaymentStatus;
import com.org.ecommerce.entity.Enums.ProductStatus;
import com.org.ecommerce.entity.Enums.StatusType;
import com.org.ecommerce.entity.OrderAddress;
import com.org.ecommerce.entity.OrderItem;
import com.org.ecommerce.entity.ShippingMethod;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.AddressRepository;
import com.org.ecommerce.repository.CustomerOrderRepository;
import com.org.ecommerce.repository.ShippingMethodRepository;
import com.org.ecommerce.repository.UserRepository;

/**
 * Checkout and order lifecycle. Placing an order is one transaction: re-price the cart, validate the
 * coupon, decrement stock (conditional update), write the order + items + address snapshots, redeem
 * the coupon and close the cart. Any failure rolls everything back.
 */
@Service
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Allowed staff-driven fulfillment transitions. CONFIRMED is reached through payment. */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private final CustomerOrderRepository orders;
    private final CartService cartService;
    private final ShippingMethodRepository shippingMethods;
    private final AddressRepository addresses;
    private final UserRepository users;
    private final PricingService pricing;
    private final CouponService couponService;
    private final InventoryService inventory;
    private final PaymentService paymentService;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final AppProperties properties;
    private final Clock clock;

    public OrderService(CustomerOrderRepository orders, CartService cartService, ShippingMethodRepository shippingMethods,
                        AddressRepository addresses, UserRepository users, PricingService pricing,
                        CouponService couponService, InventoryService inventory, PaymentService paymentService,
                        AuditService audit, JdbcTemplate jdbc, AppProperties properties, Clock clock) {
        this.orders = orders;
        this.cartService = cartService;
        this.shippingMethods = shippingMethods;
        this.addresses = addresses;
        this.users = users;
        this.pricing = pricing;
        this.couponService = couponService;
        this.inventory = inventory;
        this.paymentService = paymentService;
        this.audit = audit;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ checkout

    @Transactional(readOnly = true)
    public OrderDtos.QuoteResponse quote(Long userId, OrderDtos.QuoteRequest req) {
        Cart cart = cartService.activeCartForUser(userId)
                .orElseThrow(() -> ApiException.unprocessable("CART_EMPTY", "Your cart is empty."));
        List<PricingService.Line> lines = orderableLines(cart);
        ShippingMethod method = shippingMethod(req.shippingMethodCode());
        Coupon coupon = resolveCoupon(req.couponCode(), cart, lines, userId);
        PricingService.Quote q = pricing.quote(lines, coupon, method);
        return new OrderDtos.QuoteResponse(q.subtotal(), q.discountTotal(), q.shippingTotal(), q.taxTotal(),
                q.grandTotal(), properties.payment().currency(), coupon == null ? null : coupon.getCode(),
                lines.stream().mapToInt(PricingService.Line::quantity).sum());
    }

    @Transactional
    public CustomerOrder placeOrder(Long userId, OrderDtos.CheckoutRequest req, String idempotencyKey,
                                    AnalyticsContext analyticsContext) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "An Idempotency-Key header (8-64 characters: letters, digits, '-') is required.");
        }
        // A retried submission returns the order created by the first attempt.
        var existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Authentication required."));
        if (properties.security().requireVerifiedEmailForCheckout() && !user.isEmailVerified()) {
            throw ApiException.forbidden("Please verify your email address before placing an order.");
        }
        Cart cart = cartService.activeCartForUser(userId)
                .orElseThrow(() -> ApiException.unprocessable("CART_EMPTY", "Your cart is empty."));
        List<PricingService.Line> lines = orderableLines(cart);
        if (lines.size() != cart.getItems().size()) {
            throw ApiException.conflict("CART_CHANGED",
                    "Some items in your cart are no longer available. Please review your cart.");
        }
        ShippingMethod method = shippingMethod(req.shippingMethodCode());
        Coupon coupon = resolveCoupon(req.couponCode(), cart, lines, userId);
        PricingService.Quote quote = pricing.quote(lines, coupon, method);

        OrderDtos.AddressInput shipping = resolveShippingAddress(userId, req);
        OrderDtos.AddressInput billing = req.billingSameAsShipping() || req.billingAddress() == null
                ? shipping : req.billingAddress();

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(nextOrderNumber());
        order.setUser(user);
        order.setCurrency(properties.payment().currency());
        order.setSubtotal(quote.subtotal());
        order.setDiscountTotal(quote.discountTotal());
        order.setShippingTotal(quote.shippingTotal());
        order.setTaxTotal(quote.taxTotal());
        order.setGrandTotal(quote.grandTotal());
        order.setCoupon(coupon);
        order.setCouponCode(coupon == null ? null : coupon.getCode());
        order.setShippingMethod(method);
        order.setShippingMethodName(method.getName());
        order.setCustomerNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        order.setIdempotencyKey(idempotencyKey);
        order.setAnalyticsVisitorKey(analyticsContext.visitorKey());
        order.setAnalyticsSessionKey(analyticsContext.sessionKey());
        order.setPlacedAt(clock.instant());
        order.addAddress(snapshot(shipping, AddressType.SHIPPING));
        order.addAddress(snapshot(billing, AddressType.BILLING));
        for (PricingService.PricedLine l : quote.lines()) {
            OrderItem item = new OrderItem();
            item.setProduct(l.variant().getProduct());
            item.setVariant(l.variant());
            item.setProductName(l.variant().getProduct().getName());
            item.setVariantDescription(l.variant().describe());
            item.setSku(l.variant().getSku());
            item.setUnitPrice(l.unitPrice());
            item.setQuantity(l.quantity());
            item.setDiscountAmount(l.discountAllocation());
            item.setLineTotal(l.lineTotal());
            order.addItem(item);
        }
        order.recordStatus(StatusType.FULFILLMENT, null, OrderStatus.PENDING.name(), userId, "Order placed");
        try {
            orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Concurrent submission with the same key won the race.
            return orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElseThrow(() -> e);
        }

        for (PricingService.PricedLine l : quote.lines()) {
            inventory.reserve(l.variant().getId(), l.quantity(), order.getId(), l.variant().getSku());
        }
        if (coupon != null) {
            couponService.redeem(coupon, userId, order.getId(), quote.discountTotal());
        }
        cart.setStatus(CartStatus.CONVERTED);
        if (req.saveAddress() && req.shippingAddressId() == null) {
            saveAddress(user, shipping);
        }
        audit.record("ORDER_PLACED", "Order", order.getId(),
                Map.of("orderNumber", order.getOrderNumber(), "grandTotal", order.getGrandTotal().toPlainString()));
        return order;
    }

    // ------------------------------------------------------------------ DTO views (mapped inside transactions)

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail mineDetail(Long userId, Long orderId) {
        return toDetail(getMine(userId, orderId), false);
    }

    @Transactional(readOnly = true)
    public com.org.ecommerce.dto.PageResponse<OrderDtos.OrderSummary> mineList(Long userId, Pageable pageable) {
        return com.org.ecommerce.dto.PageResponse.of(listMine(userId, pageable),
                o -> com.org.ecommerce.mapper.OrderMapper.toSummary(o, false));
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail adminDetail(Long id) {
        return toDetail(getForAdmin(id), true);
    }

    @Transactional(readOnly = true)
    public com.org.ecommerce.dto.PageResponse<OrderDtos.OrderSummary> adminList(AdminFilter f, Pageable pageable) {
        return com.org.ecommerce.dto.PageResponse.of(search(f, pageable),
                o -> com.org.ecommerce.mapper.OrderMapper.toSummary(o, true));
    }

    private OrderDtos.OrderDetail toDetail(CustomerOrder o, boolean includeCustomer) {
        return com.org.ecommerce.mapper.OrderMapper.toDetail(o, paymentService.paymentsFor(o.getId()),
                paymentService.refundsFor(o.getId()), includeCustomer);
    }

    // ------------------------------------------------------------------ customer views

    @Transactional(readOnly = true)
    public Page<CustomerOrder> listMine(Long userId, Pageable pageable) {
        return orders.findByUserId(userId, pageable);
    }

    /** Returns 404 for orders of other customers so their existence is not revealed. */
    @Transactional(readOnly = true)
    public CustomerOrder getMine(Long userId, Long orderId) {
        CustomerOrder o = orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order"));
        o.getAddresses().size();
        o.getStatusHistory().size();
        return o;
    }

    @Transactional
    public CustomerOrder cancelMine(Long userId, Long orderId, String reason) {
        CustomerOrder o = orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order"));
        if (o.getStatus() != OrderStatus.PENDING && o.getStatus() != OrderStatus.CONFIRMED) {
            throw ApiException.unprocessable("NOT_CANCELLABLE", "This order is already being processed and cannot be cancelled.");
        }
        cancel(o, reason == null || reason.isBlank() ? "Cancelled by customer" : reason, userId, true);
        return o;
    }

    // ------------------------------------------------------------------ admin

    public record AdminFilter(String q, OrderStatus status, PaymentStatus paymentStatus, Instant from, Instant to) {
    }

    @Transactional(readOnly = true)
    public Page<CustomerOrder> search(AdminFilter f, Pageable pageable) {
        Specification<CustomerOrder> spec = (root, q, cb) -> cb.conjunction();
        if (f.q() != null && !f.q().isBlank()) {
            String term = f.q().trim().toUpperCase();
            spec = spec.and((root, q, cb) -> cb.or(cb.like(root.get("orderNumber"), "%" + CatalogService.escapeLike(term) + "%", '\\'),
                    cb.like(cb.lower(root.get("user").get("email")), "%" + CatalogService.escapeLike(f.q().trim().toLowerCase()) + "%", '\\')));
        }
        if (f.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.paymentStatus() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("paymentStatus"), f.paymentStatus()));
        }
        if (f.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("placedAt"), f.from()));
        }
        if (f.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("placedAt"), f.to()));
        }
        return orders.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public CustomerOrder getForAdmin(Long id) {
        CustomerOrder o = orders.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Order"));
        o.getAddresses().size();
        o.getStatusHistory().size();
        return o;
    }

    @Transactional
    public CustomerOrder updateFulfillment(Long id, OrderStatus target, String note, Long staffId) {
        CustomerOrder o = orders.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Order"));
        if (!TRANSITIONS.getOrDefault(o.getStatus(), Set.of()).contains(target)) {
            throw ApiException.unprocessable("INVALID_TRANSITION",
                    "Cannot change status from " + o.getStatus() + " to " + target + ".");
        }
        if (target == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("USE_CANCEL_ENDPOINT", "Use the cancel endpoint to cancel orders.");
        }
        if (o.getPaymentStatus() != PaymentStatus.PAID && o.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw ApiException.unprocessable("NOT_PAID", "Unpaid orders cannot be fulfilled.");
        }
        OrderStatus from = o.getStatus();
        o.setStatus(target);
        if (target == OrderStatus.SHIPPED) {
            o.setShippedAt(clock.instant());
        } else if (target == OrderStatus.DELIVERED) {
            o.setDeliveredAt(clock.instant());
        }
        o.recordStatus(StatusType.FULFILLMENT, from.name(), target.name(), staffId, note);
        audit.record("ORDER_STATUS_CHANGED", "Order", id, Map.of("from", from.name(), "to", target.name()));
        return o;
    }

    /** Staff cancellation; paid orders are refunded in full (requires ORDER_REFUND, checked by the controller). */
    @Transactional
    public CustomerOrder cancelByStaff(Long id, String reason, Long staffId) {
        CustomerOrder o = orders.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Order"));
        if (!TRANSITIONS.getOrDefault(o.getStatus(), Set.of()).contains(OrderStatus.CANCELLED)) {
            throw ApiException.unprocessable("NOT_CANCELLABLE", "Orders that have shipped cannot be cancelled.");
        }
        cancel(o, reason == null || reason.isBlank() ? "Cancelled by staff" : reason, staffId, false);
        return o;
    }

    /** Partial or full refund without cancelling (e.g. a returned item). */
    @Transactional
    public void refund(Long id, java.math.BigDecimal amount, String reason, Long staffId, String idempotencyKey) {
        CustomerOrder o = orders.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Order"));
        paymentService.refund(o, amount, reason, staffId, idempotencyKey);
    }

    /**
     * Cancels an order: refunds the remaining paid amount, restores stock and releases the coupon
     * (coupon only for never-paid orders).
     */
    void cancel(CustomerOrder o, String reason, Long actorId, boolean byCustomer) {
        boolean paid = o.getPaymentStatus() == PaymentStatus.PAID || o.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED;
        if (paid) {
            var remaining = o.getGrandTotal().subtract(o.getRefundedTotal());
            if (remaining.signum() > 0) {
                paymentService.refund(o, remaining, reason, actorId, "cancel-" + o.getId());
            }
        } else {
            couponService.release(o.getId());
            String fromPayment = o.getPaymentStatus().name();
            o.setPaymentStatus(PaymentStatus.CANCELLED);
            o.recordStatus(StatusType.PAYMENT, fromPayment, PaymentStatus.CANCELLED.name(), actorId, reason);
        }
        for (OrderItem item : o.getItems()) {
            inventory.restore(item.getVariant().getId(), item.getQuantity(), o.getId(), InventoryReason.ORDER_CANCELLED);
        }
        OrderStatus from = o.getStatus();
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelledAt(clock.instant());
        o.setCancelReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
        o.recordStatus(StatusType.FULFILLMENT, from.name(), OrderStatus.CANCELLED.name(), actorId, reason);
        audit.record(byCustomer ? "ORDER_CANCELLED_BY_CUSTOMER" : "ORDER_CANCELLED", "Order", o.getId(),
                Map.of("refunded", paid));
    }

    // ------------------------------------------------------------------ helpers

    private static List<PricingService.Line> orderableLines(Cart cart) {
        return cart.getItems().stream()
                .filter(i -> i.getVariant().isActive() && i.getVariant().getProduct().getStatus() == ProductStatus.ACTIVE)
                .map(i -> new PricingService.Line(i.getVariant(), i.getQuantity()))
                .toList();
    }

    private Coupon resolveCoupon(String requestedCode, Cart cart, List<PricingService.Line> lines, Long userId) {
        String code = requestedCode != null && !requestedCode.isBlank() ? requestedCode
                : cart.getCoupon() != null ? cart.getCoupon().getCode() : null;
        if (code == null) {
            return null;
        }
        return couponService.validate(code, pricing.quote(lines, null, null).subtotal(), userId);
    }

    private ShippingMethod shippingMethod(String code) {
        return shippingMethods.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> ApiException.badRequest("INVALID_SHIPPING_METHOD", "Unknown shipping method."));
    }

    private OrderDtos.AddressInput resolveShippingAddress(Long userId, OrderDtos.CheckoutRequest req) {
        if (req.shippingAddressId() != null) {
            Address a = addresses.findByIdAndUserId(req.shippingAddressId(), userId)
                    .orElseThrow(() -> ApiException.badRequest("INVALID_ADDRESS", "Address not found."));
            return new OrderDtos.AddressInput(a.getRecipientName(), a.getPhoneNumber(), a.getLine1(), a.getLine2(),
                    a.getCity(), a.getRegion(), a.getPostalCode(), a.getCountryCode());
        }
        if (req.shippingAddress() == null) {
            throw ApiException.badRequest("ADDRESS_REQUIRED", "A shipping address is required.");
        }
        return req.shippingAddress();
    }

    private static OrderAddress snapshot(OrderDtos.AddressInput in, AddressType type) {
        OrderAddress a = new OrderAddress();
        a.setAddressType(type);
        a.setRecipientName(in.recipientName().trim());
        a.setPhoneNumber(in.phoneNumber());
        a.setLine1(in.line1().trim());
        a.setLine2(in.line2());
        a.setCity(in.city().trim());
        a.setRegion(in.region());
        a.setPostalCode(in.postalCode());
        a.setCountryCode(in.countryCode().toUpperCase());
        return a;
    }

    private void saveAddress(User user, OrderDtos.AddressInput in) {
        Address a = new Address();
        a.setUser(user);
        a.setRecipientName(in.recipientName().trim());
        a.setPhoneNumber(in.phoneNumber());
        a.setLine1(in.line1().trim());
        a.setLine2(in.line2());
        a.setCity(in.city().trim());
        a.setRegion(in.region());
        a.setPostalCode(in.postalCode());
        a.setCountryCode(in.countryCode().toUpperCase());
        boolean first = addresses.findByUserIdOrderByIdAsc(user.getId()).isEmpty();
        a.setDefaultShipping(first);
        a.setDefaultBilling(first);
        addresses.save(a);
    }

    private String nextOrderNumber() {
        Long seq = jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.OrderNumberSeq", Long.class);
        return "ORD-" + LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(ORDER_DATE) + "-" + seq;
    }
}
