package com.org.ecommerce.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "Categories")
public class Category extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ParentId")
    private Category parent;

    @Nationalized
    private String name;

    private String slug;

    @Nationalized
    private String description;

    @Nationalized
    private String imageUrl;

    private int sortOrder;

    private boolean active = true;

    @Version
    private Long version;
}
