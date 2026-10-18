# How another WebBlock microservice uses the auth-service

Short version: **you never call auth-service to check a token.** Your service checks the JWT
by itself using Keycloak's public key. auth-service only *issues* tokens (login, register...).

## 1. Run the shared infra

```
cd webblock-infra
docker compose up -d
```

This gives you Keycloak (8080), PostgreSQL (15432), Mailpit (8025).
Add your own database to `postgres/init.sql`, e.g. `CREATE DATABASE webblock_project;`
(then `docker compose down -v && docker compose up -d` once).

## 2. Add 2 dependencies to your `build.gradle`

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

## 3. Add to your `application.yaml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/webblock
```

## 4. Copy `SecurityConfig.java` from auth-service

Package `com.webblock.auth.config.SecurityConfig` -> change the package name to yours and
change the `permitAll()` paths to the ones your service wants open (usually only swagger + health).
Keep the `jwtConverter()` part as it is; it turns Keycloak roles into `ROLE_USER` / `ROLE_ADMIN`.

Also copy `OpenApiConfig.java` so your Swagger gets the **Authorize** button.

## 5. Read the user in a controller

```java
@GetMapping("/projects")
public List<Project> myProjects(@AuthenticationPrincipal Jwt jwt) {
    UUID keycloakId = UUID.fromString(jwt.getSubject());   // the user's id (same as users.keycloak_id)
    String email    = jwt.getClaimAsString("email");
    ...
}
```

Store `keycloakId` as the owner of your rows. It is the same value as `keycloak_id`
in the auth-service `users` table, so everyone agrees on who the user is.

Need admin-only? Either put the path under `/api/v1/admin/**` in your SecurityConfig,
or use `@PreAuthorize("hasRole('ADMIN')")` on the method (add `@EnableMethodSecurity` on the config class).

## 6. Test in Swagger

1. In **auth-service** Swagger (`localhost:8081/swagger-ui.html`): `POST /api/v1/auth/login`
   -> copy `accessToken`.
2. In **your** Swagger: click **Authorize** (top right) -> paste the token -> Authorize.
3. Call your endpoint. Without the token you get 401; with it 200.

The accessToken lives 5 minutes. When it expires, call `POST /api/v1/auth/refresh` on auth-service
with the `refreshToken` to get a new pair.

## Endpoints the frontend will use (all on auth-service, port 8081)

| Flow | Method + path |
|---|---|
| Register | POST `/api/v1/auth/register`, `/register/verify-otp`, `/register/resend-otp`, `/register/create-password` |
| Sign In | POST `/api/v1/auth/login`, `/refresh`, `/logout` |
| Google | GET `/api/v1/auth/google` (redirect), GET `/api/v1/auth/google/callback?code=` |
| Forgot password | POST `/api/v1/auth/forgot-password`, `/forgot-password/verify-otp`, `/forgot-password/resend-otp`, `/reset-password` |
| Me | GET `/api/v1/users/me` (JWT) |
| Admin | GET `/api/v1/admin/users`, PATCH `/api/v1/admin/users/{id}/status` (JWT with ADMIN) |

Errors always look like `{ "code": "OTP_EXPIRED", "message": "...", "timestamp": "..." }`.
