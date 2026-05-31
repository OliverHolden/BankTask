package com.OliverHolden.BankApplication.repository;

import com.OliverHolden.BankApplication.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findAllByAccountNumberOrderByCreatedTimestampDesc(String accountNumber);

    Optional<Transaction> findByIdAndAccountNumber(String id, String accountNumber);
}
