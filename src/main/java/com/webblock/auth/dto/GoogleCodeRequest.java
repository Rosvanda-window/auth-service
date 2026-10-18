package com.webblock.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/google/callback : the code Keycloak gave the browser. */
public record GoogleCodeRequest(
        @NotBlank String code
) {
}
