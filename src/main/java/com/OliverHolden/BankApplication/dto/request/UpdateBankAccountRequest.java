package com.OliverHolden.BankApplication.dto.request;

import com.OliverHolden.BankApplication.model.AccountType;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBankAccountRequest {

    @Size(min = 1)
    private String name;

    private AccountType accountType;
}
