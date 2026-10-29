package com.org.ecommerce.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "Refunds")
public class Refund extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PaymentId")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OrderId")
    private CustomerOrder customerOrder;

    private String providerRefundId;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Nationalized
    private String reason;

    @Enumerated(EnumType.STRING)
    private Enums.RefundStatus status = Enums.RefundStatus.PENDING;

    private Long requestedByUserId;

    private String idempotencyKey;

    private Instant completedAt;
}
