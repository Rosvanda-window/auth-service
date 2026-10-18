package com.webblock.auth.dto;

import java.util.List;

/**
 * What the frontend gets after Sign In / Refresh.
 * "roles" is also inside the accessToken; we repeat it here so the
 * frontend can do "Is Admin?" without decoding the JWT.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        String tokenType,
        String email,
        String fullName,
        List<String> roles
) {
}
