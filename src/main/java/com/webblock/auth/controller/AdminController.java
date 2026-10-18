package com.webblock.auth.controller;

import com.webblock.auth.dto.UpdateStatusRequest;
import com.webblock.auth.dto.UserResponse;
import com.webblock.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin Portal endpoints. SecurityConfig lets only role ADMIN in here.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "6. Admin (needs ADMIN token)")
public class AdminController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all users")
    public List<UserResponse> list() {
        return userService.listAll();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend or activate a user. Body: { \"status\": \"SUSPENDED\" } or \"ACTIVE\"")
    public UserResponse updateStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(id, request.status());
    }
}
