package com.webblock.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/login */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
