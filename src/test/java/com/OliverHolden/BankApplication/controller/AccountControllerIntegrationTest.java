package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.model.AccountType;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AccountControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String USER_A_ID = "usr-accA";
    private static final String USER_B_ID = "usr-accB";
    private static final String ACCOUNT_NUMBER = "01555001";

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(buildUser(USER_A_ID, "acc-a@example.com"));
        userRepository.save(buildUser(USER_B_ID, "acc-b@example.com"));
        tokenA = jwtTokenProvider.generateToken(USER_A_ID).token();
        tokenB = jwtTokenProvider.generateToken(USER_B_ID).token();

        accountRepository.save(Account.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .userId(USER_A_ID)
                .sortCode("10-10-10")
                .name("Existing Account")
                .accountType(AccountType.personal)
                .balance(BigDecimal.valueOf(100, 0))
                .currency("GBP")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build());
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/accounts (operationId: createAccount)
    // -------------------------------------------------------------------------

    @Test
    void createAccount_validRequest_returns201WithAccountResponse() throws Exception {
        // Spec: 201 — new account with zero balance, fixed sort code and currency
        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Account",
                                "accountType", "personal"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("^01\\d{6}$")))
                .andExpect(jsonPath("$.sortCode").value("10-10-10"))
                .andExpect(jsonPath("$.name").value("New Account"))
                .andExpect(jsonPath("$.accountType").value("personal"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.createdTimestamp").isNotEmpty())
                .andExpect(jsonPath("$.updatedTimestamp").isNotEmpty());
    }

    @Test
    void createAccount_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "My Account",
                                "accountType", "personal"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_missingName_returns400() throws Exception {
        // Spec: 400 — name is required
        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("accountType", "personal"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("name")));
    }

    @Test
    void createAccount_missingAccountType_returns400() throws Exception {
        // Spec: 400 — accountType is required
        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "My Account"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("accountType")));
    }

    @Test
    void createAccount_invalidAccountType_returns400() throws Exception {
        // Spec: 400 — unrecognised accountType enum value
        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Account\",\"accountType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts (operationId: listAccounts)
    // -------------------------------------------------------------------------

    @Test
    void listAccounts_returns200WithOwnAccounts() throws Exception {
        // Spec: 200 — returns only the authenticated user's accounts
        mockMvc.perform(get("/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].accountNumber").value(ACCOUNT_NUMBER));
    }

    @Test
    void listAccounts_userWithNoAccounts_returnsEmptyList() throws Exception {
        // Spec: 200 with empty list when user has no accounts
        mockMvc.perform(get("/v1/accounts")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts", hasSize(0)));
    }

    @Test
    void listAccounts_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(get("/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts/{accountNumber} (operationId: fetchAccountByAccountNumber)
    // -------------------------------------------------------------------------

    @Test
    void getAccount_ownAccount_returns200() throws Exception {
        // Spec: 200 — returns full account details for the owner
        mockMvc.perform(get("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.sortCode").value("10-10-10"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.balance").isNumber());
    }

    @Test
    void getAccount_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(get("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccount_differentUser_returns403() throws Exception {
        // Spec: 403 — authenticated user may not access another user's account
        mockMvc.perform(get("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void getAccount_notFound_returns404() throws Exception {
        // Spec: 404 — account does not exist
        mockMvc.perform(get("/v1/accounts/{accountNumber}", "01000099")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void getAccount_invalidAccountNumberFormat_returns400() throws Exception {
        // Spec: GET /v1/accounts/{accountNumber} (operationId: fetchAccountByAccountNumber)
        // accountNumber must match ^01\d{6}$; invalid format returns 400 with BadRequestErrorResponse
        mockMvc.perform(get("/v1/accounts/{accountNumber}", "INVALID")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void deleteAccount_invalidAccountNumberFormat_returns400() throws Exception {
        // Spec: DELETE /v1/accounts/{accountNumber} (operationId: deleteAccountByAccountNumber)
        // accountNumber must match ^01\d{6}$; invalid format returns 400
        mockMvc.perform(delete("/v1/accounts/{accountNumber}", "bad-number")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void listAccounts_noToken_returns401WithErrorResponseBody() throws Exception {
        // Spec: 401 — body must be ErrorResponse {"message":"..."}, not Spring Boot's default error format
        mockMvc.perform(get("/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // Spec: PATCH /v1/accounts/{accountNumber} (operationId: updateAccountByAccountNumber)
    // PATCH is a partial update — only supplied fields are modified
    // -------------------------------------------------------------------------

    @Test
    void updateAccount_partialUpdate_returns200WithUpdatedFields() throws Exception {
        // Spec: 200 — only supplied fields are modified
        mockMvc.perform(patch("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed Account"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Account"))
                .andExpect(jsonPath("$.accountType").value("personal")); // unchanged
    }

    @Test
    void updateAccount_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(patch("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAccount_differentUser_returns403() throws Exception {
        // Spec: 403 — authenticated user may not update another user's account
        mockMvc.perform(patch("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateAccount_notFound_returns404() throws Exception {
        // Spec: 404 — account does not exist
        mockMvc.perform(patch("/v1/accounts/{accountNumber}", "01000099")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "X"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAccount_emptyBody_returns200WithNoChanges() throws Exception {
        // Spec: PATCH /v1/accounts/{accountNumber} (operationId: updateAccountByAccountNumber)
        // All fields are optional — an empty body must be accepted and change nothing
        mockMvc.perform(patch("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Existing Account"))
                .andExpect(jsonPath("$.accountType").value("personal"));
    }

    @Test
    void updateAccount_invalidAccountType_returns400() throws Exception {
        // Spec: 400 — unrecognised accountType enum value in PATCH body
        mockMvc.perform(patch("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Spec: DELETE /v1/accounts/{accountNumber} (operationId: deleteAccountByAccountNumber)
    // -------------------------------------------------------------------------

    @Test
    void deleteAccount_ownAccount_returns204() throws Exception {
        // Spec: 204 No Content — account deleted successfully
        mockMvc.perform(delete("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteAccount_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(delete("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_differentUser_returns403() throws Exception {
        // Spec: 403 — authenticated user may not delete another user's account
        mockMvc.perform(delete("/v1/accounts/{accountNumber}", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void deleteAccount_notFound_returns404() throws Exception {
        // Spec: 404 — account does not exist
        mockMvc.perform(delete("/v1/accounts/{accountNumber}", "01000099")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildUser(String id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
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
}
