package com.webblock.auth.dto;

/** Answer of "verify OTP": the token the next page must send back. */
public record VerificationTokenResponse(String verificationToken) {
}
