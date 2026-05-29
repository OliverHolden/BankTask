# Eagle Bank API — Claude Code Instructions

## Running the application

```bash
mvn spring-boot:run   # start on http://localhost:8080
mvn test              # run tests
```

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html` when running with the `dev` profile (disabled by default).

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
- **Never log PII** — email addresses, passwords, phone numbers, and address fields must never appear in log output

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
- `@Entity` classes: `@Getter` + `@Setter` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` + `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` on the `id` field — never `@Data` on entities

**Validation** — annotate controller request parameters with `@Valid`; use `@NotBlank`, `@NotNull`, `@Email`, `@Pattern` on DTO fields as appropriate.

**Constructor injection** — always inject dependencies via constructor (`@RequiredArgsConstructor`), not field injection.

**Transactions** — annotate all write service methods (`create`, `update`, `delete`) with `@Transactional`. This makes the check-then-act pattern (e.g. `existsByEmail` + `save`) atomic and ensures concurrent requests can't race through a guard. Always catch `DataIntegrityViolationException` inside a `@Transactional` method and re-throw as a domain exception (e.g. `ConflictException`) so the constraint violation surfaces as the correct HTTP status rather than a 500.

## Admin and debug consoles

Admin and debug consoles (H2 console, Swagger UI, Spring Boot Actuator, etc.) must be **disabled by default in `application.properties`** and enabled only via an explicit opt-in in `application-dev.properties`. The same principle applies to the security permit: any `permitAll()` rule for a console path must be gated on the same property so that it cannot be reached in an environment where the console is off.

```properties
# application.properties — off by default
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
spring.h2.console.enabled=false

# application-dev.properties — opt in for local dev
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
spring.h2.console.enabled=true
```

In `SecurityConfig`, read the flag with `@Value` and only open the route when the property is true:

```java
@Value("${spring.h2.console.enabled:false}")
private boolean h2ConsoleEnabled;

// inside authorizeHttpRequests lambda:
if (h2ConsoleEnabled) {
    auth.requestMatchers("/h2-console/**").permitAll();
}
```

Never add an unconditional `permitAll()` for any admin or tooling endpoint.

## Authentication

### Configuration

JWT secret is declared in `application-dev.properties` for local dev and must be supplied via environment variable in production — never hardcoded. Expiry lives in `application.properties` as it is not sensitive:

```properties
# application-dev.properties (local only)
jwt.secret=your-secret-key-min-32-chars

# application.properties (committed)
jwt.expiration-ms=3600000
```

### Classes

- **`JwtTokenProvider`** (`security/`) — generates a signed JWT on login (`sub` = userId), validates tokens, and extracts the userId from the `sub` claim. Explicitly reject expired tokens and invalid signatures with `401`.
- **`JwtAuthFilter`** extends `OncePerRequestFilter` (`security/`) — extracts the `Authorization: Bearer <token>` header, calls `JwtTokenProvider` to validate, then uses `CustomUserDetailsService.loadUserById` to populate `SecurityContextHolder`. Passes through with no auth set if no token is present (Spring Security returns 401). Explicitly returns `401` if the token is valid but the user no longer exists.
- **`CustomUserPrincipal`** implements `UserDetails` (`security/`) — wraps the `User` entity; must expose `getId()` so controllers can pass the authenticated userId to services.
- **`CustomUserDetailsService`** implements `UserDetailsService` (`security/`) — `loadUserByUsername(email)` is used by Spring Security's login flow; `loadUserById(userId)` is used by `JwtAuthFilter` to resolve the authenticated user on each request.
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
