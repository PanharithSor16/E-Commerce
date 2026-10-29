package com.org.ecommerce.controller.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.admin.AdminDtos;
import com.org.ecommerce.entity.AuthenticationSession;
import com.org.ecommerce.entity.Enums.LoginStatus;
import com.org.ecommerce.entity.Enums.RevokedReason;
import com.org.ecommerce.entity.LoginHistory;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.service.AdminUserService;
import com.org.ecommerce.service.LoginHistoryService;
import com.org.ecommerce.service.SessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Customer management and security monitoring. Login history and sessions require
 * SECURITY_LOG_READ (ADMIN only by default); role assignment requires ROLE_ASSIGN.
 */
@Tag(name = "Admin - Users & Security")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService userService;
    private final LoginHistoryService loginHistory;
    private final SessionService sessionService;
    private final AuditService audit;
    private final Clock clock;

    public AdminUserController(AdminUserService userService, LoginHistoryService loginHistory,
                               SessionService sessionService, AuditService audit, Clock clock) {
        this.userService = userService;
        this.loginHistory = loginHistory;
        this.sessionService = sessionService;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- users

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/users")
    public PageResponse<AdminDtos.CustomerSummary> users(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) Boolean enabled,
                                                         @RequestParam(required = false) String role,
                                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userService.search(q, enabled, role, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @GetMapping("/users/{id}")
    public AdminDtos.CustomerDetail user(@PathVariable Long id) {
        return userService.detail(id);
    }

    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @PutMapping("/users/{id}")
    public AdminDtos.CustomerDetail update(@PathVariable Long id, @Valid @RequestBody AdminDtos.UpdateCustomerRequest body) {
        userService.update(id, body);
        return userService.detail(id);
    }

    @Operation(summary = "Activate or deactivate an account (deactivation revokes all sessions)")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @PostMapping("/users/{id}/enabled")
    public AdminDtos.CustomerDetail setEnabled(@PathVariable Long id, @Valid @RequestBody AdminDtos.SetEnabledRequest body) {
        userService.setEnabled(CurrentUser.require(), id, body.enabled(), body.reason());
        return userService.detail(id);
    }

    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @PostMapping("/users/{id}/unlock")
    public AdminDtos.CustomerDetail unlock(@PathVariable Long id) {
        userService.unlock(id);
        return userService.detail(id);
    }

    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @PutMapping("/users/{id}/roles")
    public AdminDtos.CustomerDetail setRoles(@PathVariable Long id, @Valid @RequestBody AdminDtos.SetRolesRequest body) {
        userService.setRoles(CurrentUser.require(), id, body.roles());
        return userService.detail(id);
    }

    @PreAuthorize("hasAuthority('AUDIT_LOG_READ') or hasAuthority('CUSTOMER_READ')")
    @GetMapping("/users/{id}/activity")
    public PageResponse<AdminDtos.AuditEntry> activity(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(audit.search(id, null, null, page, Math.min(size, 100)), AdminAuditController::toEntry);
    }

    // ---------------------------------------------------------------- login history

    @PreAuthorize("hasAuthority('SECURITY_LOG_READ')")
    @GetMapping("/login-history")
    public PageResponse<AdminDtos.LoginHistoryEntry> loginHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) LoginStatus status,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        var filter = new LoginHistoryService.Filter(userId, email, status,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ip);
        return PageResponse.of(loginHistory.search(filter,
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))), AdminUserController::entry);
    }

    @PreAuthorize("hasAuthority('SECURITY_LOG_READ')")
    @GetMapping("/login-history/{id}")
    public AdminDtos.LoginHistoryEntry loginHistoryEntry(@PathVariable Long id) {
        return entry(loginHistory.get(id));
    }

    @Operation(summary = "Accounts/emails with repeated failed logins in the last N hours")
    @PreAuthorize("hasAuthority('SECURITY_LOG_READ')")
    @GetMapping("/login-history/repeated-failures")
    public List<AdminDtos.RepeatedFailure> repeatedFailures(@RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours,
                                                            @RequestParam(defaultValue = "3") @Min(2) @Max(1000) int threshold) {
        return loginHistory.repeatedFailures(clock.instant().minusSeconds(hours * 3600L), threshold, 50).stream()
                .map(v -> new AdminDtos.RepeatedFailure(v.getEmailHash(), v.getEmailMasked(), v.getUserId(),
                        v.getFailures(), v.getLastAttemptAt(), v.getDistinctIps()))
                .toList();
    }

    // ---------------------------------------------------------------- sessions

    @PreAuthorize("hasAuthority('SECURITY_LOG_READ')")
    @GetMapping("/sessions")
    public PageResponse<AdminDtos.AdminSession> sessions(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.of(sessionService.allActiveSessions(
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "lastSeenAt"))), AdminUserController::session);
    }

    @PreAuthorize("hasAuthority('SECURITY_LOG_READ')")
    @GetMapping("/users/{id}/sessions")
    public List<AdminDtos.AdminSession> userSessions(@PathVariable Long id) {
        return sessionService.activeSessions(id).stream().map(AdminUserController::session).toList();
    }

    @PreAuthorize("hasAuthority('SESSION_REVOKE')")
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID sessionId) {
        Long actor = CurrentUser.requireId();
        sessionService.revokeByPublicId(sessionId, null, RevokedReason.ADMIN_REVOKED, actor);
        audit.record("SESSION_REVOKED", "AuthenticationSession", sessionId, java.util.Map.of());
    }

    @PreAuthorize("hasAuthority('SESSION_REVOKE')")
    @PostMapping("/users/{id}/sessions/revoke-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAll(@PathVariable Long id) {
        int n = sessionService.revokeAll(id, null, RevokedReason.ADMIN_REVOKED, CurrentUser.requireId());
        audit.record("SESSIONS_REVOKED", "User", id, java.util.Map.of("count", n));
    }

    // ---------------------------------------------------------------- mapping


    static AdminDtos.LoginHistoryEntry entry(LoginHistory h) {
        return new AdminDtos.LoginHistoryEntry(h.getId(), h.getUserId(), h.getEmailMasked(), h.getStatus().name(),
                h.getFailureReason() == null ? null : h.getFailureReason().name(), h.getOccurredAt(), h.getIpAddress(),
                h.getBrowser(), h.getBrowserVersion(), h.getOperatingSystem(),
                h.getDeviceCategory() == null ? null : h.getDeviceCategory().name(), h.getAuthSessionPublicId());
    }

    static AdminDtos.AdminSession session(AuthenticationSession s) {
        return new AdminDtos.AdminSession(s.getPublicId(), s.getUser().getId(), s.getUser().getEmail(), s.getCreatedAt(),
                s.getLastSeenAt(), s.getIdleExpiresAt().isBefore(s.getAbsoluteExpiresAt()) ? s.getIdleExpiresAt() : s.getAbsoluteExpiresAt(),
                s.getIpAddress(), s.getBrowser(), s.getOperatingSystem(),
                s.getDeviceCategory() == null ? null : s.getDeviceCategory().name());
    }
}
