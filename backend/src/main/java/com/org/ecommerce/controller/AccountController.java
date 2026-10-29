package com.org.ecommerce.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.dto.auth.AuthDtos;
import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.Enums.ConsentSource;
import com.org.ecommerce.entity.Enums.ConsentType;
import com.org.ecommerce.entity.Enums.RevokedReason;
import com.org.ecommerce.mapper.OrderMapper;
import com.org.ecommerce.mapper.UserMapper;
import com.org.ecommerce.security.AuthenticatedUser;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.service.AccountService;
import com.org.ecommerce.service.AuthService;
import com.org.ecommerce.service.ConsentService;
import com.org.ecommerce.service.SessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Signed-in customer's own profile, security settings, sessions and address book. */
@Tag(name = "Account")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;
    private final SessionService sessionService;
    private final ConsentService consentService;

    public AccountController(AccountService accountService, AuthService authService, SessionService sessionService,
                             ConsentService consentService) {
        this.accountService = accountService;
        this.authService = authService;
        this.sessionService = sessionService;
        this.consentService = consentService;
    }

    @PutMapping("/profile")
    public AuthDtos.CurrentUserResponse updateProfile(@Valid @RequestBody AuthDtos.UpdateProfileRequest body) {
        return UserMapper.toCurrentUser(accountService.updateProfile(CurrentUser.requireId(), body));
    }

    @Operation(summary = "Change password (signs out all other sessions)")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest body) {
        authService.changePassword(CurrentUser.require(), body.currentPassword(), body.newPassword());
    }

    @GetMapping("/sessions")
    public List<AuthDtos.SessionResponse> sessions() {
        AuthenticatedUser user = CurrentUser.require();
        return sessionService.activeSessions(user.userId()).stream()
                .map(s -> UserMapper.toSession(s, user.sessionPublicId())).toList();
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID id) {
        AuthenticatedUser user = CurrentUser.require();
        sessionService.revokeByPublicId(id, user.userId(), RevokedReason.USER_REVOKED, user.userId());
    }

    @Operation(summary = "Sign out all other devices")
    @PostMapping("/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers() {
        AuthenticatedUser user = CurrentUser.require();
        sessionService.revokeAll(user.userId(), user.sessionId(), RevokedReason.USER_REVOKED, user.userId());
    }

    @GetMapping("/addresses")
    public List<OrderDtos.AddressResponse> addresses() {
        return accountService.addresses(CurrentUser.requireId()).stream().map(OrderMapper::toAddress).toList();
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.AddressResponse addAddress(@Valid @RequestBody OrderDtos.SavedAddressRequest body) {
        return OrderMapper.toAddress(accountService.addAddress(CurrentUser.requireId(), body));
    }

    @PutMapping("/addresses/{id}")
    public OrderDtos.AddressResponse updateAddress(@PathVariable Long id, @Valid @RequestBody OrderDtos.SavedAddressRequest body) {
        return OrderMapper.toAddress(accountService.updateAddress(CurrentUser.requireId(), id, body));
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable Long id) {
        accountService.deleteAddress(CurrentUser.requireId(), id);
    }

    public record ConsentSettings(boolean analytics, boolean marketing, String policyVersion) {
    }

    @GetMapping("/consent")
    public ConsentSettings consent() {
        Long userId = CurrentUser.requireId();
        return new ConsentSettings(consentService.currentForUser(userId, ConsentType.ANALYTICS),
                consentService.currentForUser(userId, ConsentType.MARKETING), consentService.policyVersion());
    }

    @PutMapping("/consent")
    public ConsentSettings updateConsent(@RequestBody ConsentSettings body) {
        Long userId = CurrentUser.requireId();
        consentService.recordForUser(userId, ConsentType.ANALYTICS, body.analytics(), ConsentSource.ACCOUNT_SETTINGS);
        consentService.recordForUser(userId, ConsentType.MARKETING, body.marketing(), ConsentSource.ACCOUNT_SETTINGS);
        return consent();
    }
}
