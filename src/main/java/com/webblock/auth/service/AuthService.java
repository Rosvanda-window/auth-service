package com.webblock.auth.service;

import com.webblock.auth.dto.TokenResponse;
import com.webblock.auth.entity.User;
import com.webblock.auth.entity.UserStatus;
import com.webblock.auth.exception.ApiException;
import com.webblock.auth.keycloak.JwtUtil;
import com.webblock.auth.keycloak.KeycloakAdminClient;
import com.webblock.auth.keycloak.KeycloakTokenClient;
import com.webblock.auth.keycloak.KeycloakTokenResponse;
import com.webblock.auth.keycloak.KeycloakUser;
import com.webblock.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sign In / Refresh / Logout from the diagram.
 *
 * Order for Sign In:
 *   1. Keycloak checks email + password and gives tokens
 *   2. we find (or create) the row in our users table
 *   3. SUSPENDED -> close the Keycloak session again and refuse
 *   4. save last_login_at and return the tokens + roles
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakTokenClient tokenClient;
    private final KeycloakAdminClient adminClient;
    private final UserRepository userRepository;

    @Transactional
    public TokenResponse login(String email, String password) {
        KeycloakTokenResponse tokens = tokenClient.login(email.trim().toLowerCase(), password);
        return afterTokens(tokens, true);
    }

    /** "Continue with Google": the browser came back with a code; swap it for tokens. */
    @Transactional
    public TokenResponse loginWithGoogleCode(String code, String redirectUri) {
        KeycloakTokenResponse tokens = tokenClient.exchangeCode(code, redirectUri);
        return afterTokens(tokens, true);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        KeycloakTokenResponse tokens = tokenClient.refresh(refreshToken);
        return afterTokens(tokens, false);
    }

    public void logout(String refreshToken) {
        tokenClient.logout(refreshToken);
    }

    // ---------------------------------------------------------------

    private TokenResponse afterTokens(KeycloakTokenResponse tokens, boolean isLogin) {
        UUID keycloakId = UUID.fromString(JwtUtil.subject(tokens.accessToken()));
        List<String> roles = JwtUtil.realmRoles(tokens.accessToken());

        // Find our row, or create it (first login of a user made in Keycloak console / Google)
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> createRowFromKeycloak(keycloakId));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            tokenClient.logout(tokens.refreshToken());   // do not leave a live session behind
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "Your account is suspended. Please contact support.");
        }

        if (isLogin) {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        }

        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.expiresIn(),
                tokens.refreshExpiresIn(),
                tokens.tokenType(),
                user.getEmail(),
                user.getFullName(),
                roles
        );
    }

    private User createRowFromKeycloak(UUID keycloakId) {
        KeycloakUser kc = adminClient.findUserById(keycloakId.toString())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS",
                        "Email or password is not correct."));

        String fullName = kc.fullName().isBlank() ? kc.email() : kc.fullName();

        User user = User.builder()
                .keycloakId(keycloakId)
                .fullName(fullName)
                .email(kc.email().toLowerCase())
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }
}
