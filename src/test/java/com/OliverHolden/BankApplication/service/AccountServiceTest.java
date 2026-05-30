package com.OliverHolden.BankApplication.service;

import com.OliverHolden.BankApplication.dto.request.CreateBankAccountRequest;
import com.OliverHolden.BankApplication.dto.request.UpdateBankAccountRequest;
import com.OliverHolden.BankApplication.dto.response.BankAccountResponse;
import com.OliverHolden.BankApplication.dto.response.ListBankAccountsResponse;
import com.OliverHolden.BankApplication.exception.ForbiddenException;
import com.OliverHolden.BankApplication.exception.NotFoundException;
import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.model.AccountType;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;

    @InjectMocks AccountService accountService;

    private static final String USER_A = "usr-aaa";
    private static final String USER_B = "usr-bbb";
    private static final String ACCOUNT_NUMBER = "01123456";

    Account existingAccount;

    @BeforeEach
    void setUp() {
        existingAccount = Account.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .userId(USER_A)
                .sortCode("10-10-10")
                .name("My Account")
                .accountType(AccountType.personal)
                .balance(BigDecimal.valueOf(500, 2))
                .currency("GBP")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/accounts (operationId: createAccount)
    // -------------------------------------------------------------------------

    @Test
    void createAccount_validRequest_createsAccountWithZeroBalance() {
        // Spec: 201 — new account starts at 0.00 balance; sortCode fixed to "10-10-10"; currency GBP
        when(accountRepository.existsById(any())).thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBankAccountRequest request = new CreateBankAccountRequest();
        request.setName("My Account");
        request.setAccountType(AccountType.personal);

        BankAccountResponse response = accountService.createAccount(request, USER_A);

        assertThat(response.getAccountNumber()).matches("^01\\d{6}$");
        assertThat(response.getSortCode()).isEqualTo("10-10-10");
        assertThat(response.getName()).isEqualTo("My Account");
        assertThat(response.getAccountType()).isEqualTo(AccountType.personal);
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCurrency()).isEqualTo("GBP");
        assertThat(response.getCreatedTimestamp()).isNotNull();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_collisionOnFirstAttempt_retriesUntilUniqueNumber() {
        // If the generated account number already exists, the service retries
        when(accountRepository.existsById(any())).thenReturn(true, false);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBankAccountRequest request = new CreateBankAccountRequest();
        request.setName("Retry Account");
        request.setAccountType(AccountType.personal);

        BankAccountResponse response = accountService.createAccount(request, USER_A);

        assertThat(response.getAccountNumber()).matches("^01\\d{6}$");
        verify(accountRepository, atLeast(2)).existsById(any());
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts (operationId: listAccounts)
    // -------------------------------------------------------------------------

    @Test
    void listAccounts_returnsOnlyAccountsForAuthenticatedUser() {
        // Spec: 200 — only the authenticated user's accounts are returned
        when(accountRepository.findAllByUserId(USER_A)).thenReturn(List.of(existingAccount));

        ListBankAccountsResponse response = accountService.listAccounts(USER_A);

        assertThat(response.getAccounts()).hasSize(1);
        assertThat(response.getAccounts().get(0).getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    void listAccounts_noAccounts_returnsEmptyList() {
        // Spec: 200 with empty list when user has no accounts
        when(accountRepository.findAllByUserId(USER_A)).thenReturn(List.of());

        ListBankAccountsResponse response = accountService.listAccounts(USER_A);

        assertThat(response.getAccounts()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts/{accountNumber} (operationId: fetchAccountByAccountNumber)
    // -------------------------------------------------------------------------

    @Test
    void getAccount_ownAccount_returnsAccountResponse() {
        // Spec: 200 — returns account details for the account owner
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        BankAccountResponse response = accountService.getAccount(ACCOUNT_NUMBER, USER_A);

        assertThat(response.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.getBalance()).isNotNull();
    }

    @Test
    void getAccount_accountBelongsToDifferentUser_throwsForbiddenException() {
        // Spec: 403 — account exists but is owned by a different user
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> accountService.getAccount(ACCOUNT_NUMBER, USER_B))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getAccount_notFound_throwsNotFoundException() {
        // Spec: 404 — account does not exist
        when(accountRepository.findById("01999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount("01999999", USER_A))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Spec: PATCH /v1/accounts/{accountNumber} (operationId: updateAccountByAccountNumber)
    // PATCH is a partial update — omitted fields must remain unchanged
    // -------------------------------------------------------------------------

    @Test
    void updateAccount_partialUpdate_updatesOnlySuppliedFields() {
        // Spec: PATCH — only supplied fields are modified
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest();
        request.setName("Renamed Account");

        BankAccountResponse response = accountService.updateAccount(ACCOUNT_NUMBER, request, USER_A);

        assertThat(response.getName()).isEqualTo("Renamed Account");
        assertThat(response.getAccountType()).isEqualTo(AccountType.personal); // unchanged
    }

    @Test
    void updateAccount_noFieldsProvided_updatesTimestampOnly() {
        // Spec: PATCH /v1/accounts/{accountNumber} — all fields optional; empty request leaves data unchanged
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponse response = accountService.updateAccount(ACCOUNT_NUMBER, new UpdateBankAccountRequest(), USER_A);

        assertThat(response.getName()).isEqualTo("My Account");          // unchanged
        assertThat(response.getAccountType()).isEqualTo(AccountType.personal); // unchanged
        assertThat(response.getUpdatedTimestamp()).isAfterOrEqualTo(existingAccount.getUpdatedTimestamp());
    }

    @Test
    void updateAccount_differentUser_throwsForbiddenException() {
        // Spec: 403 — authenticated user may not update another user's account
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> accountService.updateAccount(ACCOUNT_NUMBER, new UpdateBankAccountRequest(), USER_B))
                .isInstanceOf(ForbiddenException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void updateAccount_notFound_throwsNotFoundException() {
        // Spec: 404 — account does not exist
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateAccount(ACCOUNT_NUMBER, new UpdateBankAccountRequest(), USER_A))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Spec: DELETE /v1/accounts/{accountNumber} (operationId: deleteAccountByAccountNumber)
    // -------------------------------------------------------------------------

    @Test
    void deleteAccount_ownAccount_deletesSuccessfully() {
        // Spec: 204 — account is deleted when owned by the authenticated user
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        accountService.deleteAccount(ACCOUNT_NUMBER, USER_A);

        verify(accountRepository).deleteById(ACCOUNT_NUMBER);
    }

    @Test
    void deleteAccount_differentUser_throwsForbiddenException() {
        // Spec: 403 — authenticated user may not delete another user's account
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> accountService.deleteAccount(ACCOUNT_NUMBER, USER_B))
                .isInstanceOf(ForbiddenException.class);
        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void deleteAccount_notFound_throwsNotFoundException() {
        // Spec: 404 — account does not exist
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deleteAccount(ACCOUNT_NUMBER, USER_A))
                .isInstanceOf(NotFoundException.class);
        verify(accountRepository, never()).deleteById(any());
    }
}
