package com.webblock.auth.service;

import com.webblock.auth.entity.OtpPurpose;
import com.webblock.auth.entity.OtpVerification;
import com.webblock.auth.entity.User;
import com.webblock.auth.exception.ApiException;
import com.webblock.auth.keycloak.KeycloakAdminClient;
import com.webblock.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot Password / Reset Password from the diagram.
 *   1. forgotPassword -> email, send OTP (purpose RESET_PASSWORD)
 *   2. verifyOtp      -> check the code, give verificationToken
 *   3. resendOtp      -> new code
 *   4. resetPassword  -> set the new password in Keycloak
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final OtpService otpService;
    private final KeycloakAdminClient keycloak;
    private final UserRepository userRepository;

    // 1. Forgot Password page: Input Email
    public void forgotPassword(String email) {
        email = normalize(email);

        // Only send if the account exists. But we NEVER tell the caller if it exists or not,
        // so nobody can use this page to find out which emails are registered.
        if (userRepository.existsByEmail(email)) {
            otpService.sendOtp(email, null, OtpPurpose.RESET_PASSWORD);
        }
    }

    // 2. Verify OTP page
    public String verifyOtp(String email, String otp) {
        return otpService.verifyOtp(normalize(email), otp, OtpPurpose.RESET_PASSWORD);
    }

    // 2b. Resend Code (same rule as step 1)
    public void resendOtp(String email) {
        forgotPassword(email);
    }

    // 3. Reset Password page
    @Transactional
    public void resetPassword(String verificationToken, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw ApiException.badRequest("PASSWORD_MISMATCH", "Passwords do not match.");
        }

        OtpVerification row = otpService.requireValidToken(verificationToken, OtpPurpose.RESET_PASSWORD);

        User user = userRepository.findByEmail(row.getEmail())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found."));

        keycloak.setPassword(user.getKeycloakId().toString(), newPassword);

        otpService.consume(row);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
