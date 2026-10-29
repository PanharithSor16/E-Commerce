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

/** Append-only consent log. Analytics visitors/events are written with JdbcTemplate (see analytics package). */
@Getter
@Setter
@Entity
@Table(name = "UserConsent")
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long visitorId;

    @Enumerated(EnumType.STRING)
    private Enums.ConsentType consentType;

    private boolean granted;

    private String policyVersion;

    @Enumerated(EnumType.STRING)
    private Enums.ConsentSource source;

    private Instant recordedAt = Instant.now();
}
