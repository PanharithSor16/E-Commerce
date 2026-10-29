package com.org.ecommerce.entity;

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
@Table(name = "OrderAddresses")
public class OrderAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OrderId")
    private CustomerOrder customerOrder;

    @Enumerated(EnumType.STRING)
    private Enums.AddressType addressType;

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
}
