package com.webblock.auth.service;

import com.webblock.auth.entity.OtpPurpose;
import com.webblock.auth.entity.OtpVerification;
import com.webblock.auth.entity.User;
import com.webblock.auth.entity.UserStatus;
import com.webblock.auth.exception.ApiException;
import com.webblock.auth.keycloak.KeycloakAdminClient;
import com.webblock.auth.keycloak.KeycloakUser;
import com.webblock.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The Register flow from the diagram:
 *   1. register        -> name + email, send OTP
 *   2. verifyOtp       -> check the code, give verificationToken
 *   3. resendOtp       -> new code
 *   4. createPassword  -> create the account in Keycloak + our users table
 */
@Service
@RequiredArgsConstructor
public class RegisterService {

    private final OtpService otpService;
    private final KeycloakAdminClient keycloak;
    private final UserRepository userRepository;

    // 1. Register page: Full Name + Email
    public void register(String fullName, String email) {
        email = normalize(email);

        // "Is Valid?" also means: not already registered
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED",
                    "This email is already registered. Please sign in.");
        }

        otpService.sendOtp(email, fullName.trim(), OtpPurpose.REGISTER);
    }

    // 2. Verify OTP page
    public String verifyOtp(String email, String otp) {
        return otpService.verifyOtp(normalize(email), otp, OtpPurpose.REGISTER);
    }

    // 2b. Resend Code
    public void resendOtp(String email) {
        email = normalize(email);

        // Keep the full name from the first request, if we still have it
        String fullName = otpService.findPending(email, OtpPurpose.REGISTER)
                .map(OtpVerification::getFullName)
                .orElseThrow(() -> ApiException.badRequest("OTP_NOT_FOUND",
                        "Please start the registration again."));

        otpService.sendOtp(email, fullName, OtpPurpose.REGISTER);
    }

    // 3. Create Password page -> Create Account
    @Transactional
    public void createPassword(String verificationToken, String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw ApiException.badRequest("PASSWORD_MISMATCH", "Passwords do not match.");
        }

        OtpVerification row = otpService.requireValidToken(verificationToken, OtpPurpose.REGISTER);
        String email = row.getEmail();
        String fullName = row.getFullName() == null ? email : row.getFullName();

        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED",
                    "This email is already registered. Please sign in.");
        }

        // If Keycloak already has this email (for example from a Google login),
        // we do NOT create a second user: we add a password to the existing one.
        Optional<KeycloakUser> existing = keycloak.findUserByEmail(email);
        String keycloakId;
        if (existing.isPresent()) {
            keycloakId = existing.get().id();
            keycloak.setPassword(keycloakId, password);
        } else {
            keycloakId = keycloak.createUser(fullName, email, password);
        }

        User user = User.builder()
                .keycloakId(UUID.fromString(keycloakId))
                .fullName(fullName)
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        // Finished: the waiting-room row is no longer needed
        otpService.consume(row);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
