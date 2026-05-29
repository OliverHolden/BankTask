package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.model.Address;
import com.OliverHolden.BankApplication.model.User;
import com.OliverHolden.BankApplication.repository.UserRepository;
import com.OliverHolden.BankApplication.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.secret}") private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String TEST_EMAIL = "auth-test@example.com";
    private static final String TEST_PASSWORD = "Password123!";
    private static final String TEST_USER_ID = "usr-auth-test";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .name("Auth Test User")
                .address(Address.builder()
                        .line1("1 Test Street")
                        .town("London")
                        .county("Greater London")
                        .postcode("SW1A 1AA")
                        .build())
                .phoneNumber("+447911123456")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build());
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/auth/login (operationId: login)
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithLoginResponse() throws Exception {
        // Spec: 200 response references LoginResponse schema
        // LoginResponse must contain: token (string), tokenType (string), expiresAt (date-time)
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TEST_EMAIL,
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void login_validCredentials_tokenIsValidJwt() throws Exception {
        // Spec: returned token must be usable as a Bearer token on protected endpoints
        String responseBody = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TEST_EMAIL,
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseBody).get("token").asText();
        String userId = jwtTokenProvider.validateAndDecode(token).getSubject();
        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    void login_invalidPassword_returns401() throws Exception {
        // Spec: 401 response — invalid credentials
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TEST_EMAIL,
                                "password", "wrongpassword"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        // Spec: 401 response — invalid credentials
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nobody@example.com",
                                "password", TEST_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_missingEmail_returns400WithFieldError() throws Exception {
        // Spec: 400 response references BadRequestErrorResponse schema
        // BadRequestErrorResponse must contain: message (string), details (array of {field, message, type})
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", TEST_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.details[*].field", hasItem("email")));
    }

    @Test
    void login_missingPassword_returns400WithFieldError() throws Exception {
        // Spec: 400 response references BadRequestErrorResponse schema
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TEST_EMAIL))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.details[*].field", hasItem("password")));
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        // Spec: 400 — LoginRequest.email is format: email
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", TEST_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("email")));
    }

    // -------------------------------------------------------------------------
    // Spec: security — bearerAuth required on all protected endpoints
    // -------------------------------------------------------------------------

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        // Spec: all endpoints with security: bearerAuth must return 401 when token is absent
        mockMvc.perform(get("/v1/users/" + TEST_USER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_invalidToken_returns401() throws Exception {
        // Spec: 401 — access token is invalid
        mockMvc.perform(get("/v1/users/" + TEST_USER_ID)
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_expiredToken_returns401() throws Exception {
        // Spec: 401 — access token is expired
        JwtTokenProvider shortLived = new JwtTokenProvider(jwtSecret, 1L);
        String expiredToken = shortLived.generateToken(TEST_USER_ID).token();

        mockMvc.perform(get("/v1/users/" + TEST_USER_ID)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validToken_passesAuthLayer() throws Exception {
        // A valid token must pass the JWT filter and reach the endpoint — confirmed by 200, not 401
        String token = jwtTokenProvider.generateToken(TEST_USER_ID).token();

        mockMvc.perform(get("/v1/users/" + TEST_USER_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
