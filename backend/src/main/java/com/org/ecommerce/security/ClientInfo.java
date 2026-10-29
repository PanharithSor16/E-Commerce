package com.org.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;

import com.org.ecommerce.entity.Enums.DeviceCategory;

/**
 * Request metadata used for security records. The IP comes from {@code getRemoteAddr()}, which
 * Tomcat's RemoteIpValve rewrites from X-Forwarded-For only when the immediate peer matches
 * {@code server.tomcat.remoteip.internal-proxies}; arbitrary forwarded headers are never trusted.
 */
public record ClientInfo(String ipAddress, String userAgent, String browser, String browserVersion, String os,
                         DeviceCategory deviceCategory) {

    private static final int MAX_UA = 512;

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_UA) {
            ua = ua.substring(0, MAX_UA);
        }
        UserAgentParser.Parsed parsed = UserAgentParser.parse(ua);
        String ip = request.getRemoteAddr();
        if (ip != null && ip.length() > 45) {
            ip = ip.substring(0, 45);
        }
        return new ClientInfo(ip, ua, parsed.browser(), parsed.browserVersion(), parsed.os(), parsed.device());
    }
}
