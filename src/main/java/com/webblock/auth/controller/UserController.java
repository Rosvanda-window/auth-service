package com.webblock.auth.controller;

import com.webblock.auth.dto.UserResponse;
import com.webblock.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints for a logged-in user (needs a JWT).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "5. Me (needs token)")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Who am I? Click Authorize (top right) and paste the accessToken first.")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        // jwt.getSubject() = the "sub" claim = the user's Keycloak id
        return userService.me(UUID.fromString(jwt.getSubject()));
    }
}
