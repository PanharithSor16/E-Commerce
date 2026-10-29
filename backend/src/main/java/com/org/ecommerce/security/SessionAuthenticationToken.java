package com.org.ecommerce.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class SessionAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;

    public SessionAuthenticationToken(AuthenticatedUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }
}
