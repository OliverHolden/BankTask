# Eagle Bank API — Claude Code Instructions

## Running the application

```bash
mvn spring-boot:run   # start on http://localhost:8080
mvn test              # run tests
```

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html` once running.

## Package structure

All code lives under `com.OliverHolden.BankApplication`:

```
├── configuration/    # Spring beans and security config
├── controller/       # REST controllers
├── dto/
│   ├── request/
│   └── response/
├── exception/        # Custom exceptions and global handler
├── model/            # JPA entities
├── repository/       # Spring Data repositories
├── security/         # JWT filter and token provider
├── service/          # Business logic
└── utility/          # Shared helpers
```

## Logging

Every service and controller must declare a logger via Lombok:

```java
@Slf4j
public class UserService { ... }
```

- `INFO` on entry to every public service method, including key identifiers
- `WARN` for 403 and 404 outcomes
- `ERROR` (with the exception) before re-throwing inside catch blocks

## Swagger / OpenAPI annotations

Every controller class must have `@Tag`. Every endpoint method must have `@Operation` and an `@ApiResponse` for each status code it can return.

```java
@Tag(name = "user", description = "Manage a user")
@RestController
public class UserController {

    @Operation(summary = "Fetch user by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User details"),
        @ApiResponse(responseCode = "401", description = "Unauthorised"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(...) { ... }
}
```

## Code conventions

**Lombok** — use throughout:
- `@Slf4j` on every service and controller
- `@RequiredArgsConstructor` for constructor injection (never `@Autowired` on fields)
- `@Data` or `@Value` on DTOs
- `@Builder` on response DTOs
- `@Entity` classes: `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor`

**Validation** — annotate controller request parameters with `@Valid`; use `@NotBlank`, `@NotNull`, `@Email`, `@Pattern` on DTO fields as appropriate.

**Constructor injection** — always inject dependencies via constructor (`@RequiredArgsConstructor`), not field injection.

## Authentication

### Configuration

JWT secret and expiry must be externalised in `application.properties` and injected via `@Value` — never hardcoded:

```properties
jwt.secret=your-secret-key-min-32-chars
jwt.expiration-ms=3600000
```

### Classes

- **`JwtTokenProvider`** (`security/`) — generates a signed JWT on login (`sub` = userId), validates tokens, and extracts the userId from the `sub` claim. Explicitly reject expired tokens and invalid signatures with `401`.
- **`JwtAuthFilter`** extends `OncePerRequestFilter` (`security/`) — extracts the `Authorization: Bearer <token>` header, calls `JwtTokenProvider` to validate, then populates `SecurityContextHolder`. Does nothing and passes through if no token is present (let Spring Security return 401).
- **`CustomUserPrincipal`** implements `UserDetails` (`security/`) — wraps the `User` entity; must expose `getId()` so controllers can pass the authenticated userId to services.
- **`CustomUserDetailsService`** implements `UserDetailsService` (`security/`) — loads a user by email for Spring Security's login flow.
- **`SecurityConfig`** (`configuration/`) — stateless sessions, CSRF disabled, permit `POST /v1/users` and `POST /v1/auth/login`, require authentication for all other endpoints; register `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`.

### Using the authenticated user in controllers

Always inject via `@AuthenticationPrincipal` — never call `SecurityContextHolder` inside a service:

```java
@GetMapping("/{userId}")
public ResponseEntity<UserResponse> getUser(
        @PathVariable String userId,
        @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(userService.getUser(userId, principal.getId()));
}
```

## Testing

- **Unit tests** — JUnit 5 (Jupiter) + Mockito; mock repositories and cover happy path and each error branch per service method
- **Integration tests** — `@SpringBootTest` + H2 + `MockMvc`; exercise the full HTTP stack including request serialisation and response shape
- **Security tests** — assert `401` for every protected endpoint with no token; assert `403` for cross-user access attempts
