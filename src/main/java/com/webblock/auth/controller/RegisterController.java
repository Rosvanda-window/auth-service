package com.webblock.auth.controller;

import com.webblock.auth.dto.*;
import com.webblock.auth.service.RegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The 4 Register endpoints from the workflow diagram.
 */
@RestController
@RequestMapping("/api/v1/auth/register")
@RequiredArgsConstructor
@Tag(name = "1. Register")
public class RegisterController {

    private final RegisterService registerService;

    @PostMapping
    @Operation(summary = "Step 1 - Full name + email. Sends a 6-digit OTP to the email.")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        registerService.register(request.fullName(), request.email());
        return new MessageResponse("A verification code was sent to " + request.email().trim().toLowerCase());
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Step 2 - Check the OTP. Returns a verificationToken for the next step.")
    public VerificationTokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String token = registerService.verifyOtp(request.email(), request.otp());
        return new VerificationTokenResponse(token);
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Step 2b - Resend Code. Old code stops working.")
    public MessageResponse resendOtp(@Valid @RequestBody EmailRequest request) {
        registerService.resendOtp(request.email());
        return new MessageResponse("A new verification code was sent.");
    }

    @PostMapping("/create-password")
    @Operation(summary = "Step 3 - Set the password. Creates the account. Then go to Sign In.")
    public MessageResponse createPassword(@Valid @RequestBody CreatePasswordRequest request) {
        registerService.createPassword(request.verificationToken(), request.password(), request.confirmPassword());
        return new MessageResponse("Account created. You can now sign in.");
    }

}
