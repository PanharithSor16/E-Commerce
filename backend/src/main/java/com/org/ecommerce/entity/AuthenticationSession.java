package com.org.ecommerce.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "AuthenticationSessions")
public class AuthenticationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId")
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64, nullable = false, updatable = false)
    private String tokenHash;

    private Instant createdAt = Instant.now();

    private Instant lastSeenAt = Instant.now();

    private Instant idleExpiresAt;

    private Instant absoluteExpiresAt;

    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    private Enums.RevokedReason revokedReason;

    private Long revokedByUserId;

    private String ipAddress;

    @Nationalized
    private String userAgent;

    @Nationalized
    private String browser;

    @Nationalized
    private String operatingSystem;

    @Enumerated(EnumType.STRING)
    private Enums.DeviceCategory deviceCategory;

    public boolean isActive(Instant now) {
        return revokedAt == null && idleExpiresAt.isAfter(now) && absoluteExpiresAt.isAfter(now);
    }
}
