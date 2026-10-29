package com.org.ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Provider webhooks. Authenticated by signature verification, not by session or CSRF token. */
@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {

    private static final int MAX_PAYLOAD = 256 * 1024;
    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Stripe webhook endpoint")
    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(@RequestBody String payload,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (payload.length() > MAX_PAYLOAD) {
            return ResponseEntity.status(413).build();
        }
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "ABA PayWay pushback endpoint",
            description = "Unsigned notification; the backend confirms the status with PayWay before applying it.")
    @PostMapping("/payway")
    public ResponseEntity<Void> payway(@RequestBody(required = false) String payload) {
        if (payload != null && payload.length() > MAX_PAYLOAD) {
            return ResponseEntity.status(413).build();
        }
        paymentService.handleWebhook(payload, null);
        return ResponseEntity.ok().build();
    }
}
