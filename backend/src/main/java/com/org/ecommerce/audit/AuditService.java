package com.org.ecommerce.audit;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.org.ecommerce.entity.AuditLog;
import com.org.ecommerce.entity.Enums.AuditOutcome;
import com.org.ecommerce.repository.AuditLogRepository;
import com.org.ecommerce.security.CurrentUser;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes the audit trail for administrative and security-relevant actions. Details are small,
 * allowlisted maps; callers must never pass secrets, tokens or full request bodies.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Recorded in the caller's transaction, so a rolled-back action leaves no "success" entry. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
        save(action, entityType, entityId, details, AuditOutcome.SUCCESS, CurrentUser.idOrNull());
    }

    /** Failures and denials are committed independently of the failing transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(String action, String entityType, Object entityId, Map<String, ?> details,
                              AuditOutcome outcome, Long actorId) {
        save(action, entityType, entityId, details, outcome, actorId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(Long actorUserId, String entityType, String action, int page, int size) {
        Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();
        if (actorUserId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    private void save(String action, String entityType, Object entityId, Map<String, ?> details, AuditOutcome outcome,
                      Long actorId) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setOutcome(outcome);
        if (details != null && !details.isEmpty()) {
            String json = objectMapper.writeValueAsString(details);
            log.setDetails(json.length() > 4000 ? json.substring(0, 4000) : json);
        }
        log.setIpAddress(currentIp());
        repository.save(log);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
