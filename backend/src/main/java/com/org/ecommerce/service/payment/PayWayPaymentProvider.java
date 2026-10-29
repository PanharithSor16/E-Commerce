package com.org.ecommerce.service.payment;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ABA PayWay (Cambodia) e-commerce checkout over its REST API (no SDK dependency).
 *
 * <p>The browser POSTs a server-signed form to PayWay's hosted checkout page (cards, ABA Pay,
 * KHQR, ...), so card data never reaches this application. Every request is signed with
 * base64(HMAC-SHA512(api key, concatenated fields in PayWay's documented order)).
 *
 * <p>PayWay's pushback notification to {@code return_url} only carries the transaction id, so it
 * is treated as a hint: the real status always comes from the server-to-server
 * {@code check-transaction-2} lookup.
 */
public class PayWayPaymentProvider implements PaymentProvider {

    public static final String NAME = "PAYWAY";
    public static final String SANDBOX_URL = "https://checkout-sandbox.payway.com.kh";

    private static final Logger log = LoggerFactory.getLogger(PayWayPaymentProvider.class);
    private static final String PURCHASE_PATH = "/api/payment-gateway/v1/payments/purchase";
    private static final String CHECK_PATH = "/api/payment-gateway/v1/payments/check-transaction-2";
    private static final String CLOSE_PATH = "/api/payment-gateway/v1/payments/close-transaction";
    private static final String REFUND_PATH = "/api/merchant-portal/merchant-access/online-transaction/refund";
    private static final DateTimeFormatter REQ_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    /** PayWay: at most 20 characters, letters, digits and '-' only. */
    private static final Pattern TRAN_ID = Pattern.compile("[A-Za-z0-9-]{1,20}");
    private static final long MIN_LIFETIME_MINUTES = 3;

    private final String merchantId;
    private final byte[] apiKey;
    private final RSAPublicKey rsaPublicKey;
    private final String baseUrl;
    private final String callbackUrl;
    private final String paymentOption;
    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @param rsaPublicKeyPem PayWay's RSA public key (only needed for refunds; may be blank)
     * @param callbackUrl     absolute URL of the pushback endpoint PayWay calls after payment
     * @param paymentOption   blank lets the customer choose on PayWay's page; or e.g. cards, abapay_khqr
     */
    public PayWayPaymentProvider(String merchantId, String apiKey, String rsaPublicKeyPem, String baseUrl,
                                 String callbackUrl, String paymentOption, RestClient.Builder http,
                                 ObjectMapper objectMapper, Clock clock) {
        this.merchantId = merchantId;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.rsaPublicKey = rsaPublicKeyPem == null || rsaPublicKeyPem.isBlank() ? null : parsePublicKey(rsaPublicKeyPem);
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.callbackUrl = callbackUrl;
        this.paymentOption = paymentOption == null ? "" : paymentOption.trim();
        this.http = http.baseUrl(this.baseUrl).build();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CreatedPayment create(PaymentRequest request) {
        String tranId = request.reference();
        if (!TRAN_ID.matcher(tranId).matches()) {
            throw new IllegalArgumentException("PayWay tran_id must be 1-20 letters, digits or '-'");
        }
        return new CreatedPayment(tranId, null, Status.PENDING, checkout(tranId, request));
    }

    @Override
    public Optional<CreatedPayment> resume(String providerPaymentId, ProviderPayment current, PaymentRequest request) {
        if (lookup(providerPaymentId).isEmpty()) {
            // The customer never submitted the form: PayWay has not seen this tran_id, so re-sign it.
            return Optional.of(new CreatedPayment(providerPaymentId, null, Status.PENDING, checkout(providerPaymentId, request)));
        }
        // PayWay already holds an open transaction; close it so it cannot be paid alongside a new attempt.
        close(providerPaymentId);
        return Optional.empty();
    }

    @Override
    public ProviderPayment fetch(String providerPaymentId) {
        JsonNode data = lookup(providerPaymentId).orElse(null);
        if (data == null) {
            return new ProviderPayment(providerPaymentId, Status.PENDING, null, null, null, null, null);
        }
        Status status = status(data.path("payment_status").asString(""));
        return new ProviderPayment(providerPaymentId, status, null,
                status == Status.FAILED ? "declined" : null,
                status == Status.FAILED ? "The payment was declined." : null,
                null, null);
    }

    @Override
    public RefundOutcome refund(String providerPaymentId, BigDecimal amount, String idempotencyKey) {
        if (rsaPublicKey == null) {
            log.warn("PayWay refund requested but PAYWAY_RSA_PUBLIC_KEY is not configured");
            return new RefundOutcome(null, false, "Refunds require PAYWAY_RSA_PUBLIC_KEY.");
        }
        try {
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("mc_id", merchantId);
            auth.put("tran_id", providerPaymentId);
            auth.put("refund_amount", amount.setScale(2, RoundingMode.HALF_EVEN));
            String merchantAuth = rsaEncrypt(objectMapper.writeValueAsString(auth));
            String requestTime = reqTime();
            Map<String, String> body = new LinkedHashMap<>();
            body.put("request_time", requestTime);
            body.put("merchant_id", merchantId);
            body.put("merchant_auth", merchantAuth);
            body.put("hash", hash(requestTime + merchantId + merchantAuth));
            JsonNode res = postJson(REFUND_PATH, body);
            if ("00".equals(res.path("status").path("code").asString(""))) {
                return new RefundOutcome(null, true, null);
            }
            log.warn("PayWay refund rejected with code {}", res.path("status").path("code").asString(""));
        } catch (IllegalStateException e) {
            log.warn("PayWay refund failed: {}", e.getMessage());
        }
        return new RefundOutcome(null, false, "The payment provider rejected the refund.");
    }

    /**
     * Parses a pushback ({@code {"tran_id": "...", "apv": "...", "status": 0}}, JSON or form encoded)
     * and confirms it with PayWay. The pushback itself is unsigned, so its status is ignored.
     */
    @Override
    public Optional<WebhookEvent> parseWebhook(String payload, String signatureHeader) {
        String tranId = tranIdFrom(payload);
        if (tranId == null || !TRAN_ID.matcher(tranId).matches()) {
            throw new InvalidSignatureException("Pushback without a valid tran_id");
        }
        ProviderPayment verified = fetch(tranId);
        if (verified.status() == Status.PENDING || verified.status() == Status.REQUIRES_ACTION) {
            return Optional.empty();
        }
        // One event per (transaction, verified outcome): retries of the same pushback are de-duplicated.
        return Optional.of(new WebhookEvent(tranId + ":" + verified.status(), "pushback", verified));
    }

    // ------------------------------------------------------------------ requests

    /** The signed form the browser submits to PayWay's hosted checkout (multipart/form-data). */
    Checkout checkout(String tranId, PaymentRequest request) {
        String reqTime = reqTime();
        Payer payer = request.payer();
        // Insertion order is the hash order PayWay documents; blank fields are omitted from both.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("req_time", reqTime);
        fields.put("merchant_id", merchantId);
        fields.put("tran_id", tranId);
        fields.put("amount", formatAmount(request.amount(), request.currency()));
        if (payer != null) {
            fields.put("firstname", truncate(payer.firstName(), 20));
            fields.put("lastname", truncate(payer.lastName(), 20));
            fields.put("email", truncate(payer.email(), 50));
            fields.put("phone", truncate(digitsOnly(payer.phone()), 20));
        }
        fields.put("type", "purchase");
        fields.put("payment_option", paymentOption);
        fields.put("return_url", Base64.getEncoder().encodeToString(callbackUrl.getBytes(StandardCharsets.UTF_8)));
        fields.put("cancel_url", request.returnUrl() + (request.returnUrl().contains("?") ? "&" : "?") + "redirect_status=cancelled");
        fields.put("continue_success_url", request.returnUrl() + (request.returnUrl().contains("?") ? "&" : "?") + "redirect_status=payway");
        fields.put("currency", request.currency().toUpperCase(Locale.ROOT));
        if (request.expiresAt() != null) {
            long minutes = Duration.between(clock.instant(), request.expiresAt()).toMinutes();
            fields.put("lifetime", Long.toString(Math.max(MIN_LIFETIME_MINUTES, minutes)));
        }
        fields.values().removeIf(v -> v == null || v.isBlank());
        fields.put("hash", hash(String.join("", fields.values())));
        return new Checkout(baseUrl + PURCHASE_PATH, fields);
    }

    /** check-transaction-2: the transaction's data block, or empty if PayWay does not know the tran_id. */
    Optional<JsonNode> lookup(String tranId) {
        JsonNode res = postJson(CHECK_PATH, signedTranRequest(tranId));
        if (!"00".equals(res.path("status").path("code").asString(""))) {
            return Optional.empty();
        }
        return Optional.of(res.path("data"));
    }

    private void close(String tranId) {
        try {
            postJson(CLOSE_PATH, signedTranRequest(tranId));
        } catch (IllegalStateException e) {
            log.warn("Could not close PayWay transaction {}: {}", tranId, e.getMessage());
        }
    }

    private Map<String, String> signedTranRequest(String tranId) {
        String reqTime = reqTime();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("req_time", reqTime);
        body.put("merchant_id", merchantId);
        body.put("tran_id", tranId);
        body.put("hash", hash(reqTime + merchantId + tranId));
        return body;
    }

    private JsonNode postJson(String path, Map<String, String> body) {
        try {
            String res = http.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(res == null ? "{}" : res);
        } catch (RestClientResponseException e) {
            // Never log the response body: it can echo request data.
            throw new IllegalStateException("PayWay request failed with status " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new IllegalStateException("PayWay request failed: " + e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ helpers

    private String tranIdFrom(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readTree(trimmed).path("tran_id").asString(null);
            } catch (RuntimeException e) {
                return null;
            }
        }
        for (String pair : trimmed.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "tran_id".equals(URLDecoder.decode(kv[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static Status status(String paymentStatus) {
        return switch (paymentStatus.toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> Status.SUCCEEDED;
            case "DECLINED" -> Status.FAILED;
            case "CANCELLED" -> Status.CANCELLED;
            // PENDING, PRE-AUTH (not used: we only send type=purchase), REFUNDED (handled by our own refund flow)
            default -> Status.PENDING;
        };
    }

    /** USD is sent with 2 decimals; KHR has no minor unit. */
    static String formatAmount(BigDecimal amount, String currency) {
        int scale = "KHR".equalsIgnoreCase(currency) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_EVEN).toPlainString();
    }

    String hash(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(apiKey, "HmacSHA512"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** RSA/PKCS#1 v1.5 in (key size - 11)-byte chunks, concatenated and base64 encoded, as PayWay expects. */
    private String rsaEncrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] input = plain.getBytes(StandardCharsets.UTF_8);
            int chunk = rsaPublicKey.getModulus().bitLength() / 8 - 11;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < input.length; i += chunk) {
                out.writeBytes(cipher.doFinal(input, i, Math.min(chunk, input.length - i)));
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA encryption failed");
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        String body = pem.replace("\\n", "\n")
                .replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("PAYWAY_RSA_PUBLIC_KEY is not a valid PEM RSA public key");
        }
    }

    private String reqTime() {
        return REQ_TIME.format(clock.instant());
    }

    private static String digitsOnly(String s) {
        return s == null ? null : s.replaceAll("[^0-9+]", "");
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
