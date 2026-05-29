package com.OliverHolden.BankApplication.repository;

import com.OliverHolden.BankApplication.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {

    boolean existsByUserId(String userId);
}
