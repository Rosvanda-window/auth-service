package com.webblock.auth.dto;

import com.webblock.auth.entity.User;
import com.webblock.auth.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** A user as the API shows it (never the entity itself). */
public record UserResponse(
        UUID id,
        UUID keycloakId,
        String fullName,
        String email,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getKeycloakId(), u.getFullName(), u.getEmail(),
                u.getStatus(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
