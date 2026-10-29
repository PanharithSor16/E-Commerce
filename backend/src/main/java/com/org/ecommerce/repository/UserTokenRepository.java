package com.org.ecommerce.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.Enums;
import com.org.ecommerce.entity.UserToken;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<UserToken> findByTokenHash(String tokenHash);

    /** Invalidates outstanding tokens of a type, e.g. when a new reset link is requested. */
    @Modifying
    @Query("""
            update UserToken t set t.consumedAt = :now
            where t.user.id = :userId and t.tokenType = :type and t.consumedAt is null
            """)
    int consumeAllForUser(@Param("userId") Long userId, @Param("type") Enums.TokenType type,
                          @Param("now") Instant now);
}
