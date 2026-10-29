package com.org.ecommerce.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "ProductVariants")
public class ProductVariant extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ProductId")
    private Product product;

    private String sku;

    @Nationalized
    private String size;

    @Nationalized
    private String color;

    @Column(precision = 19, scale = 4)
    private BigDecimal priceOverride;

    private int stockQuantity;

    private int lowStockThreshold = 5;

    private boolean active = true;

    @Version
    private Long version;

    /** Unit price for this variant before coupons. */
    public BigDecimal unitPrice() {
        return priceOverride != null ? priceOverride : product.currentPrice();
    }

    /** "Size M / Blue", or null for the default variant. */
    public String describe() {
        if (size == null && color == null) {
            return null;
        }
        if (size == null) {
            return color;
        }
        return color == null ? "Size " + size : "Size " + size + " / " + color;
    }
}
