package com.webblock.auth.controller;

import com.webblock.auth.dto.LoginRequest;
import com.webblock.auth.dto.MessageResponse;
import com.webblock.auth.dto.RefreshRequest;
import com.webblock.auth.dto.TokenResponse;
import com.webblock.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign In, Refresh, Logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "2. Sign In")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Email + password. Returns accessToken, refreshToken and roles (for Is Admin?).")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get new tokens with the refreshToken, before the accessToken expires.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Close the session. The refreshToken stops working.")
    public MessageResponse logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return new MessageResponse("Logged out.");
    }
}
