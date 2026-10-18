package com.webblock.auth.service;

import com.webblock.auth.entity.OtpPurpose;
import com.webblock.auth.entity.OtpVerification;
import com.webblock.auth.exception.ApiException;
import com.webblock.auth.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Everything about the 6-digit OTP:
 *   sendOtp      -> make a code, save its hash, email the code
 *   verifyOtp    -> check the code, give back a verificationToken
 *   requireValidToken -> check the verificationToken (used by Create Password / Reset Password)
 *   consume      -> delete the row when the flow is finished
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpRepository;
    private final EmailService emailService;

    @Value("${app.otp.expire-minutes}")
    private int expireMinutes;

    @Value("${app.otp.max-attempts}")
    private int maxAttempts;

    @Value("${app.otp.token-expire-minutes}")
    private int tokenExpireMinutes;

    @Value("${app.otp.pepper}")
    private String pepper;

    // ---------------------------------------------------------------
    // 1. Send (also used by "Resend Code": old row is deleted first)
    // ---------------------------------------------------------------
    @Transactional
    public void sendOtp(String email, String fullName, OtpPurpose purpose) {
        email = normalize(email);

        // Resend = delete the old row, make a new one
        otpRepository.deleteByEmailAndPurpose(email, purpose);

        String otp = generateOtp();

        OtpVerification row = OtpVerification.builder()
                .email(email)
                .fullName(fullName)
                .otpHash(hash(email, otp))
                .purpose(purpose)
                .attempts(0)
                .verified(false)
                .expiresAt(LocalDateTime.now().plusMinutes(expireMinutes))
                .build();
        otpRepository.save(row);

        emailService.sendOtp(email, otp, purpose);
    }

    // ---------------------------------------------------------------
    // 2. Verify -> returns the verificationToken
    // ---------------------------------------------------------------
    @Transactional
    public String verifyOtp(String email, String otp, OtpPurpose purpose) {
        email = normalize(email);

        OtpVerification row = otpRepository.findByEmailAndPurpose(email, purpose)
                .orElseThrow(() -> ApiException.badRequest("OTP_NOT_FOUND",
                        "No code was requested for this email. Please request a new one."));

        if (row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("OTP_EXPIRED",
                    "The code has expired. Please click Resend Code.");
        }

        if (row.getAttempts() >= maxAttempts) {
            throw ApiException.badRequest("OTP_TOO_MANY_ATTEMPTS",
                    "Too many wrong tries. Please click Resend Code.");
        }

        if (!hash(email, otp).equals(row.getOtpHash())) {
            row.setAttempts(row.getAttempts() + 1);
            otpRepository.save(row);
            int left = maxAttempts - row.getAttempts();
            throw ApiException.badRequest("OTP_INVALID",
                    "Wrong code. " + left + " tries left.");
        }

        // Correct: mark verified and give a token for the next page
        String token = generateToken();
        row.setVerified(true);
        row.setVerificationToken(token);
        row.setTokenExpiresAt(LocalDateTime.now().plusMinutes(tokenExpireMinutes));
        otpRepository.save(row);

        return token;
    }

    // ---------------------------------------------------------------
    // 3. Check the verificationToken (Create Password / Reset Password)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public OtpVerification requireValidToken(String token, OtpPurpose purpose) {
        OtpVerification row = otpRepository.findByVerificationToken(token)
                .orElseThrow(() -> ApiException.unauthorized("TOKEN_INVALID",
                        "Verification token is not valid. Please verify your email again."));

        if (row.getPurpose() != purpose || !row.isVerified()) {
            throw ApiException.unauthorized("TOKEN_INVALID",
                    "Verification token is not valid. Please verify your email again.");
        }

        if (row.getTokenExpiresAt() == null || row.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.unauthorized("TOKEN_EXPIRED",
                    "Verification token has expired. Please verify your email again.");
        }

        return row;
    }

    // ---------------------------------------------------------------
    // 3b. Find the waiting-room row (used by Resend Code to keep the full name)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public Optional<OtpVerification> findPending(String email, OtpPurpose purpose) {
        return otpRepository.findByEmailAndPurpose(normalize(email), purpose);
    }

    // ---------------------------------------------------------------
    // 4. Finished -> delete the row
    // ---------------------------------------------------------------
    @Transactional
    public void consume(OtpVerification row) {
        otpRepository.delete(row);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------
    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    /** 6 digits, always with leading zeros, e.g. "048213". */
    private static String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Long random string, safe to put in a URL or JSON. */
    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 of pepper + email + otp.
     * The email is part of the hash, so the same code for two emails gives two different hashes.
     */
    private String hash(String email, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((pepper + ":" + email + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
