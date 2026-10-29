package com.org.ecommerce.security;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.org.ecommerce.config.AppProperties;

/**
 * Session and anonymous-cart cookies. Both are HttpOnly, SameSite=Lax and Secure in production.
 * The production session cookie uses the __Host- prefix (requires Secure, Path=/ and no Domain).
 */
@Component
public class SessionCookies {

    public static final String CART_COOKIE = "cart";
    private final AppProperties.Security security;

    public SessionCookies(AppProperties properties) {
        this.security = properties.security();
    }

    public String sessionCookieName() {
        return security.sessionCookieName();
    }

    public Optional<String> readSession(HttpServletRequest request) {
        return read(request, security.sessionCookieName());
    }

    public void writeSession(HttpServletResponse response, String token, Duration maxAge) {
        write(response, security.sessionCookieName(), token, maxAge);
    }

    public void clearSession(HttpServletResponse response) {
        write(response, security.sessionCookieName(), "", Duration.ZERO);
    }

    public Optional<String> readCart(HttpServletRequest request) {
        return read(request, CART_COOKIE);
    }

    public void writeCart(HttpServletResponse response, String token, Duration maxAge) {
        write(response, CART_COOKIE, token, maxAge);
    }

    public void clearCart(HttpServletResponse response) {
        write(response, CART_COOKIE, "", Duration.ZERO);
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank() && c.getValue().length() <= 128) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private void write(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(security.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
