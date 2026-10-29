package com.org.ecommerce.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "ProductImages")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ProductId")
    private Product product;

    private Long variantId;

    @Nationalized
    private String url;

    @Nationalized
    private String storageKey;

    @Nationalized
    private String altText;

    private String contentType;

    private Long sizeBytes;

    private Integer width;

    private Integer height;

    private int sortOrder;

    private boolean isPrimary;

    private Instant createdAt = Instant.now();
}
