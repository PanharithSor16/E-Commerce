package com.org.ecommerce.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "ShippingMethods")
public class ShippingMethod extends AuditableEntity {

    private String code;

    @Nationalized
    private String name;

    @Nationalized
    private String description;

    @Column(precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(precision = 19, scale = 4)
    private BigDecimal freeShippingThreshold;

    private Integer estimatedDaysMin;

    private Integer estimatedDaysMax;

    private int sortOrder;

    private boolean active = true;

    /** Shipping fee for a given merchandise subtotal (after discounts). */
    public BigDecimal feeFor(BigDecimal subtotal) {
        if (freeShippingThreshold != null && subtotal.compareTo(freeShippingThreshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return fee;
    }
}
