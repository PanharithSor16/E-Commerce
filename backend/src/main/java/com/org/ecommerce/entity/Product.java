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

@Getter
@Setter
@Entity
@Table(name = "Products")
public class Product extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CategoryId")
    private Category category;

    @Nationalized
    private String name;

    private String slug;

    private String sku;

    @Nationalized
    private String brand;

    @Nationalized
    private String shortDescription;

    @Nationalized
    private String description;

    @Column(precision = 19, scale = 4)
    private BigDecimal basePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal discountPrice;

    /** Persisted computed column COALESCE(DiscountPrice, BasePrice); read-only. */
    @Column(precision = 19, scale = 4, insertable = false, updatable = false)
    private BigDecimal effectivePrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    private Enums.ProductStatus status = Enums.ProductStatus.DRAFT;

    private boolean featured;

    private int soldCount;

    private Instant publishedAt;

    private Instant archivedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    /** Current selling price per unit before coupons. */
    public BigDecimal currentPrice() {
        return discountPrice != null ? discountPrice : basePrice;
    }
}
