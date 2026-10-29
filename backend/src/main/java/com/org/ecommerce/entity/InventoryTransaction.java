package com.org.ecommerce.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "InventoryTransactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long variantId;

    private int changeQuantity;

    private int quantityAfter;

    @Enumerated(EnumType.STRING)
    private Enums.InventoryReason reason;

    private Long orderId;

    @Nationalized
    private String note;

    private Long createdByUserId;

    private Instant createdAt = Instant.now();
}
