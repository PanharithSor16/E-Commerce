package com.org.ecommerce.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.AuthenticationSession;
import com.org.ecommerce.entity.Enums;

public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession, Long> {

    /** Loads the session with its user, roles and permissions in one round trip for request authentication. */
    @Query("""
            select s from AuthenticationSession s
            join fetch s.user u
            left join fetch u.roles r
            left join fetch r.permissions
            where s.tokenHash = :hash
            """)
    Optional<AuthenticationSession> findForAuthentication(@Param("hash") String tokenHash);

    Optional<AuthenticationSession> findByPublicId(UUID publicId);

    @Query("""
            select s from AuthenticationSession s join fetch s.user
            where s.user.id = :userId and s.revokedAt is null
              and s.idleExpiresAt > :now and s.absoluteExpiresAt > :now
            order by s.lastSeenAt desc
            """)
    List<AuthenticationSession> findActiveByUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Query(value = """
            select s from AuthenticationSession s join fetch s.user
            where s.revokedAt is null and s.idleExpiresAt > :now and s.absoluteExpiresAt > :now
            """,
            countQuery = """
            select count(s) from AuthenticationSession s
            where s.revokedAt is null and s.idleExpiresAt > :now and s.absoluteExpiresAt > :now
            """)
    Page<AuthenticationSession> findAllActive(@Param("now") Instant now, Pageable pageable);

    @Query("""
            select count(s) from AuthenticationSession s
            where s.revokedAt is null and s.idleExpiresAt > :now and s.absoluteExpiresAt > :now
            """)
    long countActive(@Param("now") Instant now);

    @Modifying
    @Query("""
            update AuthenticationSession s
            set s.revokedAt = :now, s.revokedReason = :reason, s.revokedByUserId = :by
            where s.user.id = :userId and s.revokedAt is null and (:exceptId is null or s.id <> :exceptId)
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("exceptId") Long exceptSessionId,
                         @Param("reason") Enums.RevokedReason reason, @Param("by") Long revokedBy,
                         @Param("now") Instant now);
}
