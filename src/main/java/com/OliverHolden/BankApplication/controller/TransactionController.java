package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.dto.request.CreateTransactionRequest;
import com.OliverHolden.BankApplication.dto.response.ListTransactionsResponse;
import com.OliverHolden.BankApplication.dto.response.TransactionResponse;
import com.OliverHolden.BankApplication.security.CustomUserPrincipal;
import com.OliverHolden.BankApplication.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "transaction", description = "Manage transactions on a bank account")
@RestController
@RequestMapping("/v1/accounts/{accountNumber}/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private static final String ACCOUNT_NUMBER_PATTERN = "^01\\d{6}$";
    private static final String TRANSACTION_ID_PATTERN = "^tan-[A-Za-z0-9]+$";

    private final TransactionService transactionService;

    @Operation(summary = "Create a transaction (deposit or withdrawal)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transaction created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid details supplied"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds, or deposit would exceed the maximum account balance"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @PathVariable @Pattern(regexp = ACCOUNT_NUMBER_PATTERN) String accountNumber,
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("POST /v1/accounts/{}/transactions", accountNumber);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(accountNumber, request, principal.getId()));
    }

    @Operation(summary = "List transactions for an account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of transactions"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @GetMapping
    public ResponseEntity<ListTransactionsResponse> listTransactions(
            @PathVariable @Pattern(regexp = ACCOUNT_NUMBER_PATTERN) String accountNumber,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("GET /v1/accounts/{}/transactions", accountNumber);
        return ResponseEntity.ok(transactionService.listTransactions(accountNumber, principal.getId()));
    }

    @Operation(summary = "Fetch a transaction by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction details"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Account or transaction not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable @Pattern(regexp = ACCOUNT_NUMBER_PATTERN) String accountNumber,
            @PathVariable @Pattern(regexp = TRANSACTION_ID_PATTERN) String transactionId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("GET /v1/accounts/{}/transactions/{}", accountNumber, transactionId);
        return ResponseEntity.ok(transactionService.getTransaction(accountNumber, transactionId, principal.getId()));
    }
}
