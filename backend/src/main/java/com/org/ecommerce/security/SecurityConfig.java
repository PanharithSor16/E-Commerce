package com.org.ecommerce.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.service.SessionService;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /** Paths that must work without login. Everything else requires authentication. */
    private static final String[] PUBLIC_GET = {
            "/api/v1/products/**", "/api/v1/categories/**", "/api/v1/shipping-methods", "/api/v1/media/**",
            "/api/v1/auth/csrf", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionService sessionService, SessionCookies cookies,
                                            RateLimiter rateLimiter, AppProperties properties,
                                            ProblemResponder responder) throws Exception {
        http
                // Our own session table replaces HttpSession; nothing is stored server-side by Spring.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .cors(c -> c.configurationSource(corsConfigurationSource(properties)))
                // Double-submit CSRF: XSRF-TOKEN cookie (readable by JS) must be echoed in X-XSRF-TOKEN.
                .csrf(csrf -> csrf
                        .spa()
                        // Signed by the payment provider; browsers cannot send credentials there.
                        .ignoringRequestMatchers("/api/v1/payments/webhooks/**")
                        // navigator.sendBeacon cannot set headers; events carry no privileges.
                        .ignoringRequestMatchers("/api/v1/analytics/events"))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; img-src 'self' data:; "
                                        + "style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        // Actuator is served only on the internal management port (never routed by nginx);
                        // exposure is limited to health/info/prometheus and health shows no details.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/logout", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email").permitAll()
                        .requestMatchers("/api/v1/cart/**", "/api/v1/cart").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/analytics/events", "/api/v1/consent").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/consent").permitAll()
                        .requestMatchers("/api/v1/payments/webhooks/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                responder.write(res, 401, "UNAUTHORIZED", "Authentication required."))
                        .accessDeniedHandler((req, res, ex) -> {
                            boolean csrf = ex instanceof org.springframework.security.web.csrf.CsrfException;
                            responder.write(res, 403, csrf ? "CSRF_INVALID" : "FORBIDDEN",
                                    csrf ? "Missing or invalid CSRF token." : "You do not have permission to perform this action.");
                        }))
                .addFilterBefore(new RateLimitFilter(rateLimiter, properties, responder), CsrfFilter.class)
                .addFilterBefore(new SessionTokenAuthenticationFilter(sessionService, cookies),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Forces the deferred CSRF token to load so the XSRF-TOKEN cookie is always present for the SPA. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
