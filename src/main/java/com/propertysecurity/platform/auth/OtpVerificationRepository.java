package com.propertysecurity.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findFirstByPhoneNumberAndConsumedAtIsNullOrderByCreatedAtDesc(String phoneNumber);
}
