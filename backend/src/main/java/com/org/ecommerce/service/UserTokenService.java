package com.org.ecommerce.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.entity.Enums.TokenType;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.entity.UserToken;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.UserTokenRepository;
import com.org.ecommerce.security.TokenHasher;

/** Issues and redeems single-use email tokens (only hashes are stored). */
@Service
public class UserTokenService {

    private final UserTokenRepository tokens;
    private final TokenHasher hasher;
    private final Clock clock;

    public UserTokenService(UserTokenRepository tokens, TokenHasher hasher, Clock clock) {
        this.tokens = tokens;
        this.hasher = hasher;
        this.clock = clock;
    }

    /** Issues a new token and invalidates older unused tokens of the same type. Returns the raw token. */
    @Transactional
    public String issue(User user, TokenType type, Duration ttl) {
        Instant now = clock.instant();
        tokens.consumeAllForUser(user.getId(), type, now);
        String raw = hasher.newToken();
        UserToken token = new UserToken();
        token.setUser(user);
        token.setTokenType(type);
        token.setTokenHash(hasher.sha256(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        tokens.save(token);
        return raw;
    }

    /** Marks the token consumed and returns its user; fails with a generic message if invalid. */
    @Transactional
    public User redeem(String rawToken, TokenType type) {
        Instant now = clock.instant();
        UserToken token = tokens.findByTokenHash(hasher.sha256(rawToken))
                .filter(t -> t.getTokenType() == type && t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "This link is invalid or has expired."));
        token.setConsumedAt(now);
        return token.getUser();
    }
}
