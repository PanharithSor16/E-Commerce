package com.org.ecommerce.analytics;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.entity.Enums.EventType;
import com.org.ecommerce.entity.Enums.TrafficSource;
import com.org.ecommerce.security.ClientInfo;

import tools.jackson.databind.ObjectMapper;

/**
 * Ingests consented client events and records trusted server events.
 * <ul>
 *   <li>Client batches are validated, sanitized and deduplicated by EventId.</li>
 *   <li>Server-only types (PURCHASE, LOGIN, LOGOUT, REGISTER) are rejected from clients.</li>
 *   <li>PURCHASE is unique per order (filtered unique index), so confirmation can run many times.</li>
 * </ul>
 * Plain JDBC is used for idempotent upserts that JPA cannot express efficiently.
 */
@Service
public class AnalyticsEventService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration sessionTimeout;
    private final String ownHost;

    public AnalyticsEventService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock,
                                 AppProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.sessionTimeout = properties.analytics().sessionTimeout();
        this.ownHost = URI.create(properties.publicBaseUrl()).getHost();
    }

    @Transactional
    public AnalyticsDtos.IngestResult ingest(AnalyticsDtos.EventBatch batch, Long userId, ClientInfo client) {
        Instant now = clock.instant();
        List<AnalyticsDtos.ClientEvent> events = batch.events();

        // Validate referenced products/categories in two queries instead of one per event.
        Set<Long> validProducts = existingIds("SELECT Id FROM dbo.Products WHERE Status = 'ACTIVE' AND Id IN (:ids)",
                events.stream().map(AnalyticsDtos.ClientEvent::productId).collect(Collectors.toSet()));
        Set<Long> validCategories = existingIds("SELECT Id FROM dbo.Categories WHERE Active = 1 AND Id IN (:ids)",
                events.stream().map(AnalyticsDtos.ClientEvent::categoryId).collect(Collectors.toSet()));

        long visitorId = upsertVisitor(batch.visitorId(), now);
        Long sessionDbId = null;
        int accepted = 0;
        int rejected = 0;
        for (AnalyticsDtos.ClientEvent e : events) {
            EventType type = parseType(e.type());
            if (type == null || type.serverOnly()) {
                rejected++;
                continue;
            }
            String path = EventSanitizer.path(e.path());
            if (path != null && EventSanitizer.isExcludedPath(path)) {
                rejected++;
                continue;
            }
            Long productId = e.productId() != null && validProducts.contains(e.productId()) ? e.productId() : null;
            Long categoryId = e.categoryId() != null && validCategories.contains(e.categoryId()) ? e.categoryId() : null;
            if ((type == EventType.PRODUCT_VIEW && productId == null) || (type == EventType.CATEGORY_VIEW && categoryId == null)) {
                rejected++;
                continue;
            }
            String referrer = EventSanitizer.referrer(e.referrer());
            String utmSource = EventSanitizer.campaign(e.utmSource());
            String utmMedium = EventSanitizer.campaign(e.utmMedium());
            String utmCampaign = EventSanitizer.campaign(e.utmCampaign());
            if (sessionDbId == null) {
                sessionDbId = upsertSession(batch.sessionId(), visitorId, userId, now, path, referrer, utmSource,
                        utmMedium, utmCampaign, client);
            }
            boolean inserted = insertEvent(new EventRow(e.eventId(), type, "CLIENT", now, visitorId, sessionDbId, userId,
                    path, productId, categoryId, null, referrer, utmSource, utmMedium, utmCampaign, client,
                    propertiesJson(type, e.properties())));
            if (inserted) {
                accepted++;
                if (type == EventType.PAGE_VIEW) {
                    jdbc.update("UPDATE dbo.VisitorSessions SET PageViewCount = PageViewCount + 1 WHERE Id = :id",
                            Map.of("id", sessionDbId));
                }
            } else {
                rejected++; // duplicate EventId: already recorded
            }
        }
        return new AnalyticsDtos.IngestResult(accepted, rejected);
    }

    /**
     * Records a trusted server-side event. Without a visitor key (no analytics consent) only
     * PURCHASE is recorded, unattributed, so purchase counts stay complete.
     * Runs in its own transaction so analytics can never roll back business operations.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordServerEvent(EventType type, UUID visitorKey, UUID sessionKey, Long userId, Long orderId) {
        if (visitorKey == null && type != EventType.PURCHASE) {
            return;
        }
        try {
            Instant now = clock.instant();
            Long visitorId = null;
            Long sessionDbId = null;
            if (visitorKey != null) {
                visitorId = upsertVisitor(visitorKey, now);
                if (sessionKey != null) {
                    sessionDbId = findSessionId(sessionKey);
                }
            }
            insertEvent(new EventRow(UUID.randomUUID(), type, "SERVER", now, visitorId, sessionDbId,
                    visitorKey != null ? userId : null, null, null, null, orderId, null, null, null, null, null, null));
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate server event {} for order {} ignored", type, orderId);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private record EventRow(UUID eventId, EventType type, String source, Instant at, Long visitorId, Long sessionId,
                            Long userId, String path, Long productId, Long categoryId, Long orderId, String referrer,
                            String utmSource, String utmMedium, String utmCampaign, ClientInfo client,
                            String properties) {
    }

    private boolean insertEvent(EventRow r) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("eventId", r.eventId())
                .addValue("type", r.type().name())
                .addValue("source", r.source())
                .addValue("at", Timestamp.from(r.at()))
                .addValue("visitorId", r.visitorId())
                .addValue("sessionId", r.sessionId())
                .addValue("userId", r.userId())
                .addValue("path", r.path())
                .addValue("productId", r.productId())
                .addValue("categoryId", r.categoryId())
                .addValue("orderId", r.orderId())
                .addValue("referrer", r.referrer())
                .addValue("utmSource", r.utmSource())
                .addValue("utmMedium", r.utmMedium())
                .addValue("utmCampaign", r.utmCampaign())
                .addValue("device", r.client() == null ? null : r.client().deviceCategory().name())
                .addValue("browser", r.client() == null ? null : r.client().browser())
                .addValue("os", r.client() == null ? null : r.client().os())
                .addValue("props", r.properties());
        try {
            int rows = jdbc.update("""
                    INSERT INTO dbo.AnalyticsEvents (EventId, EventType, Source, OccurredAt, VisitorId, VisitorSessionId,
                        UserId, PagePath, ProductId, CategoryId, OrderId, Referrer, UtmSource, UtmMedium, UtmCampaign,
                        DeviceCategory, Browser, OperatingSystem, Properties)
                    SELECT :eventId, :type, :source, :at, :visitorId, :sessionId, :userId, :path, :productId, :categoryId,
                        :orderId, :referrer, :utmSource, :utmMedium, :utmCampaign, :device, :browser, :os, :props
                    WHERE NOT EXISTS (SELECT 1 FROM dbo.AnalyticsEvents WHERE EventId = :eventId)
                      AND (:orderId IS NULL OR NOT EXISTS (
                          SELECT 1 FROM dbo.AnalyticsEvents WHERE EventType = 'PURCHASE' AND OrderId = :orderId))
                    """, p);
            return rows > 0;
        } catch (DuplicateKeyException e) {
            return false; // concurrent duplicate lost the race to the unique index
        }
    }

    private long upsertVisitor(UUID visitorKey, Instant now) {
        Long id = jdbc.queryForObject("""
                MERGE dbo.Visitors WITH (HOLDLOCK) AS t
                USING (SELECT :key AS VisitorKey) AS s ON t.VisitorKey = s.VisitorKey
                WHEN MATCHED THEN UPDATE SET LastSeenAt = :now
                WHEN NOT MATCHED THEN INSERT (VisitorKey, FirstSeenAt, LastSeenAt) VALUES (:key, :now, :now)
                OUTPUT inserted.Id;
                """, Map.of("key", visitorKey, "now", Timestamp.from(now)), Long.class);
        return id;
    }

    /**
     * Finds or starts the visitor session. A client session key whose last activity is older than the
     * session timeout is treated as expired: activity is still recorded, but traffic attribution stays
     * with the original session (the client rotates its key after inactivity).
     */
    private long upsertSession(UUID sessionKey, long visitorId, Long userId, Instant now, String landingPath,
                               String referrer, String utmSource, String utmMedium, String utmCampaign,
                               ClientInfo client) {
        String referrerHost = EventSanitizer.referrerHost(referrer);
        TrafficSource source = EventSanitizer.classify(referrerHost, ownHost, utmSource, utmMedium);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("key", sessionKey)
                .addValue("visitorId", visitorId)
                .addValue("userId", userId)
                .addValue("now", Timestamp.from(now))
                .addValue("landing", landingPath)
                .addValue("refHost", referrerHost != null && referrerHost.equalsIgnoreCase(ownHost) ? null : referrerHost)
                .addValue("utmSource", utmSource)
                .addValue("utmMedium", utmMedium)
                .addValue("utmCampaign", utmCampaign)
                .addValue("traffic", source.name())
                .addValue("device", client.deviceCategory().name())
                .addValue("browser", client.browser())
                .addValue("os", client.os());
        return jdbc.queryForObject("""
                MERGE dbo.VisitorSessions WITH (HOLDLOCK) AS t
                USING (SELECT :key AS SessionKey) AS s ON t.SessionKey = s.SessionKey
                WHEN MATCHED THEN UPDATE SET LastActivityAt = :now, UserId = COALESCE(t.UserId, :userId)
                WHEN NOT MATCHED THEN INSERT (SessionKey, VisitorId, UserId, StartedAt, LastActivityAt, LandingPath,
                    ReferrerHost, UtmSource, UtmMedium, UtmCampaign, TrafficSource, DeviceCategory, Browser, OperatingSystem)
                    VALUES (:key, :visitorId, :userId, :now, :now, :landing, :refHost, :utmSource, :utmMedium,
                    :utmCampaign, :traffic, :device, :browser, :os)
                OUTPUT inserted.Id;
                """, p, Long.class);
    }

    private Long findSessionId(UUID sessionKey) {
        List<Long> ids = jdbc.queryForList("SELECT Id FROM dbo.VisitorSessions WHERE SessionKey = :key",
                Map.of("key", sessionKey), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Set<Long> existingIds(String sql, Set<Long> ids) {
        Set<Long> nonNull = new HashSet<>(ids);
        nonNull.remove(null);
        if (nonNull.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(sql, Map.of("ids", nonNull), Long.class));
    }

    private String propertiesJson(EventType type, AnalyticsDtos.Properties p) {
        if (p == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        switch (type) {
            case SEARCH -> {
                putIfNotNull(m, "query", EventSanitizer.searchQuery(p.query()));
                putIfNotNull(m, "resultsCount", p.resultsCount());
            }
            case ADD_TO_CART, REMOVE_FROM_CART -> {
                putIfNotNull(m, "variantId", p.variantId());
                putIfNotNull(m, "quantity", p.quantity());
            }
            case BEGIN_CHECKOUT -> putIfNotNull(m, "itemCount", p.itemCount());
            default -> {
                // no properties allowed for other types
            }
        }
        return m.isEmpty() ? null : objectMapper.writeValueAsString(m);
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    private static EventType parseType(String raw) {
        try {
            return EventType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /** Session timeout exposed for the client contract (documented in /api/v1/analytics/config). */
    public Duration sessionTimeout() {
        return sessionTimeout;
    }
}
