package com.webblock.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of "create password" (register) and "reset password" (forgot password). */
public record CreatePasswordRequest(
        @NotBlank String verificationToken,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
        @NotBlank String confirmPassword
) {
}
