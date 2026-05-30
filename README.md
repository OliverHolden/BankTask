# Eagle Bank API

A RESTful banking API built with Spring Boot for the Barclays take-home coding test. It supports user management, bank accounts, and deposit/withdrawal transactions, secured with JWT bearer token authentication.

## Tech stack

- Java 21
- Spring Boot 4.0.6 (Web MVC)
- Spring Security + JWT
- Spring Data JPA (H2 in-memory database)
- Lombok
- Maven
- springdoc-openapi (Swagger UI)

## Running the application

**Prerequisites:** Java 21 and Maven installed.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The dev profile must be active — it supplies `jwt.secret` which is absent from the base `application.properties`. The API will start on `http://localhost:8080`.

### Run with Docker

```bash
docker build -f Notes/Dockerfile -t eagle-bank .
docker run -p 8080:8080 eagle-bank
```

### Run tests

```bash
mvn test
```

## API overview

All protected endpoints require an `Authorization: Bearer <token>` header. Obtain a token via `POST /v1/auth/login`.

Full endpoint documentation is available via Swagger UI at `http://localhost:8080/swagger-ui/index.html` once the application is running.

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

## OpenAPI spec

The full API spec lives at [src/main/resources/openapi.yaml](src/main/resources/openapi.yaml). Swagger UI is served at `http://localhost:8080/swagger-ui/index.html` once the application is running.
