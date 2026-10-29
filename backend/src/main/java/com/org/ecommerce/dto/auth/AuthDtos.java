package com.org.ecommerce.dto.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.org.ecommerce.validation.StrongPassword;

/** Request/response records for authentication and account security endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @StrongPassword String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            boolean analyticsConsent,
            boolean marketingConsent) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @StrongPassword String newPassword) {
    }

    public record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @StrongPassword String newPassword) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 32) @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "must be a valid phone number") String phoneNumber) {
    }

    public record CurrentUserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            boolean emailVerified,
            Set<String> roles,
            Set<String> permissions,
            Instant createdAt) {
    }

    public record SessionResponse(
            UUID id,
            Instant createdAt,
            Instant lastSeenAt,
            Instant expiresAt,
            String ipAddress,
            String browser,
            String operatingSystem,
            String deviceCategory,
            boolean current) {
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
