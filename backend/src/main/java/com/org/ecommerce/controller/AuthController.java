package com.org.ecommerce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.analytics.AnalyticsContext;
import com.org.ecommerce.dto.auth.AuthDtos;
import com.org.ecommerce.mapper.UserMapper;
import com.org.ecommerce.repository.UserRepository;
import com.org.ecommerce.security.AuthenticatedUser;
import com.org.ecommerce.security.ClientInfo;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.security.SessionCookies;
import com.org.ecommerce.service.AuthService;
import com.org.ecommerce.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CartService cartService;
    private final SessionCookies cookies;
    private final UserRepository users;

    public AuthController(AuthService authService, CartService cartService, SessionCookies cookies, UserRepository users) {
        this.authService = authService;
        this.cartService = cartService;
        this.cookies = cookies;
        this.users = users;
    }

    @Operation(summary = "Issue the CSRF token (also set as the XSRF-TOKEN cookie)")
    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) {
        return new AuthDtos.CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @Operation(summary = "Register a customer account and sign in")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.CurrentUserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                                 HttpServletRequest http, HttpServletResponse response) {
        AuthService.LoginResult result = authService.register(request, ClientInfo.from(http), AnalyticsContext.from(http));
        completeLogin(result, http, response);
        return UserMapper.toCurrentUser(result.user());
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public AuthDtos.CurrentUserResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                              HttpServletRequest http, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request, ClientInfo.from(http), AnalyticsContext.from(http));
        completeLogin(result, http, response);
        return UserMapper.toCurrentUser(result.user());
    }

    @Operation(summary = "Sign out the current session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        CurrentUser.get().ifPresent(user -> authService.logout(user, AnalyticsContext.from(http)));
        cookies.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current authenticated user")
    @GetMapping("/me")
    public AuthDtos.CurrentUserResponse me() {
        AuthenticatedUser principal = CurrentUser.require();
        return users.findWithRolesById(principal.userId()).map(UserMapper::toCurrentUser)
                .orElseThrow(() -> com.org.ecommerce.exception.ApiException.unauthorized("Authentication required."));
    }

    @Operation(summary = "Request a password reset email (always 202)")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
    }

    @Operation(summary = "Set a new password with a reset token")
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
    }

    @Operation(summary = "Confirm an email address")
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
    }

    @Operation(summary = "Send a new verification email")
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification() {
        authService.resendVerification(CurrentUser.requireId());
    }

    private void completeLogin(AuthService.LoginResult result, HttpServletRequest http, HttpServletResponse response) {
        cookies.writeSession(response, result.session().rawToken(), result.session().cookieMaxAge());
        // Move anything the shopper added while anonymous into their account cart.
        cookies.readCart(http).ifPresent(token -> {
            cartService.mergeAnonymousCart(token, result.user().getId());
            cookies.clearCart(response);
        });
    }
}
