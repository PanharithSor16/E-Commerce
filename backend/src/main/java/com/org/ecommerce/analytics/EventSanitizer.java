package com.org.ecommerce.analytics;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.org.ecommerce.entity.Enums.TrafficSource;

/**
 * Normalizes untrusted analytics input: paths and referrers lose query strings and fragments,
 * campaign values are restricted to a safe character set, and sensitive areas are excluded.
 */
public final class EventSanitizer {

    /** Pages that are never tracked (account data, auth flows, admin, payment step). */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/account", "/admin", "/login", "/register", "/forgot-password", "/reset-password", "/verify-email",
            "/checkout/payment");
    private static final Pattern SAFE_CAMPAIGN = Pattern.compile("[^A-Za-z0-9 _.\\-]");
    private static final Pattern SAFE_PATH = Pattern.compile("[^A-Za-z0-9/_.\\-~%]");
    private static final List<String> SEARCH_ENGINES = List.of("google.", "bing.", "duckduckgo.", "yahoo.", "baidu.",
            "yandex.", "ecosia.");
    private static final List<String> SOCIAL = List.of("facebook.", "instagram.", "t.co", "twitter.", "x.com",
            "linkedin.", "tiktok.", "pinterest.", "reddit.", "youtube.");

    private EventSanitizer() {
    }

    /** Returns a path like "/products/42", or null if the input is unusable. */
    public static String path(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String p = raw.trim();
        int cut = indexOfAny(p, '?', '#');
        if (cut >= 0) {
            p = p.substring(0, cut);
        }
        if (!p.startsWith("/") || p.startsWith("//")) {
            return null;
        }
        p = SAFE_PATH.matcher(p).replaceAll("");
        return truncate(p, 512);
    }

    public static boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return EXCLUDED_PREFIXES.stream().anyMatch(prefix -> lower.equals(prefix) || lower.startsWith(prefix + "/"));
    }

    /** "https://www.google.com/search?q=secret" -> "https://www.google.com/search". */
    public static String referrer(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2048) {
            return null;
        }
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return null;
            }
            String path = uri.getRawPath() == null ? "" : SAFE_PATH.matcher(uri.getRawPath()).replaceAll("");
            return truncate(scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + path, 512);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String referrerHost(String sanitizedReferrer) {
        if (sanitizedReferrer == null) {
            return null;
        }
        return truncate(URI.create(sanitizedReferrer).getHost(), 255);
    }

    public static String campaign(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = SAFE_CAMPAIGN.matcher(raw.trim()).replaceAll("");
        return v.isEmpty() ? null : truncate(v, 100);
    }

    public static String searchQuery(String raw) {
        if (raw == null) {
            return null;
        }
        // Collapse whitespace, drop control characters; queries are kept short and never contain emails.
        String q = raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (q.contains("@")) {
            return null;
        }
        return q.isEmpty() ? null : truncate(q, 100);
    }

    public static TrafficSource classify(String referrerHost, String ownHost, String utmSource, String utmMedium) {
        String medium = utmMedium == null ? "" : utmMedium.toLowerCase(Locale.ROOT);
        if (medium.matches("cpc|ppc|paid.*|display|cpm")) {
            return TrafficSource.PAID;
        }
        if (medium.equals("email") || medium.equals("newsletter")) {
            return TrafficSource.EMAIL;
        }
        if (medium.equals("social") || medium.equals("social-network")) {
            return TrafficSource.SOCIAL;
        }
        if (utmSource != null || utmMedium != null) {
            return TrafficSource.CAMPAIGN;
        }
        if (referrerHost == null || referrerHost.equalsIgnoreCase(ownHost)) {
            return TrafficSource.DIRECT;
        }
        String host = referrerHost.toLowerCase(Locale.ROOT);
        if (SEARCH_ENGINES.stream().anyMatch(host::contains)) {
            return TrafficSource.ORGANIC;
        }
        if (SOCIAL.stream().anyMatch(host::contains)) {
            return TrafficSource.SOCIAL;
        }
        return TrafficSource.REFERRAL;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) {
            return j;
        }
        return j < 0 ? i : Math.min(i, j);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
