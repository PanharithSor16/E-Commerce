package com.org.ecommerce.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "Coupons")
public class Coupon extends AuditableEntity {

    private String code;

    @Nationalized
    private String description;

    @Enumerated(EnumType.STRING)
    private Enums.DiscountType discountType;

    @Column(precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal minOrderAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal maxDiscountAmount;

    private Instant startsAt;

    private Instant endsAt;

    private Integer usageLimit;

    private Integer perUserLimit;

    private int timesUsed;

    private boolean active = true;

    @Version
    private Long version;
}
