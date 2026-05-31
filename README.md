# Eagle Bank API

A RESTful banking API built with Spring Boot for the Barclays take-home coding test. It supports user management, bank accounts, and deposit/withdrawal transactions, secured with JWT bearer token authentication.

## Tech stack

- Java 21
- Spring Boot 4.0.6 (Web MVC)
- Spring Security + JWT
- Spring Data JPA (H2 in-memory database)
- Spring Boot Actuator (dev health and metrics endpoints)
- Lombok
- Maven
- springdoc-openapi (Swagger UI)

## Running the application

**Prerequisites:** Java 21 and Maven installed.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The dev profile must be active — it supplies `jwt.secret` which is absent from the base `application.properties`. The API will start on `http://localhost:8080`; internal tooling is exposed only on `http://localhost:8081`.

### Run with Docker

```bash
docker build -t eagle-bank .
docker run -p 8080:8080 -p 8081:8081 -e SPRING_PROFILES_ACTIVE=dev eagle-bank
```

Swagger UI will be available at `http://localhost:8081/swagger-ui/index.html` once the container is running.
The dev profile also exposes H2 Console at `http://localhost:8081/h2-console/` and Actuator endpoints at `http://localhost:8081/actuator/health`, `http://localhost:8081/actuator/info`, and `http://localhost:8081/actuator/metrics`.

### Run tests

```bash
mvn test
```

## API overview

All protected endpoints require an `Authorization: Bearer <token>` header. Obtain a token via `POST /v1/auth/login`.

Full endpoint documentation is available via Swagger UI at `http://localhost:8081/swagger-ui/index.html` once the application is running with the dev profile.

## Project structure

```
src/main/java/com/OliverHolden/BankApplication/
├── configuration/       # Spring beans and security config
├── controller/          # REST controllers
├── dto/                 # Request and response objects
├── exception/           # Custom exceptions and global handler
├── model/               # JPA entities
├── repository/          # Spring Data repositories
├── security/            # JWT filter and token provider
├── service/             # Business logic
└── utility/             # Shared helpers
```

## Authentication flow

```bash
# 1. Create a user
curl -X POST http://localhost:8080/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"Secret123!","phoneNumber":"+447911000001","address":{"line1":"1 High St","town":"London","county":"Greater London","postcode":"SW1A 1AA"}}'

# 2. Log in to obtain a JWT
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Secret123!"}'
# → {"token":"<jwt>","tokenType":"Bearer","expiresAt":"..."}

# 3. Use the token on protected endpoints
curl http://localhost:8080/v1/accounts \
  -H "Authorization: Bearer <jwt>"
```

## Testing

```bash
mvn test
```

Tests are structured in three layers:

| Layer | Framework | Coverage |
|-------|-----------|----------|
| Unit | JUnit 5 + Mockito | Service methods: happy path and every error branch |
| Integration | `@SpringBootTest` + H2 + `MockMvc` | Full HTTP stack including request serialisation and response shape |
| Security | Integration tests | `401` for every protected endpoint with no token; `403` for cross-user access |

## Conventions

Code conventions, logging rules, Swagger annotation requirements, and Lombok usage are documented in [CLAUDE.md](CLAUDE.md).

## Assumptions

- **H2 in-memory database** — data does not persist across restarts. Swap the datasource configuration for PostgreSQL or MySQL for persistence.
- **Single currency, single sort code** — all accounts are GBP-denominated with sort code `10-10-10`. The spec defines both as single-value enums; multi-currency and multi-sort-code support would require schema changes.
- **Account number format** — `01XXXXXX` (01 + 6 random digits, 1,000,000 address space). Uniqueness is guaranteed by a retry loop; a DB sequence would be the production approach.
- **Monetary precision** — amounts are limited to two decimal places (`@Digits(integer = 5, fraction = 2)`), matching the spec's "up to two decimal places". A value with a third decimal is rejected with `400` rather than being silently rounded at the persistence boundary.
- **Transaction ordering** — `GET /v1/accounts/{accountNumber}/transactions` returns transactions newest-first (by `createdTimestamp`). Pagination is out of scope (see below); a real ledger would page and index on `account_number`.
- **No password policy** — any non-blank string is accepted as a password. `@Size(min=8)` and complexity constraints would be added before production.
- **No email verification** — any email address can be registered without proof of ownership.
- **HTTPS assumed at the deployment layer** — the API itself does not enforce TLS; a load balancer or reverse proxy is assumed to handle it.

## Out of scope

These were consciously deferred as outside the spec or disproportionate to a take-home exercise:

| Item | Rationale |
|------|-----------|
| Token refresh / revocation | Short-lived tokens with refresh would require a server-side token store; stateless JWT is appropriate for this scope |
| 2FA / MFA | No phone or TOTP infrastructure; flagged as out of scope in the brief |
| Rate limiting on endpoints like `POST /v1/auth/login` | Important in production to prevent brute-force; would use a filter + Redis counter |
| Audit logging | `createdTimestamp` / `updatedTimestamp` on entities; a full audit trail (who changed what, when) would need a separate audit table |
| External metrics backend | Actuator metrics are exposed on the internal dev port; publishing to Graphite or Prometheus would require deployment-specific registry configuration |
| Distributed transaction locking | A single-instance H2 demo does not face concurrent node contention; distributed locks (Redlock, DB advisory locks) are production concerns |
| UUID-based identifiers at scale | `usr-<uuid>` and `tan-<uuid>` would eliminate collision risk; the current prefixed-random format satisfies the spec patterns and is honest about demo scope |
| Privileged roles (admin, teller) | The spec defines a single user type; role-based access control would require a `roles` table and Spring Security method security |
| Soft delete | Hard deletion is used; a `deletedAt` timestamp and `status` field would be needed to preserve history and support account recovery |
| Optimistic locking | `@Version` on `User` and `Account` would prevent lost-update races on concurrent PATCH requests |

## OpenAPI spec

The full API spec lives at [src/main/resources/openapi.yaml](src/main/resources/openapi.yaml). Swagger UI is served at `http://localhost:8081/swagger-ui/index.html` once the application is running with the dev profile.
