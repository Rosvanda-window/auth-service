package com.webblock.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Who may call what.
 *
 *   /api/v1/auth/**        -> open (register, login, forgot password, google)
 *   /swagger-ui/**, docs   -> open (dev)
 *   /api/v1/admin/**       -> needs a JWT with role ADMIN
 *   everything else        -> needs a valid JWT
 *
 * The JWT is checked against Keycloak's public key (issuer-uri in application.yaml).
 * Other microservices copy this class to protect their own endpoints.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Allow the frontend origin (see CorsConfig)
                .cors(Customizer.withDefaults())
                // No cookies / sessions: every request carries its own JWT
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                // Validate the Bearer token and read the roles from it
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
                );

        return http.build();
    }

    /**
     * Keycloak puts roles in  realm_access.roles = ["USER", "ADMIN", ...].
     * Spring wants them as "ROLE_USER", "ROLE_ADMIN". This converter does that.
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return List.of();
            Object roles = realmAccess.get("roles");
            if (!(roles instanceof Collection<?> list)) return List.of();
            return list.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        });
        return converter;
    }
}
