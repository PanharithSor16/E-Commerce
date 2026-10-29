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
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "Payments")
public class Payment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OrderId")
    private CustomerOrder customerOrder;

    private String provider;

    private String providerPaymentId;

    private String providerSessionId;

    @Enumerated(EnumType.STRING)
    private Enums.PaymentTxStatus status = Enums.PaymentTxStatus.PENDING;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    private String cardBrand;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 4)
    private String cardLast4;

    private String failureCode;

    @Nationalized
    private String failureMessage;

    private String idempotencyKey;

    private Instant completedAt;

    @Version
    private Long version;
}
