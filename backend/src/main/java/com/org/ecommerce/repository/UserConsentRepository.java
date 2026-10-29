package com.org.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.org.ecommerce.entity.Enums;
import com.org.ecommerce.entity.UserConsent;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findFirstByUserIdAndConsentTypeOrderByRecordedAtDescIdDesc(Long userId, Enums.ConsentType type);
}
