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
@Table(name = "AuditLogs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorUserId;

    private String action;

    private String entityType;

    private String entityId;

    @Enumerated(EnumType.STRING)
    private Enums.AuditOutcome outcome = Enums.AuditOutcome.SUCCESS;

    @Nationalized
    private String details;

    private String ipAddress;

    private Instant occurredAt = Instant.now();
}
