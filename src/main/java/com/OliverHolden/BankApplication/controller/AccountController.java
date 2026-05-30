package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.dto.request.CreateBankAccountRequest;
import com.OliverHolden.BankApplication.dto.request.UpdateBankAccountRequest;
import com.OliverHolden.BankApplication.dto.response.BankAccountResponse;
import com.OliverHolden.BankApplication.dto.response.ListBankAccountsResponse;
import com.OliverHolden.BankApplication.security.CustomUserPrincipal;
import com.OliverHolden.BankApplication.service.AccountService;
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
@Tag(name = "account", description = "Manage a bank account")
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private static final String ACCOUNT_NUMBER_PATTERN = "^01\\d{6}$";

    private final AccountService accountService;

    @Operation(summary = "Create a new bank account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid details supplied"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @PostMapping
    public ResponseEntity<BankAccountResponse> createAccount(
            @Valid @RequestBody CreateBankAccountRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("POST /v1/accounts");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request, principal.getId()));
    }

    @Operation(summary = "List accounts for authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of accounts"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @GetMapping
    public ResponseEntity<ListBankAccountsResponse> listAccounts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("GET /v1/accounts");
        return ResponseEntity.ok(accountService.listAccounts(principal.getId()));
    }

    @Operation(summary = "Fetch account by account number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account details"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @GetMapping("/{accountNumber}")
    public ResponseEntity<BankAccountResponse> getAccount(
            @PathVariable @Pattern(regexp = ACCOUNT_NUMBER_PATTERN) String accountNumber,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("GET /v1/accounts/{}", accountNumber);
        return ResponseEntity.ok(accountService.getAccount(accountNumber, principal.getId()));
    }

    @Operation(summary = "Update account by account number")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @PatchMapping("/{accountNumber}")
    public ResponseEntity<BankAccountResponse> updateAccount(
            @PathVariable @Pattern(regexp = ACCOUNT_NUMBER_PATTERN) String accountNumber,
            @Valid @RequestBody UpdateBankAccountRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("PATCH /v1/accounts/{}", accountNumber);
        return ResponseEntity.ok(accountService.updateAccount(accountNumber, request, principal.getId()));
    }

    @Operation(summary = "Delete account by account number")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable @Pattern(regexp = ACCOUNT_NUMBER_PATTERN) String accountNumber,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("DELETE /v1/accounts/{}", accountNumber);
        accountService.deleteAccount(accountNumber, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
