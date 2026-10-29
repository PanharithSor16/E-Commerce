package com.org.ecommerce.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "OrderStatusHistory")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OrderId")
    private CustomerOrder customerOrder;

    @Enumerated(EnumType.STRING)
    private Enums.StatusType statusType;

    private String fromStatus;

    private String toStatus;

    private Long changedByUserId;

    @Nationalized
    private String note;

    private Instant changedAt = Instant.now();
}
