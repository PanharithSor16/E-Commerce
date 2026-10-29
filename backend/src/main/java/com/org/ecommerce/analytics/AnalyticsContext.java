package com.org.ecommerce.analytics;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Consent-scoped analytics identifiers read from the first-party cookies the storefront sets
 * only after analytics consent ("vid" = visitor, "vsid" = visitor session). Both are random UUIDs.
 */
public record AnalyticsContext(UUID visitorKey, UUID sessionKey) {

    public static final String VISITOR_COOKIE = "vid";
    public static final String SESSION_COOKIE = "vsid";

    public static AnalyticsContext from(HttpServletRequest request) {
        UUID visitor = null;
        UUID session = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (VISITOR_COOKIE.equals(c.getName())) {
                    visitor = parse(c.getValue());
                } else if (SESSION_COOKIE.equals(c.getName())) {
                    session = parse(c.getValue());
                }
            }
        }
        return new AnalyticsContext(visitor, visitor == null ? null : session);
    }

    private static UUID parse(String v) {
        if (v == null || v.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
