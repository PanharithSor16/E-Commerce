package com.org.ecommerce.controller.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.dto.PageResponse;
import com.org.ecommerce.dto.admin.AdminDtos;
import com.org.ecommerce.entity.AuditLog;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - Audit")
@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AuditService audit;

    public AdminAuditController(AuditService audit) {
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @GetMapping
    public PageResponse<AdminDtos.AuditEntry> list(@RequestParam(required = false) Long actorUserId,
                                                   @RequestParam(required = false) String entityType,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.of(audit.search(actorUserId, entityType, action, page, size), AdminAuditController::toEntry);
    }

    static AdminDtos.AuditEntry toEntry(AuditLog a) {
        return new AdminDtos.AuditEntry(a.getId(), a.getActorUserId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getOutcome().name(), a.getDetails(), a.getIpAddress(), a.getOccurredAt());
    }
}
