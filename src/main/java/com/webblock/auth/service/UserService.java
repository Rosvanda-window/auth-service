package com.webblock.auth.service;

import com.webblock.auth.dto.UserResponse;
import com.webblock.auth.entity.User;
import com.webblock.auth.entity.UserStatus;
import com.webblock.auth.exception.ApiException;
import com.webblock.auth.keycloak.KeycloakAdminClient;
import com.webblock.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * "Who am I" for logged-in users, and the Admin Portal actions.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloak;

    /** GET /users/me : the row of the user whose JWT was sent */
    @Transactional(readOnly = true)
    public UserResponse me(UUID keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found."));
        return UserResponse.from(user);
    }

    /** Admin: all users */
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    /** Admin: ACTIVE <-> SUSPENDED */
    @Transactional
    public UserResponse updateStatus(UUID userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found."));

        user.setStatus(status);
        userRepository.save(user);

        // Suspended -> kill their live sessions in Keycloak, so /refresh stops working now,
        // not only after the next login.
        if (status == UserStatus.SUSPENDED) {
            keycloak.logoutAllSessions(user.getKeycloakId().toString());
        }

        return UserResponse.from(user);
    }
}
