package com.org.ecommerce.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.org.ecommerce.exception.ApiException;

/** Access to the authenticated principal. User identity always comes from here, never from request input. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> ApiException.unauthorized("Authentication required."));
    }

    public static Long requireId() {
        return require().userId();
    }

    public static Long idOrNull() {
        return get().map(AuthenticatedUser::userId).orElse(null);
    }
}
