package com.org.ecommerce.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.entity.Enums.ConsentSource;
import com.org.ecommerce.entity.Enums.ConsentType;
import com.org.ecommerce.entity.UserConsent;
import com.org.ecommerce.repository.UserConsentRepository;

/** Append-only consent log for users and anonymous visitors. */
@Service
public class ConsentService {

    private final UserConsentRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final String policyVersion;

    public ConsentService(UserConsentRepository repository, NamedParameterJdbcTemplate jdbc, AppProperties properties) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.policyVersion = properties.analytics().privacyPolicyVersion();
    }

    @Transactional
    public void recordForUser(Long userId, ConsentType type, boolean granted, ConsentSource source) {
        UserConsent c = new UserConsent();
        c.setUserId(userId);
        c.setConsentType(type);
        c.setGranted(granted);
        c.setSource(source);
        c.setPolicyVersion(policyVersion);
        repository.save(c);
    }

    /**
     * Records a banner decision. The visitor row is created only when consent is granted; a
     * refusal from an unknown visitor is logged against the user (if signed in) or not at all,
     * because creating an identifier for someone who refused tracking defeats the purpose.
     */
    @Transactional
    public void recordFromBanner(UUID visitorKey, Long userId, ConsentType type, boolean granted) {
        Long visitorId = null;
        if (visitorKey != null) {
            visitorId = granted
                    ? jdbc.queryForObject("""
                        MERGE dbo.Visitors WITH (HOLDLOCK) AS t
                        USING (SELECT :key AS VisitorKey) AS s ON t.VisitorKey = s.VisitorKey
                        WHEN MATCHED THEN UPDATE SET LastSeenAt = SYSUTCDATETIME()
                        WHEN NOT MATCHED THEN INSERT (VisitorKey) VALUES (:key)
                        OUTPUT inserted.Id;
                        """, Map.of("key", visitorKey), Long.class)
                    : jdbc.queryForList("SELECT Id FROM dbo.Visitors WHERE VisitorKey = :key",
                            Map.of("key", visitorKey), Long.class).stream().findFirst().orElse(null);
        }
        if (visitorId == null && userId == null) {
            return;
        }
        UserConsent c = new UserConsent();
        c.setUserId(userId);
        c.setVisitorId(visitorId);
        c.setConsentType(type);
        c.setGranted(granted);
        c.setSource(ConsentSource.BANNER);
        c.setPolicyVersion(policyVersion);
        repository.save(c);
    }

    @Transactional(readOnly = true)
    public boolean currentForUser(Long userId, ConsentType type) {
        return repository.findFirstByUserIdAndConsentTypeOrderByRecordedAtDescIdDesc(userId, type)
                .map(UserConsent::isGranted)
                .orElse(false);
    }

    public String policyVersion() {
        return policyVersion;
    }
}
