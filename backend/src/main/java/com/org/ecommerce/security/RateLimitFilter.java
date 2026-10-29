package com.org.ecommerce.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.org.ecommerce.config.AppProperties;

/**
 * Per-IP rate limits for abuse-prone public endpoints: authentication (credential stuffing,
 * reset-email flooding) and analytics ingestion (event spam). Account lockout is separate.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final long MAX_ANALYTICS_BODY = 16 * 1024;

    private final RateLimiter limiter;
    private final AppProperties properties;
    private final ProblemResponder responder;

    public RateLimitFilter(RateLimiter limiter, AppProperties properties, ProblemResponder responder) {
        this.limiter = limiter;
        this.properties = properties;
        this.responder = responder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();
        boolean allowed = true;
        if ("POST".equals(request.getMethod()) && path.startsWith("/api/v1/auth/")) {
            allowed = limiter.tryAcquire("auth:" + ip, properties.security().authRateLimitPerMinute());
        } else if (path.startsWith("/api/v1/analytics/")) {
            // Reject oversized (or unbounded chunked) bodies before they are parsed.
            long length = request.getContentLengthLong();
            if ("POST".equals(request.getMethod()) && (length < 0 || length > MAX_ANALYTICS_BODY)) {
                responder.write(response, 413, "PAYLOAD_TOO_LARGE", "Request body too large.");
                return;
            }
            allowed = limiter.tryAcquire("analytics:" + ip, properties.security().analyticsRateLimitPerMinute());
        }
        if (!allowed) {
            response.setHeader("Retry-After", "60");
            responder.write(response, 429, "RATE_LIMITED", "Too many requests. Please try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
