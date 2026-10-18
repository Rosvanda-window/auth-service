package com.webblock.auth.controller;

import com.webblock.auth.dto.GoogleCodeRequest;
import com.webblock.auth.dto.TokenResponse;
import com.webblock.auth.keycloak.KeycloakTokenClient;
import com.webblock.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * "Continue with Google".
 *
 *   1. GET  /api/v1/auth/google/url   -> gives the frontend the Keycloak/Google URL to open
 *   2. the browser goes to Google, then Keycloak sends it back to the FRONTEND page
 *      (app.google.redirect-uri, e.g. http://localhost:3000/auth/google/callback?code=...)
 *   3. the frontend POSTs the code here -> we swap it for tokens
 *      POST /api/v1/auth/google/callback  { "code": "..." }
 */
@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
@Tag(name = "4. Continue with Google")
public class GoogleAuthController {

    private final KeycloakTokenClient tokenClient;
    private final AuthService authService;

    @Value("${app.google.redirect-uri}")
    private String redirectUri;

    @GetMapping("/url")
    @Operation(summary = "Step 1 - Get the URL the browser must open to start Google login.")
    public Map<String, String> url() {
        String state = UUID.randomUUID().toString();
        return Map.of(
                "url", tokenClient.googleLoginUrl(redirectUri, state),
                "state", state
        );
    }

    @PostMapping("/callback")
    @Operation(summary = "Step 2 - Send the code from the redirect. Returns the tokens.")
    public TokenResponse callback(@Valid @RequestBody GoogleCodeRequest request) {
        return authService.loginWithGoogleCode(request.code(), redirectUri);
    }
}
