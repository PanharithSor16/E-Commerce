package com.org.ecommerce.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "LoginHistory")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String emailHash;

    @Nationalized
    private String emailMasked;

    @Enumerated(EnumType.STRING)
    private Enums.LoginStatus status;

    @Enumerated(EnumType.STRING)
    private Enums.LoginFailureReason failureReason;

    private Instant occurredAt = Instant.now();

    private String ipAddress;

    @Nationalized
    private String userAgent;

    @Nationalized
    private String browser;

    @Nationalized
    private String browserVersion;

    @Nationalized
    private String operatingSystem;

    @Enumerated(EnumType.STRING)
    private Enums.DeviceCategory deviceCategory;

    private UUID authSessionPublicId;
}
