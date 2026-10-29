package com.org.ecommerce.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.org.ecommerce.config.AppProperties;

/**
 * Generates opaque random tokens and hashes them. Raw tokens are only ever sent to the
 * client (cookie or email); the database stores SHA-256 hashes.
 */
@Component
public class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] emailHashKey;

    public TokenHasher(AppProperties properties) {
        this.emailHashKey = Base64.getDecoder().decode(properties.security().emailHashKey());
        if (emailHashKey.length < 32) {
            throw new IllegalStateException("app.security.email-hash-key must be at least 32 bytes (base64)");
        }
    }

    /** 256-bit random token, URL-safe Base64 without padding. */
    public String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Keyed hash of a normalized email for login history search without storing the address in clear. */
    public String emailHash(String email) {
        return hmacSha256Hex(emailHashKey, normalizeEmail(email));
    }

    public static String hmacSha256Hex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** "john.doe@example.com" -> "j***@example.com". */
    public static String maskEmail(String email) {
        String e = normalizeEmail(email);
        int at = e.indexOf('@');
        if (at <= 0) {
            return e.isEmpty() ? "***" : e.charAt(0) + "***";
        }
        String domain = e.substring(at);
        return e.charAt(0) + "***" + (domain.length() > 300 ? domain.substring(0, 300) : domain);
    }
}
