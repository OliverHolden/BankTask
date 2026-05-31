package com.OliverHolden.BankApplication.service;

import com.OliverHolden.BankApplication.dto.request.CreateTransactionRequest;
import com.OliverHolden.BankApplication.dto.response.ListTransactionsResponse;
import com.OliverHolden.BankApplication.dto.response.TransactionResponse;
import com.OliverHolden.BankApplication.exception.ForbiddenException;
import com.OliverHolden.BankApplication.exception.NotFoundException;
import com.OliverHolden.BankApplication.exception.UnprocessableEntityException;
import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.model.AccountType;
import com.OliverHolden.BankApplication.model.Currency;
import com.OliverHolden.BankApplication.model.Transaction;
import com.OliverHolden.BankApplication.model.TransactionType;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import com.OliverHolden.BankApplication.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class TransactionServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks TransactionService transactionService;

    private static final String USER_A = "usr-aaa";
    private static final String USER_B = "usr-bbb";
    private static final String ACCOUNT_NUMBER = "01123456";
    private static final String TRANSACTION_ID = "tan-abc123";

    Account existingAccount;

    @BeforeEach
    void setUp() {
        existingAccount = Account.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .userId(USER_A)
                .sortCode("10-10-10")
                .name("My Account")
                .accountType(AccountType.personal)
                .balance(new BigDecimal("100.00"))
                .currency("GBP")
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build();
    }

    private CreateTransactionRequest request(TransactionType type, String amount) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency(Currency.GBP);
        return request;
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/accounts/{accountNumber}/transactions (operationId: createTransaction)
    // -------------------------------------------------------------------------

    @Test
    void createTransaction_deposit_increasesBalanceAndStoresTransaction() {
        // Spec: 201 — deposit is registered and the account balance is updated
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.deposit, "50.00"), USER_A);

        assertThat(response.getId()).matches("^tan-[A-Za-z0-9]+$");
        assertThat(response.getType()).isEqualTo(TransactionType.deposit);
        assertThat(response.getAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getUserId()).isEqualTo(USER_A);
        assertThat(existingAccount.getBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void createTransaction_withdrawalWithSufficientFunds_decreasesBalance() {
        // Spec: 201 — withdrawal with sufficient funds updates the balance
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.withdrawal, "40.00"), USER_A);

        assertThat(response.getType()).isEqualTo(TransactionType.withdrawal);
        assertThat(existingAccount.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void createTransaction_withdrawalExactlyBalance_succeedsToZero() {
        // Boundary: withdrawing the full balance is allowed and leaves zero
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.withdrawal, "100.00"), USER_A);

        assertThat(existingAccount.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void createTransaction_withdrawalInsufficientFunds_throwsUnprocessableEntity() {
        // Spec: 422 — withdrawal amount exceeds the available balance
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.withdrawal, "150.00"), USER_A))
                .isInstanceOf(UnprocessableEntityException.class);
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_depositExceedingMaxBalance_throwsUnprocessableEntity() {
        // Gap #4: a deposit that would push the balance over £10,000 is rejected with 422
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.deposit, "9999.00"), USER_A))
                .isInstanceOf(UnprocessableEntityException.class);
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_accountNotFound_throwsNotFound() {
        // Spec: 404 — account does not exist
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.deposit, "10.00"), USER_A))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createTransaction_differentUser_throwsForbidden() {
        // Spec: 403 — user may not transact on another user's account
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.deposit, "10.00"), USER_B))
                .isInstanceOf(ForbiddenException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_persistsTransactionScopedToAccountAndUser() {
        // The stored transaction must carry the account number and authenticated user
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        transactionService.createTransaction(
                ACCOUNT_NUMBER, request(TransactionType.deposit, "25.00"), USER_A);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_A);
        assertThat(captor.getValue().getCreatedTimestamp()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts/{accountNumber}/transactions (operationId: listAccountTransaction)
    // -------------------------------------------------------------------------

    @Test
    void listTransactions_ownAccount_returnsTransactions() {
        // Spec: 200 — transactions for the owner's account are returned
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(transactionRepository.findAllByAccountNumberOrderByCreatedTimestampDesc(ACCOUNT_NUMBER))
                .thenReturn(List.of(buildTransaction()));

        ListTransactionsResponse response = transactionService.listTransactions(ACCOUNT_NUMBER, USER_A);

        assertThat(response.getTransactions()).hasSize(1);
        assertThat(response.getTransactions().get(0).getId()).isEqualTo(TRANSACTION_ID);
    }

    @Test
    void listTransactions_accountNotFound_throwsNotFound() {
        // Spec: 404 — account does not exist
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.listTransactions(ACCOUNT_NUMBER, USER_A))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listTransactions_differentUser_throwsForbidden() {
        // Spec: 403 — user may not view another user's transactions
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> transactionService.listTransactions(ACCOUNT_NUMBER, USER_B))
                .isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/accounts/{accountNumber}/transactions/{transactionId}
    // -------------------------------------------------------------------------

    @Test
    void getTransaction_ownAccount_returnsTransaction() {
        // Spec: 200 — transaction belonging to the owner's account is returned
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(transactionRepository.findByIdAndAccountNumber(TRANSACTION_ID, ACCOUNT_NUMBER))
                .thenReturn(Optional.of(buildTransaction()));

        TransactionResponse response = transactionService.getTransaction(ACCOUNT_NUMBER, TRANSACTION_ID, USER_A);

        assertThat(response.getId()).isEqualTo(TRANSACTION_ID);
    }

    @Test
    void getTransaction_accountNotFound_throwsNotFound() {
        // Spec: 404 — account does not exist
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(ACCOUNT_NUMBER, TRANSACTION_ID, USER_A))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTransaction_differentUser_throwsForbidden() {
        // Spec: 403 — user may not view a transaction on another user's account
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> transactionService.getTransaction(ACCOUNT_NUMBER, TRANSACTION_ID, USER_B))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getTransaction_transactionNotFoundOrWrongAccount_throwsNotFound() {
        // Spec: 404 — transaction does not exist, or exists but belongs to another account
        when(accountRepository.findById(ACCOUNT_NUMBER)).thenReturn(Optional.of(existingAccount));
        when(transactionRepository.findByIdAndAccountNumber(TRANSACTION_ID, ACCOUNT_NUMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(ACCOUNT_NUMBER, TRANSACTION_ID, USER_A))
                .isInstanceOf(NotFoundException.class);
    }

    private Transaction buildTransaction() {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .accountNumber(ACCOUNT_NUMBER)
                .userId(USER_A)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.GBP)
                .type(TransactionType.deposit)
                .createdTimestamp(OffsetDateTime.now())
                .build();
    }
}
