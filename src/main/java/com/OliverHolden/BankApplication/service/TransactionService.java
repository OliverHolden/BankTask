package com.OliverHolden.BankApplication.service;

import com.OliverHolden.BankApplication.dto.request.CreateTransactionRequest;
import com.OliverHolden.BankApplication.dto.response.ListTransactionsResponse;
import com.OliverHolden.BankApplication.dto.response.TransactionResponse;
import com.OliverHolden.BankApplication.exception.ForbiddenException;
import com.OliverHolden.BankApplication.exception.NotFoundException;
import com.OliverHolden.BankApplication.exception.UnprocessableEntityException;
import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.model.Transaction;
import com.OliverHolden.BankApplication.model.TransactionType;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import com.OliverHolden.BankApplication.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final BigDecimal MAX_BALANCE = new BigDecimal("10000.00");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse createTransaction(String accountNumber, CreateTransactionRequest request, String userId) {
        log.info("Creating {} transaction on account {} for user {}", request.getType(), accountNumber, userId);
        Account account = loadOwnedAccount(accountNumber, userId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal newBalance = applyToBalance(account.getBalance(), request);
        account.setBalance(newBalance);
        account.setUpdatedTimestamp(now);
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .id("tan-" + UUID.randomUUID().toString().replace("-", ""))
                .accountNumber(accountNumber)
                .userId(userId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .reference(request.getReference())
                .createdTimestamp(now)
                .build();
        return toResponse(transactionRepository.save(transaction));
    }

    public ListTransactionsResponse listTransactions(String accountNumber, String userId) {
        log.info("Listing transactions on account {} for user {}", accountNumber, userId);
        loadOwnedAccount(accountNumber, userId);
        List<TransactionResponse> transactions = transactionRepository.findAllByAccountNumberOrderByCreatedTimestampDesc(accountNumber)
                .stream()
                .map(this::toResponse)
                .toList();
        return ListTransactionsResponse.builder().transactions(transactions).build();
    }

    public TransactionResponse getTransaction(String accountNumber, String transactionId, String userId) {
        log.info("Fetching transaction {} on account {} for user {}", transactionId, accountNumber, userId);
        loadOwnedAccount(accountNumber, userId);
        // Query by both id AND accountNumber: a transaction that exists but belongs to a
        // different account must return 404, not the other account's data.
        return transactionRepository.findByIdAndAccountNumber(transactionId, accountNumber)
                .map(this::toResponse)
                .orElseThrow(() -> {
                    log.warn("Transaction not found: {} on account {}", transactionId, accountNumber);
                    return new NotFoundException("Transaction not found");
                });
    }

    /**
     * Loads the account and verifies the authenticated user owns it.
     * 404 if the account does not exist, 403 if it belongs to another user.
     */
    private Account loadOwnedAccount(String accountNumber, String userId) {
        Account account = accountRepository.findById(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new NotFoundException("Account not found");
                });
        if (!account.getUserId().equals(userId)) {
            log.warn("Forbidden: user {} attempted to access transactions on account {}", userId, accountNumber);
            throw new ForbiddenException("Access denied");
        }
        return account;
    }

    /**
     * Computes the resulting balance for a transaction, enforcing the business rules:
     * withdrawals cannot exceed the balance, deposits cannot breach the £10,000 cap.
     * Uses BigDecimal.compareTo for exact financial comparisons.
     */
    private BigDecimal applyToBalance(BigDecimal balance, CreateTransactionRequest request) {
        if (request.getType() == TransactionType.withdrawal) {
            if (balance.compareTo(request.getAmount()) < 0) {
                log.warn("Insufficient funds: balance {} < requested {}", balance, request.getAmount());
                throw new UnprocessableEntityException("Insufficient funds to process transaction");
            }
            return balance.subtract(request.getAmount());
        }
        BigDecimal newBalance = balance.add(request.getAmount());
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            log.warn("Deposit would breach maximum balance: {} > {}", newBalance, MAX_BALANCE);
            throw new UnprocessableEntityException("Deposit would exceed the maximum account balance");
        }
        return newBalance;
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .reference(transaction.getReference())
                .userId(transaction.getUserId())
                .createdTimestamp(transaction.getCreatedTimestamp())
                .build();
    }
}
