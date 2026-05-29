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
mvn spring-boot:run
```

The API will start on `http://localhost:8080`.

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

## OpenAPI spec

The full API spec lives at [src/main/resources/openapi.yaml](src/main/resources/openapi.yaml). Swagger UI is served at `http://localhost:8080/swagger-ui/index.html` once the application is running.
