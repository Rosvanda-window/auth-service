package com.webblock.auth.controller;

import com.webblock.auth.dto.CreatePasswordRequest;
import com.webblock.auth.dto.EmailRequest;
import com.webblock.auth.dto.MessageResponse;
import com.webblock.auth.dto.VerificationTokenResponse;
import com.webblock.auth.dto.VerifyOtpRequest;
import com.webblock.auth.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forgot Password / Reset Password endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "3. Forgot Password")
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/forgot-password")
    @Operation(summary = "Step 1 - Email. Sends a 6-digit OTP if the account exists (answer is the same either way).")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        passwordService.forgotPassword(request.email());
        return new MessageResponse("If this email is registered, a verification code was sent.");
    }

    @PostMapping("/forgot-password/verify-otp")
    @Operation(summary = "Step 2 - Check the OTP. Returns a verificationToken for Reset Password.")
    public VerificationTokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String token = passwordService.verifyOtp(request.email(), request.otp());
        return new VerificationTokenResponse(token);
    }

    @PostMapping("/forgot-password/resend-otp")
    @Operation(summary = "Step 2b - Resend Code.")
    public MessageResponse resendOtp(@Valid @RequestBody EmailRequest request) {
        passwordService.resendOtp(request.email());
        return new MessageResponse("If this email is registered, a new verification code was sent.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Step 3 - New password + confirm. Then go to Sign In.")
    public MessageResponse resetPassword(@Valid @RequestBody CreatePasswordRequest request) {
        passwordService.resetPassword(request.verificationToken(), request.password(), request.confirmPassword());
        return new MessageResponse("Password updated. You can now sign in.");
    }
}
