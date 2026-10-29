package com.org.ecommerce.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Principal stored in the SecurityContext for a request authenticated by session cookie.
 * Holds identifiers only; services re-load entities when they need more.
 */
public record AuthenticatedUser(
        Long userId,
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified,
        Long sessionId,
        UUID sessionPublicId,
        Set<String> roles,
        Set<String> permissions) implements Serializable {

    public boolean isStaff() {
        return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_STAFF");
    }
}
