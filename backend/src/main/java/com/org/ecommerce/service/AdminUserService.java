package com.org.ecommerce.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.dto.admin.AdminDtos;
import com.org.ecommerce.entity.CustomerOrder;
import com.org.ecommerce.entity.Enums.RevokedReason;
import com.org.ecommerce.entity.Role;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.CustomerOrderRepository;
import com.org.ecommerce.repository.RoleRepository;
import com.org.ecommerce.repository.UserRepository;
import com.org.ecommerce.security.AuthenticatedUser;

@Service
@Transactional
public class AdminUserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final CustomerOrderRepository orders;
    private final SessionService sessions;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserService(UserRepository users, RoleRepository roles, CustomerOrderRepository orders,
                            SessionService sessions, AuditService audit, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.orders = orders;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public com.org.ecommerce.dto.PageResponse<AdminDtos.CustomerSummary> search(String q, Boolean enabled, String role,
                                                                              Pageable pageable) {
        Page<User> page = users.search(q == null || q.isBlank() ? null : q.trim(), enabled, role, pageable);
        return com.org.ecommerce.dto.PageResponse.of(page, u -> new AdminDtos.CustomerSummary(u.getId(), u.getPublicId(),
                u.getEmail(), u.getFirstName(), u.getLastName(), u.isEnabled(), u.isEmailVerified(),
                u.isLocked(clock.instant()), roleNames(u), u.getLastLoginAt(), u.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminDtos.CustomerDetail detail(Long id) {
        User u = users.findWithRolesById(id).orElseThrow(() -> ApiException.notFound("User"));
        Specification<CustomerOrder> byUser = (root, q, cb) -> cb.equal(root.get("user").get("id"), id);
        long orderCount = orders.count(byUser);
        return new AdminDtos.CustomerDetail(u.getId(), u.getPublicId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhoneNumber(), u.isEnabled(), u.isEmailVerified(), u.isLocked(clock.instant()), u.getLockedUntil(),
                u.getFailedLoginCount(), roleNames(u), u.getLastLoginAt(), u.getCreatedAt(), u.getDeactivatedAt(),
                orderCount, sessions.activeSessions(id).size());
    }

    public void update(Long id, AdminDtos.UpdateCustomerRequest req) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setPhoneNumber(req.phoneNumber() == null || req.phoneNumber().isBlank() ? null : req.phoneNumber().trim());
        audit.record("USER_UPDATED_BY_ADMIN", "User", id, Map.of());
    }

    /** Deactivation signs the user out everywhere immediately. */
    public void setEnabled(AuthenticatedUser actor, Long id, boolean enabled, String reason) {
        if (actor.userId().equals(id) && !enabled) {
            throw ApiException.unprocessable("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account.");
        }
        User u = users.findWithRolesById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.hasRole(Role.ADMIN) && !actor.roles().contains(Role.ADMIN)) {
            throw ApiException.forbidden("Only administrators can change administrator accounts.");
        }
        u.setEnabled(enabled);
        u.setDeactivatedAt(enabled ? null : clock.instant());
        if (enabled) {
            u.setLockedUntil(null);
            u.setFailedLoginCount(0);
        } else {
            sessions.revokeAll(id, null, RevokedReason.ACCOUNT_DEACTIVATED, actor.userId());
        }
        audit.record(enabled ? "USER_ACTIVATED" : "USER_DEACTIVATED", "User", id,
                reason == null ? Map.of() : Map.of("reason", reason));
    }

    public void unlock(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setLockedUntil(null);
        u.setFailedLoginCount(0);
        audit.record("USER_UNLOCKED", "User", id, Map.of());
    }

    /** Role assignment is an ADMIN-only permission (ROLE_ASSIGN), enforced by the controller. */
    public void setRoles(AuthenticatedUser actor, Long id, Set<String> roleNames) {
        User u = users.findWithRolesById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (actor.userId().equals(id) && u.hasRole(Role.ADMIN) && !roleNames.contains(Role.ADMIN)) {
            throw ApiException.unprocessable("CANNOT_DEMOTE_SELF", "You cannot remove your own administrator role.");
        }
        Set<String> before = roleNames(u);
        Set<Role> newRoles = new HashSet<>();
        for (String name : roleNames) {
            newRoles.add(roles.findByName(name).orElseThrow(() -> ApiException.badRequest("INVALID_ROLE", "Unknown role " + name)));
        }
        u.getRoles().clear();
        u.getRoles().addAll(newRoles);
        // Permissions changed: force re-login so session lifetimes and authorities are recomputed.
        sessions.revokeAll(id, null, RevokedReason.ADMIN_REVOKED, actor.userId());
        audit.record("USER_ROLES_CHANGED", "User", id, Map.of("before", String.join(",", before),
                "after", String.join(",", roleNames)));
    }

    public static Set<String> roleNames(User u) {
        return u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }
}
