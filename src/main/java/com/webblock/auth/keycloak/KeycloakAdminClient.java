package com.webblock.auth.keycloak;

import com.webblock.auth.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All talk with the Keycloak Admin API lives here.
 * auth-service logs in to Keycloak as the client "webblock-auth-service"
 * (client_credentials) and then can create users, set passwords, and search users.
 */
@Component
public class KeycloakAdminClient {

    private final RestClient http = RestClient.create();

    @Value("${app.keycloak.base-url}")
    private String baseUrl;

    @Value("${app.keycloak.realm}")
    private String realm;

    @Value("${app.keycloak.client-id}")
    private String clientId;

    @Value("${app.keycloak.client-secret}")
    private String clientSecret;

    // The admin token is cached so we do not ask Keycloak for a new one on every call.
    private String cachedToken;
    private Instant cachedTokenExpiresAt = Instant.EPOCH;

    // ---------------------------------------------------------------
    // URLs
    // ---------------------------------------------------------------
    public String tokenUrl() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private String usersUrl() {
        return baseUrl + "/admin/realms/" + realm + "/users";
    }

    // ---------------------------------------------------------------
    // 1. Admin token (client_credentials)
    // ---------------------------------------------------------------
    private synchronized String adminToken() {
        // still good for at least 10 more seconds -> reuse it
        if (cachedToken != null && Instant.now().plusSeconds(10).isBefore(cachedTokenExpiresAt)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        KeycloakTokenResponse res = callKeycloak(() -> http.post()
                .uri(tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenResponse.class));

        cachedToken = res.accessToken();
        cachedTokenExpiresAt = Instant.now().plusSeconds(res.expiresIn());
        return cachedToken;
    }

    private String bearer() {
        return "Bearer " + adminToken();
    }

    // ---------------------------------------------------------------
    // 2. Find a user by email
    // ---------------------------------------------------------------
    public Optional<KeycloakUser> findUserByEmail(String email) {
        String normalized = email.trim().toLowerCase();

        List<KeycloakUser> users = callKeycloak(() -> http.get()
                .uri(usersUrl() + "?email={email}&exact=true", normalized)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<KeycloakUser>>() {}));

        if (users == null) return Optional.empty();
        return users.stream()
                .filter(u -> u.email() != null && u.email().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Optional<KeycloakUser> findUserById(String keycloakId) {
        try {
            KeycloakUser user = http.get()
                    .uri(usersUrl() + "/{id}", keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(KeycloakUser.class);
            return Optional.ofNullable(user);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    // ---------------------------------------------------------------
    // 3. Create a user with a password. Returns the new Keycloak id.
    //    emailVerified = true because our OTP already proved the email.
    // ---------------------------------------------------------------
    public String createUser(String fullName, String email, String password) {
        String normalized = email.trim().toLowerCase();
        String[] names = splitName(fullName);

        Map<String, Object> body = Map.of(
                "username", normalized,
                "email", normalized,
                "firstName", names[0],
                "lastName", names[1],
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false
                ))
        );

        try {
            ResponseEntity<Void> res = http.post()
                    .uri(usersUrl())
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            // Keycloak answers 201 with header  Location: .../users/<new-id>
            URI location = res.getHeaders().getLocation();
            if (location == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "KEYCLOAK_ERROR",
                        "Keycloak did not return the new user id");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);

        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED",
                    "This email is already registered");
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    // ---------------------------------------------------------------
    // 4. Set / reset the password of an existing user
    // ---------------------------------------------------------------
    public void setPassword(String keycloakId, String password) {
        Map<String, Object> body = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );

        callKeycloak(() -> http.put()
                .uri(usersUrl() + "/{id}/reset-password", keycloakId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    // ---------------------------------------------------------------
    // 5. Close ALL sessions of a user (used when an admin suspends them,
    //    so their refresh tokens stop working right away)
    // ---------------------------------------------------------------
    public void logoutAllSessions(String keycloakId) {
        callKeycloak(() -> http.post()
                .uri(usersUrl() + "/{id}/logout", keycloakId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .toBodilessEntity());
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    /** "Sok Dara Vann" -> ["Sok", "Dara Vann"];  "Sok" -> ["Sok", ""] */
    private static String[] splitName(String fullName) {
        String name = fullName == null ? "" : fullName.trim();
        int space = name.indexOf(' ');
        if (space < 0) return new String[]{name, ""};
        return new String[]{name.substring(0, space), name.substring(space + 1).trim()};
    }

    private interface KeycloakCall<T> {
        T run();
    }

    /** Runs a call and turns "Keycloak is down" into a clean 502 error. */
    private <T> T callKeycloak(KeycloakCall<T> call) {
        try {
            return call.run();
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    private static ApiException keycloakDown(RestClientException e) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "KEYCLOAK_ERROR",
                "Could not talk to Keycloak: " + e.getMessage());
    }
}
