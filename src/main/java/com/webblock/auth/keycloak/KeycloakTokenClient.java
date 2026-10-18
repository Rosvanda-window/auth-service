package com.webblock.auth.keycloak;

import com.webblock.auth.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Talks to Keycloak's token endpoint FOR THE USER:
 *   login        -> email + password  => access_token + refresh_token   (grant_type=password)
 *   exchangeCode -> code from Google login => tokens                   (grant_type=authorization_code)
 *   refresh      -> refresh_token     => new tokens                      (grant_type=refresh_token)
 *   logout       -> refresh_token     => Keycloak session closed
 *
 * The client "webblock-auth-service" must have "Direct access grants" and "Standard flow" ON (Step 2).
 */
@Component
public class KeycloakTokenClient {

    private final RestClient http = RestClient.create();

    @Value("${app.keycloak.base-url}")
    private String baseUrl;

    @Value("${app.keycloak.realm}")
    private String realm;

    @Value("${app.keycloak.client-id}")
    private String clientId;

    @Value("${app.keycloak.client-secret}")
    private String clientSecret;

    private String tokenUrl() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private String logoutUrl() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }

    private String authUrl() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
    }

    // ---------------------------------------------------------------
    // Google login (Authorization Code flow)
    // ---------------------------------------------------------------

    /**
     * The URL the browser must open to start "Continue with Google".
     * kc_idp_hint=google makes Keycloak skip its own login page and go straight to Google.
     * After Google, Keycloak sends the browser back to redirectUri with ?code=...
     */
    public String googleLoginUrl(String redirectUri, String state) {
        return UriComponentsBuilder.fromUriString(authUrl())
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("kc_idp_hint", "google")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /** code (from the redirect) -> tokens. The redirectUri must be the same one used above. */
    public KeycloakTokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = baseForm("authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        try {
            return http.post()
                    .uri(tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
            throw ApiException.unauthorized("GOOGLE_LOGIN_FAILED",
                    "Google login failed or the code was already used. Please try again.");
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    private MultiValueMap<String, String> baseForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    /** Email + password -> tokens. Throws 401 INVALID_CREDENTIALS when wrong. */
    public KeycloakTokenResponse login(String email, String password) {
        MultiValueMap<String, String> form = baseForm("password");
        form.add("username", email);
        form.add("password", password);
        form.add("scope", "openid");

        try {
            return http.post()
                    .uri(tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
            // Keycloak answers 401 "invalid_grant" for a wrong email/password,
            // and 400 when the account is disabled or needs an action.
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is not correct.");
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    /** refresh_token -> new tokens. Throws 401 when the refresh token is old or wrong. */
    public KeycloakTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm("refresh_token");
        form.add("refresh_token", refreshToken);

        try {
            return http.post()
                    .uri(tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
            throw ApiException.unauthorized("REFRESH_TOKEN_INVALID", "Session expired. Please sign in again.");
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    /** Closes the Keycloak session. After this the refresh token is dead. */
    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);

        try {
            http.post()
                    .uri(logoutUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // already logged out / token already dead -> that is fine for logout
        } catch (RestClientException e) {
            throw keycloakDown(e);
        }
    }

    private static ApiException keycloakDown(RestClientException e) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "KEYCLOAK_ERROR",
                "Could not talk to Keycloak: " + e.getMessage());
    }
}
