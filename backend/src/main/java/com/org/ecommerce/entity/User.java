package com.org.ecommerce.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "Users")
public class User extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Nationalized
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Nationalized
    @Column(nullable = false)
    private String firstName;

    @Nationalized
    @Column(nullable = false)
    private String lastName;

    private String phoneNumber;

    private boolean emailVerified;

    private Instant emailVerifiedAt;

    private boolean enabled = true;

    private int failedLoginCount;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    private Instant passwordChangedAt;

    private Instant deactivatedAt;

    @Version
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "UserRoles",
            joinColumns = @JoinColumn(name = "UserId"),
            inverseJoinColumns = @JoinColumn(name = "RoleId"))
    private Set<Role> roles = new HashSet<>();

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }

    /** Staff and admins get shorter sessions and access to /admin. */
    public boolean isStaffOrAdmin() {
        return hasRole(Role.ADMIN) || hasRole(Role.STAFF);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
