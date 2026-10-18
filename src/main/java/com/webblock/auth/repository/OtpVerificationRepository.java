package com.webblock.auth.repository;

import com.webblock.auth.entity.OtpPurpose;
import com.webblock.auth.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the "otp_verification" table.
 */
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findByEmailAndPurpose(String email, OtpPurpose purpose);

    Optional<OtpVerification> findByVerificationToken(String verificationToken);

    void deleteByEmailAndPurpose(String email, OtpPurpose purpose);
}
