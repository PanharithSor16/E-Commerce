package com.org.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "Addresses")
public class Address extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId")
    private User user;

    @Nationalized
    private String label;

    @Nationalized
    private String recipientName;

    private String phoneNumber;

    @Nationalized
    private String line1;

    @Nationalized
    private String line2;

    @Nationalized
    private String city;

    @Nationalized
    private String region;

    @Nationalized
    private String postalCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String countryCode;

    private boolean isDefaultShipping;

    private boolean isDefaultBilling;
}
