package com.org.ecommerce.mapper;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.org.ecommerce.dto.auth.AuthDtos;
import com.org.ecommerce.entity.AuthenticationSession;
import com.org.ecommerce.entity.Permission;
import com.org.ecommerce.entity.Role;
import com.org.ecommerce.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static AuthDtos.CurrentUserResponse toCurrentUser(User u) {
        Set<String> roles = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        Set<String> permissions = u.getRoles().stream().flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName).collect(Collectors.toSet());
        return new AuthDtos.CurrentUserResponse(u.getPublicId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhoneNumber(), u.isEmailVerified(), roles, permissions, u.getCreatedAt());
    }

    public static AuthDtos.SessionResponse toSession(AuthenticationSession s, UUID currentSessionId) {
        return new AuthDtos.SessionResponse(s.getPublicId(), s.getCreatedAt(), s.getLastSeenAt(),
                s.getIdleExpiresAt().isBefore(s.getAbsoluteExpiresAt()) ? s.getIdleExpiresAt() : s.getAbsoluteExpiresAt(),
                s.getIpAddress(), s.getBrowser(), s.getOperatingSystem(),
                s.getDeviceCategory() == null ? null : s.getDeviceCategory().name(),
                s.getPublicId().equals(currentSessionId));
    }
}
