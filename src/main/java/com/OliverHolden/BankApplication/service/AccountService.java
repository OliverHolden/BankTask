package com.OliverHolden.BankApplication.service;

import com.OliverHolden.BankApplication.dto.request.CreateBankAccountRequest;
import com.OliverHolden.BankApplication.dto.request.UpdateBankAccountRequest;
import com.OliverHolden.BankApplication.dto.response.BankAccountResponse;
import com.OliverHolden.BankApplication.dto.response.ListBankAccountsResponse;
import com.OliverHolden.BankApplication.exception.ConflictException;
import com.OliverHolden.BankApplication.exception.ForbiddenException;
import com.OliverHolden.BankApplication.exception.NotFoundException;
import com.OliverHolden.BankApplication.model.Account;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String SORT_CODE = "10-10-10";
    private static final String CURRENCY  = "GBP";

    private final AccountRepository accountRepository;

    @Transactional
    public BankAccountResponse createAccount(CreateBankAccountRequest request, String userId) {
        log.info("Creating account for user: {}", userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String accountNumber = generateAccountNumber();
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .userId(userId)
                .sortCode(SORT_CODE)
                .name(request.getName())
                .accountType(request.getAccountType())
                .balance(BigDecimal.ZERO)
                .currency(CURRENCY)
                .createdTimestamp(now)
                .updatedTimestamp(now)
                .build();
        try {
            return toResponse(accountRepository.save(account));
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation saving account", e);
            throw new ConflictException("Account creation failed due to a data conflict");
        }
    }

    public ListBankAccountsResponse listAccounts(String userId) {
        log.info("Listing accounts for user: {}", userId);
        List<BankAccountResponse> accounts = accountRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ListBankAccountsResponse.builder().accounts(accounts).build();
    }

    public BankAccountResponse getAccount(String accountNumber, String userId) {
        log.info("Fetching account: {}", accountNumber);
        Account account = accountRepository.findById(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new NotFoundException("Account not found");
                });
        if (!account.getUserId().equals(userId)) {
            log.warn("Forbidden: user {} attempted to access account {}", userId, accountNumber);
            throw new ForbiddenException("Access denied");
        }
        return toResponse(account);
    }

    @Transactional
    public BankAccountResponse updateAccount(String accountNumber, UpdateBankAccountRequest request, String userId) {
        log.info("Updating account: {}", accountNumber);
        Account account = accountRepository.findById(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new NotFoundException("Account not found");
                });
        if (!account.getUserId().equals(userId)) {
            log.warn("Forbidden: user {} attempted to update account {}", userId, accountNumber);
            throw new ForbiddenException("Access denied");
        }
        if (request.getName() != null) account.setName(request.getName());
        if (request.getAccountType() != null) account.setAccountType(request.getAccountType());
        account.setUpdatedTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(String accountNumber, String userId) {
        log.info("Deleting account: {}", accountNumber);
        Account account = accountRepository.findById(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new NotFoundException("Account not found");
                });
        if (!account.getUserId().equals(userId)) {
            log.warn("Forbidden: user {} attempted to delete account {}", userId, accountNumber);
            throw new ForbiddenException("Access denied");
        }
        accountRepository.deleteById(accountNumber);
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            int suffix = ThreadLocalRandom.current().nextInt(1_000_000);
            accountNumber = String.format("01%06d", suffix);
        } while (accountRepository.existsById(accountNumber));
        return accountNumber;
    }

    private BankAccountResponse toResponse(Account account) {
        return BankAccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .sortCode(account.getSortCode())
                .name(account.getName())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdTimestamp(account.getCreatedTimestamp())
                .updatedTimestamp(account.getUpdatedTimestamp())
                .build();
    }
}
