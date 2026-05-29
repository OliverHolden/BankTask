package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.model.Address;
import com.OliverHolden.BankApplication.model.User;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import com.OliverHolden.BankApplication.repository.UserRepository;
import com.OliverHolden.BankApplication.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UserControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String USER_A_ID    = "usr-int-a";
    private static final String USER_A_EMAIL = "user-a@example.com";
    private static final String USER_B_ID    = "usr-int-b";
    private static final String USER_B_EMAIL = "user-b@example.com";
    private static final String PASSWORD     = "Password123!";

    private String tokenA;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(buildUser(USER_A_ID, USER_A_EMAIL));
        userRepository.save(buildUser(USER_B_ID, USER_B_EMAIL));
        tokenA = jwtTokenProvider.generateToken(USER_A_ID).token();
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/users (operationId: createUser)
    // -------------------------------------------------------------------------

    @Test
    void createUser_validRequest_returns201WithUserResponse() throws Exception {
        // Spec: 201 — response shape matches UserResponse schema; no password field
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserBody("new@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(matchesPattern("^usr-[A-Za-z0-9]+$")))
                .andExpect(jsonPath("$.name").value("New User"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+447911999888"))
                .andExpect(jsonPath("$.address.line1").value("2 New Street"))
                .andExpect(jsonPath("$.createdTimestamp").isNotEmpty())
                .andExpect(jsonPath("$.updatedTimestamp").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        // Spec: 409 — email already registered
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserBody(USER_A_EMAIL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createUser_missingRequiredFields_returns400WithFieldErrors() throws Exception {
        // Spec: 400 — BadRequestErrorResponse with details array
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.details[*].field").isNotEmpty())
                .andExpect(jsonPath("$.details[*].message").isNotEmpty());
    }

    @Test
    void createUser_invalidPhoneFormat_returns400() throws Exception {
        // Spec: CreateUserRequest.phoneNumber format: ^\+[1-9]\d{1,14}$
        var body = createUserBody("phone-test@example.com");
        body.put("phoneNumber", "not-a-phone");

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("phoneNumber")));
    }

    @Test
    void createUser_invalidEmailFormat_returns400() throws Exception {
        // Spec: CreateUserRequest.email format: email
        Map<String, Object> body = createUserBody("not-an-email");

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("email")));
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/users/{userId} (operationId: fetchUserByID)
    // -------------------------------------------------------------------------

    @Test
    void getUser_ownRecord_returns200WithUserResponse() throws Exception {
        // Spec: 200 — returns UserResponse for the authenticated user
        mockMvc.perform(get("/v1/users/{userId}", USER_A_ID)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_A_ID))
                .andExpect(jsonPath("$.email").value(USER_A_EMAIL))
                .andExpect(jsonPath("$.address").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUser_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(get("/v1/users/{userId}", USER_A_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_differentUser_returns403() throws Exception {
        // Spec: 403 — authenticated user may not access another user's record
        mockMvc.perform(get("/v1/users/{userId}", USER_B_ID)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void getUser_validJwtForAbsentUser_returns401() throws Exception {
        // JwtAuthFilter explicitly returns 401 when the JWT is valid but the user no longer exists in the DB.
        // The 404 service path is covered by UserServiceTest; this exercises the filter's deleted-user guard.
        mockMvc.perform(get("/v1/users/{userId}", "usr-nonexistent")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateToken("usr-nonexistent").token()))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Spec: PATCH /v1/users/{userId} (operationId: updateUserByID)
    // PATCH is a partial update — only supplied fields are modified
    // -------------------------------------------------------------------------

    @Test
    void updateUser_partialUpdate_returns200WithUpdatedFields() throws Exception {
        // Spec: 200 — only supplied fields are modified; omitted fields stay unchanged
        mockMvc.perform(patch("/v1/users/{userId}", USER_A_ID)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated Name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value(USER_A_EMAIL)); // unchanged
    }

    @Test
    void updateUser_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(patch("/v1/users/{userId}", USER_A_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUser_differentUser_returns403() throws Exception {
        // Spec: 403 — authenticated user may not update another user's record
        mockMvc.perform(patch("/v1/users/{userId}", USER_B_ID)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateUser_duplicateEmail_returns409() throws Exception {
        // Spec: 409 — new email is already taken by another user
        mockMvc.perform(patch("/v1/users/{userId}", USER_A_ID)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", USER_B_EMAIL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // Spec: DELETE /v1/users/{userId} (operationId: deleteUserByID)
    // -------------------------------------------------------------------------

    @Test
    void deleteUser_noAccounts_returns204() throws Exception {
        // Spec: 204 No Content — user deleted successfully
        mockMvc.perform(delete("/v1/users/{userId}", USER_A_ID)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteUser_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(delete("/v1/users/{userId}", USER_A_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_differentUser_returns403() throws Exception {
        // Spec: 403 — authenticated user may not delete another user
        mockMvc.perform(delete("/v1/users/{userId}", USER_B_ID)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void deleteUser_hasAccounts_returns409() throws Exception {
        // Spec: 409 — a user cannot be deleted when associated with a bank account
        accountRepository.save(Account.builder()
                .accountNumber("01000001")
                .userId(USER_A_ID)
                .build());

        mockMvc.perform(delete("/v1/users/{userId}", USER_A_ID)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildUser(String id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .name("Test User")
                .address(Address.builder()
                        .line1("1 Test Street")
                        .town("London")
                        .county("Greater London")
                        .postcode("SW1A 1AA")
                        .build())
                .phoneNumber("+447911123456")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build();
    }

    private Map<String, Object> createUserBody(String email) {
        return new java.util.HashMap<>(Map.of(
                "name", "New User",
                "email", email,
                "password", PASSWORD,
                "phoneNumber", "+447911999888",
                "address", Map.of(
                        "line1", "2 New Street",
                        "town", "Manchester",
                        "county", "Greater Manchester",
                        "postcode", "M1 1AA"
                )
        ));
    }
}
