package com.webblock.auth.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A user as Keycloak returns it from the Admin API.
 * Keycloak sends many more fields; we only keep the ones we need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean emailVerified,
        Boolean enabled
) {
    /** "Sok" + "Dara" -> "Sok Dara". Handles a missing last name. */
    public String fullName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }
}
