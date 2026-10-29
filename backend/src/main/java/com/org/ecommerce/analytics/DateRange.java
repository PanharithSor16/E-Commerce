package com.org.ecommerce.analytics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.org.ecommerce.exception.ApiException;

/**
 * Half-open UTC reporting window [from, to). Presets: today, 7d, 30d, this_month, last_month;
 * or custom ISO dates (inclusive end date). Ranges are capped at 366 days.
 */
public record DateRange(Instant from, Instant to) {

    private static final Duration MAX = Duration.ofDays(366);

    public static DateRange resolve(String preset, LocalDate fromDate, LocalDate toDate, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        DateRange r;
        if (fromDate != null && toDate != null) {
            if (toDate.isBefore(fromDate)) {
                throw ApiException.badRequest("INVALID_RANGE", "'to' must not be before 'from'.");
            }
            r = days(fromDate, toDate.plusDays(1));
        } else {
            String p = preset == null ? "30d" : preset;
            r = switch (p) {
                case "today" -> days(today, today.plusDays(1));
                case "7d" -> days(today.minusDays(6), today.plusDays(1));
                case "30d" -> days(today.minusDays(29), today.plusDays(1));
                case "this_month" -> days(today.withDayOfMonth(1), today.plusDays(1));
                case "last_month" -> days(today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1));
                default -> throw ApiException.badRequest("INVALID_RANGE", "Unknown range preset '" + p + "'.");
            };
        }
        if (Duration.between(r.from, r.to).compareTo(MAX) > 0) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Date ranges are limited to 366 days.");
        }
        return r;
    }

    /** The window of the same length immediately before this one (for period comparisons). */
    public DateRange previous() {
        Duration length = Duration.between(from, to);
        return new DateRange(from.minus(length), from);
    }

    private static DateRange days(LocalDate start, LocalDate endExclusive) {
        return new DateRange(start.atStartOfDay().toInstant(ZoneOffset.UTC), endExclusive.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
