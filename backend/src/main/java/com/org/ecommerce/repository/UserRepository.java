package com.org.ecommerce.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.org.ecommerce.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesById(Long id);

    Optional<User> findByPublicId(UUID publicId);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where (:q is null or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.firstName) like lower(concat('%', :q, '%'))
                   or lower(u.lastName) like lower(concat('%', :q, '%')))
              and (:enabled is null or u.enabled = :enabled)
              and (:role is null or exists (select 1 from u.roles r where r.name = :role))
            """)
    Page<User> search(@Param("q") String q, @Param("enabled") Boolean enabled, @Param("role") String role,
                      Pageable pageable);

    @Query("select count(u) from User u join u.roles r where r.name = :role")
    long countByRole(@Param("role") String role);

    @Query("""
            select count(u) from User u join u.roles r
            where r.name = :role and u.createdAt >= :from and u.createdAt < :to
            """)
    long countByRoleCreatedBetween(@Param("role") String role, @Param("from") java.time.Instant from,
                                   @Param("to") java.time.Instant to);
}
