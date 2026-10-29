package com.org.ecommerce.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

/** Maps the Orders table ("Order" is a reserved word in JPQL). */
@Getter
@Setter
@Entity
@Table(name = "Orders")
public class CustomerOrder extends AuditableEntity {

    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId")
    private User user;

    @Enumerated(EnumType.STRING)
    private Enums.OrderStatus status = Enums.OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private Enums.PaymentStatus paymentStatus = Enums.PaymentStatus.PENDING;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    @Column(precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(precision = 19, scale = 4)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4)
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Column(precision = 19, scale = 4)
    private BigDecimal refundedTotal = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CouponId")
    private Coupon coupon;

    private String couponCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ShippingMethodId")
    private ShippingMethod shippingMethod;

    @Nationalized
    private String shippingMethodName;

    @Nationalized
    private String customerNote;

    private String idempotencyKey;

    /** Captured at checkout only with analytics consent; used to attribute the PURCHASE event. */
    private java.util.UUID analyticsVisitorKey;

    private java.util.UUID analyticsSessionKey;

    private Instant placedAt = Instant.now();

    private Instant paidAt;

    private Instant shippedAt;

    private Instant deliveredAt;

    private Instant cancelledAt;

    @Nationalized
    private String cancelReason;

    @Version
    private Long version;

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setCustomerOrder(this);
        items.add(item);
    }

    public void addAddress(OrderAddress address) {
        address.setCustomerOrder(this);
        addresses.add(address);
    }

    public void recordStatus(Enums.StatusType type, String from, String to, Long changedBy, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setCustomerOrder(this);
        h.setStatusType(type);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedByUserId(changedBy);
        h.setNote(note);
        statusHistory.add(h);
    }
}
