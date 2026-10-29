package com.org.ecommerce.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.analytics.AnalyticsDtos;
import com.org.ecommerce.analytics.AnalyticsEventService;
import com.org.ecommerce.entity.Enums.ConsentType;
import com.org.ecommerce.security.ClientInfo;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.service.ConsentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Analytics")
@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    private final AnalyticsEventService analytics;
    private final ConsentService consentService;

    public AnalyticsController(AnalyticsEventService analytics, ConsentService consentService) {
        this.analytics = analytics;
        this.consentService = consentService;
    }

    @Operation(summary = "Record a batch of consented storefront events",
            description = "Only allowlisted event types and properties are accepted. PURCHASE, LOGIN, LOGOUT and "
                    + "REGISTER are recorded by the server and rejected here.")
    @PostMapping("/analytics/events")
    public ResponseEntity<AnalyticsDtos.IngestResult> events(@Valid @RequestBody AnalyticsDtos.EventBatch batch,
                                                             HttpServletRequest request) {
        // Body size (max 16 KB) is enforced by RateLimitFilter before parsing.
        ClientInfo client = ClientInfo.from(request);
        if (client.deviceCategory() == com.org.ecommerce.entity.Enums.DeviceCategory.BOT) {
            return ResponseEntity.accepted().body(new AnalyticsDtos.IngestResult(0, batch.events().size()));
        }
        return ResponseEntity.accepted().body(analytics.ingest(batch, CurrentUser.idOrNull(), client));
    }

    public record ConsentRequest(UUID visitorId, boolean analytics, boolean marketing) {
    }

    public record ConsentResponse(boolean analytics, boolean marketing, String policyVersion) {
    }

    @Operation(summary = "Record the cookie banner decision")
    @PostMapping("/consent")
    public ConsentResponse consent(@RequestBody ConsentRequest body) {
        Long userId = CurrentUser.idOrNull();
        consentService.recordFromBanner(body.visitorId(), userId, ConsentType.ANALYTICS, body.analytics());
        consentService.recordFromBanner(body.visitorId(), userId, ConsentType.MARKETING, body.marketing());
        return new ConsentResponse(body.analytics(), body.marketing(), consentService.policyVersion());
    }

    @GetMapping("/consent")
    public ConsentResponse currentConsent() {
        Long userId = CurrentUser.idOrNull();
        if (userId == null) {
            return new ConsentResponse(false, false, consentService.policyVersion());
        }
        return new ConsentResponse(consentService.currentForUser(userId, ConsentType.ANALYTICS),
                consentService.currentForUser(userId, ConsentType.MARKETING), consentService.policyVersion());
    }
}
