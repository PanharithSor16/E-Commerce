package com.org.ecommerce.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.entity.Enums.LoginFailureReason;
import com.org.ecommerce.entity.Enums.LoginStatus;
import com.org.ecommerce.entity.LoginHistory;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.LoginHistoryRepository;
import com.org.ecommerce.security.ClientInfo;
import com.org.ecommerce.security.TokenHasher;

/**
 * Records every login attempt and serves the admin login-history views. Writes use REQUIRES_NEW so
 * failed attempts are persisted even though the login request itself fails.
 */
@Service
public class LoginHistoryService {

    private final LoginHistoryRepository repository;
    private final TokenHasher hasher;
    private final Clock clock;

    public LoginHistoryService(LoginHistoryRepository repository, TokenHasher hasher, Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, String email, ClientInfo client, UUID sessionPublicId) {
        save(userId, email, LoginStatus.SUCCESS, null, client, sessionPublicId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, String email, LoginFailureReason reason, ClientInfo client) {
        save(userId, email, LoginStatus.FAILURE, reason, client, null);
    }

    /** Failed attempts for a user inside the lockout window (used to decide on account lockout). */
    @Transactional(readOnly = true)
    public long recentFailures(Long userId, Instant since) {
        Specification<LoginHistory> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.equal(root.get("status"), LoginStatus.FAILURE),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), since));
        return repository.count(spec);
    }

    public record Filter(Long userId, String email, LoginStatus status, Instant from, Instant to, String ipAddress) {
    }

    @Transactional(readOnly = true)
    public Page<LoginHistory> search(Filter f, Pageable pageable) {
        Specification<LoginHistory> spec = (root, q, cb) -> cb.conjunction();
        if (f.userId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("userId"), f.userId()));
        }
        if (f.email() != null && !f.email().isBlank()) {
            // Exact-match search on the keyed hash: the email itself is never stored in clear.
            String hash = hasher.emailHash(f.email());
            spec = spec.and((root, q, cb) -> cb.equal(root.get("emailHash"), hash));
        }
        if (f.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
        }
        if (f.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("occurredAt"), f.to()));
        }
        if (f.ipAddress() != null && !f.ipAddress().isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("ipAddress"), f.ipAddress().trim()));
        }
        return repository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public LoginHistory get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Login record"));
    }

    @Transactional(readOnly = true)
    public List<LoginHistoryRepository.RepeatedFailureView> repeatedFailures(Instant since, long threshold, int limit) {
        return repository.findRepeatedFailures(since, threshold, PageRequest.of(0, limit));
    }

    private void save(Long userId, String email, LoginStatus status, LoginFailureReason reason, ClientInfo client,
                      UUID sessionPublicId) {
        LoginHistory h = new LoginHistory();
        h.setUserId(userId);
        h.setEmailHash(hasher.emailHash(email));
        h.setEmailMasked(TokenHasher.maskEmail(email));
        h.setStatus(status);
        h.setFailureReason(reason);
        h.setOccurredAt(clock.instant());
        h.setIpAddress(client.ipAddress());
        h.setUserAgent(client.userAgent());
        h.setBrowser(client.browser());
        h.setBrowserVersion(client.browserVersion());
        h.setOperatingSystem(client.os());
        h.setDeviceCategory(client.deviceCategory());
        h.setAuthSessionPublicId(sessionPublicId);
        repository.save(h);
    }
}
