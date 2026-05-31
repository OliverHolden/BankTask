package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.model.AccountType;
import com.OliverHolden.BankApplication.model.Address;
import com.OliverHolden.BankApplication.model.Currency;
import com.OliverHolden.BankApplication.model.Transaction;
import com.OliverHolden.BankApplication.model.TransactionType;
import com.OliverHolden.BankApplication.model.User;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import com.OliverHolden.BankApplication.repository.TransactionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TransactionControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String USER_A_ID = "usr-txA";
    private static final String USER_B_ID = "usr-txB";
    private static final String ACCOUNT_NUMBER = "01555100";

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(buildUser(USER_A_ID, "tx-a@example.com"));
        userRepository.save(buildUser(USER_B_ID, "tx-b@example.com"));
        tokenA = jwtTokenProvider.generateToken(USER_A_ID).token();
        tokenB = jwtTokenProvider.generateToken(USER_B_ID).token();

        accountRepository.save(Account.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .userId(USER_A_ID)
                .sortCode("10-10-10")
                .name("Existing Account")
                .accountType(AccountType.personal)
                .balance(new BigDecimal("100.00"))
                .currency("GBP")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build());
    }

    private String body(String type, Object amount) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", type,
                "amount", amount,
                "currency", "GBP"));
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/accounts/{accountNumber}/transactions (operationId: createTransaction)
    // -------------------------------------------------------------------------

    @Test
    void createTransaction_deposit_returns201AndUpdatesBalance() throws Exception {
        // Spec: 201 — deposit registered, balance updated
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("deposit", "50.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(matchesPattern("^tan-[A-Za-z0-9]+$")))
                .andExpect(jsonPath("$.type").value("deposit"))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.userId").value(USER_A_ID))
                .andExpect(jsonPath("$.createdTimestamp").isNotEmpty());

        assertThat(accountRepository.findById(ACCOUNT_NUMBER).orElseThrow().getBalance())
                .isEqualByComparingTo("150.00");
    }

    @Test
    void createTransaction_withdrawalWithSufficientFunds_returns201() throws Exception {
        // Spec: 201 — withdrawal with sufficient funds updates balance
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("withdrawal", "40.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("withdrawal"));

        assertThat(accountRepository.findById(ACCOUNT_NUMBER).orElseThrow().getBalance())
                .isEqualByComparingTo("60.00");
    }

    @Test
    void createTransaction_insufficientFunds_returns422() throws Exception {
        // Spec: 422 — withdrawal exceeds available balance
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("withdrawal", "150.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createTransaction_missingType_returns400() throws Exception {
        // Spec: 400 — required field missing
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "10.00", "currency", "GBP"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("type")));
    }

    @Test
    void createTransaction_moreThanTwoDecimalPlaces_returns400() throws Exception {
        // Spec: amount is "up to two decimal places" — a third decimal is rejected, not silently rounded
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("deposit", "10.999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("amount")));
    }

    @Test
    void createTransaction_invalidType_returns400() throws Exception {
        // Spec: 400 — unrecognised transaction type enum value
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"transfer\",\"amount\":10.00,\"currency\":\"GBP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("deposit", "10.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTransaction_differentUser_returns403() throws Exception {
        // Spec: 403 — user may not transact on another user's account
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("deposit", "10.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createTransaction_nonExistentAccount_returns404() throws Exception {
        // Spec: 404 — account does not exist
        mockMvc.perform(post("/v1/accounts/{accountNumber}/transactions", "01000099")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("deposit", "10.00")))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts/{accountNumber}/transactions (operationId: listAccountTransaction)
    // -------------------------------------------------------------------------

    @Test
    void listTransactions_ownAccount_returns200() throws Exception {
        // Spec: 200 — returns transactions for the owner's account
        transactionRepository.save(buildTransaction("tan-seed1"));

        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].id").value("tan-seed1"));
    }

    @Test
    void listTransactions_returnsNewestFirst() throws Exception {
        // Ledger ordering: most recent transaction is returned first
        Transaction older = buildTransaction("tan-older");
        older.setCreatedTimestamp(OffsetDateTime.now().minusHours(1));
        Transaction newer = buildTransaction("tan-newer");
        newer.setCreatedTimestamp(OffsetDateTime.now());
        transactionRepository.save(older);
        transactionRepository.save(newer);

        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].id").value("tan-newer"))
                .andExpect(jsonPath("$.transactions[1].id").value("tan-older"));
    }

    @Test
    void listTransactions_differentUser_returns403() throws Exception {
        // Spec: 403 — user may not list another user's transactions
        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTransactions_nonExistentAccount_returns404() throws Exception {
        // Spec: 404 — account does not exist
        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions", "01000099")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void listTransactions_noToken_returns401() throws Exception {
        // Spec: 401 — access token is missing
        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions", ACCOUNT_NUMBER))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts/{accountNumber}/transactions/{transactionId}
    // -------------------------------------------------------------------------

    @Test
    void getTransaction_ownAccount_returns200() throws Exception {
        // Spec: 200 — transaction belonging to the owner's account is returned
        transactionRepository.save(buildTransaction("tan-seed2"));

        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions/{transactionId}", ACCOUNT_NUMBER, "tan-seed2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("tan-seed2"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void getTransaction_nonExistentTransaction_returns404() throws Exception {
        // Spec: 404 — transactionId does not exist
        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions/{transactionId}", ACCOUNT_NUMBER, "tan-missing")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransaction_transactionBelongsToAnotherAccount_returns404() throws Exception {
        // Spec: 404 — transaction exists but is not associated with the given accountNumber
        Account other = accountRepository.save(Account.builder()
                .accountNumber("01555200")
                .userId(USER_A_ID)
                .sortCode("10-10-10")
                .name("Second Account")
                .accountType(AccountType.personal)
                .balance(new BigDecimal("0.00"))
                .currency("GBP")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build());
        Transaction onOther = buildTransaction("tan-other");
        onOther.setAccountNumber(other.getAccountNumber());
        transactionRepository.save(onOther);

        // Look up the transaction under the first account — it belongs to the second.
        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions/{transactionId}", ACCOUNT_NUMBER, "tan-other")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransaction_differentUser_returns403() throws Exception {
        // Spec: 403 — user may not fetch a transaction on another user's account
        transactionRepository.save(buildTransaction("tan-seed3"));

        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions/{transactionId}", ACCOUNT_NUMBER, "tan-seed3")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransaction_nonExistentAccount_returns404() throws Exception {
        // Spec: 404 — account does not exist
        mockMvc.perform(get("/v1/accounts/{accountNumber}/transactions/{transactionId}", "01000099", "tan-seed4")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Transaction buildTransaction(String id) {
        return Transaction.builder()
                .id(id)
                .accountNumber(ACCOUNT_NUMBER)
                .userId(USER_A_ID)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.GBP)
                .type(TransactionType.deposit)
                .createdTimestamp(OffsetDateTime.now())
                .build();
    }

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
