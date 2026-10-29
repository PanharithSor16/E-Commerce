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
@Table(name = "PaymentWebhookEvents")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;

    private String providerEventId;

    private String eventType;

    @Enumerated(EnumType.STRING)
    private Enums.WebhookStatus status = Enums.WebhookStatus.RECEIVED;

    @Nationalized
    private String errorMessage;

    private Instant receivedAt = Instant.now();

    private Instant processedAt;
}
