package com.webblock.auth.dto;

import com.webblock.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

/** Body of PATCH /api/v1/admin/users/{id}/status  ->  { "status": "SUSPENDED" } or "ACTIVE" */
public record UpdateStatusRequest(
        @NotNull UserStatus status
) {
}
