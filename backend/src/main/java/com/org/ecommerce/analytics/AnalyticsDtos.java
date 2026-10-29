package com.org.ecommerce.analytics;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Wire format for POST /api/v1/analytics/events. Only these fields are accepted. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record EventBatch(
            @NotNull UUID visitorId,
            @NotNull UUID sessionId,
            @NotEmpty @Size(max = 20) List<@Valid ClientEvent> events) {
    }

    public record ClientEvent(
            @NotNull UUID eventId,
            @NotNull @Size(max = 24) String type,
            @Size(max = 2048) String path,
            @Size(max = 2048) String referrer,
            @Positive Long productId,
            @Positive Long categoryId,
            @Size(max = 200) String utmSource,
            @Size(max = 200) String utmMedium,
            @Size(max = 200) String utmCampaign,
            @Valid Properties properties) {
    }

    /** Allowlisted event properties; anything else in the payload is ignored by the JSON binder. */
    public record Properties(
            @Size(max = 200) String query,
            @Min(0) @Max(100000) Integer resultsCount,
            @Positive Long variantId,
            @Min(1) @Max(99) Integer quantity,
            @Min(0) @Max(1000) Integer itemCount) {
    }

    public record IngestResult(int accepted, int rejected) {
    }
}
