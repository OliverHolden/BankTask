package com.OliverHolden.BankApplication.dto.request;

import com.OliverHolden.BankApplication.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBankAccountRequest {

    @NotBlank
    private String name;

    @NotNull
    private AccountType accountType;
}
