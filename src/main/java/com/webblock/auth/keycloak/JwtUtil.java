package com.webblock.auth.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reads the middle part (payload) of a JWT WITHOUT checking the signature.
 * That is fine here: the token just came from Keycloak over our own call,
 * so we trust it. Other services must verify the signature (Step 10).
 */
public final class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtUtil() {
    }

    /** All claims of the token as a map. */
    public static Map<String, Object> payload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readValue(new String(json, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid JWT", e);
        }
    }

    /** The "sub" claim = the user's Keycloak id. */
    public static String subject(String jwt) {
        Object sub = payload(jwt).get("sub");
        return sub == null ? null : sub.toString();
    }

    /** realm_access.roles -> ["USER"] or ["ADMIN", "USER"] ... */
    @SuppressWarnings("unchecked")
    public static List<String> realmRoles(String jwt) {
        Object realmAccess = payload(jwt).get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> list) {
                return (List<String>) list;
            }
        }
        return Collections.emptyList();
    }
}
