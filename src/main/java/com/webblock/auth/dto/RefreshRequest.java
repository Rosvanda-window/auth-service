package com.webblock.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/refresh and /api/v1/auth/logout */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
