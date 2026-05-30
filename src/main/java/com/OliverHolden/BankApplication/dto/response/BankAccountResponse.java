package com.OliverHolden.BankApplication.dto.response;

import com.OliverHolden.BankApplication.model.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class BankAccountResponse {

    private String accountNumber;
    private String sortCode;
    private String name;
    private AccountType accountType;
    private BigDecimal balance;
    private String currency;
    private OffsetDateTime createdTimestamp;
    private OffsetDateTime updatedTimestamp;
}
