package com.webblock.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of "resend OTP" and "forgot password": only an email. */
public record EmailRequest(
        @NotBlank @Email String email
) {
}
