package com.org.ecommerce.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.org.ecommerce.service.SessionService;

/**
 * Authenticates requests from the opaque session cookie. Authorities contain both the role names
 * and the permission names, so endpoints can use {@code hasAuthority('ORDER_REFUND')}.
 */
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final SessionCookies cookies;

    public SessionTokenAuthenticationFilter(SessionService sessionService, SessionCookies cookies) {
        this.sessionService = sessionService;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.readSession(request).ifPresent(token -> sessionService.authenticate(token).ifPresentOrElse(
                user -> {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
                    user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new SessionAuthenticationToken(user, authorities));
                    SecurityContextHolder.setContext(context);
                },
                // Stale or revoked cookie: remove it so the browser stops sending it.
                () -> cookies.clearSession(response)));
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/media/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
