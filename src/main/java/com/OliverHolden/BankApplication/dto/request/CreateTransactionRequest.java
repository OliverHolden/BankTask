package com.OliverHolden.BankApplication.dto.request;

import com.OliverHolden.BankApplication.model.Currency;
import com.OliverHolden.BankApplication.model.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTransactionRequest {

    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "10000.00")
    @Digits(integer = 5, fraction = 2)
    private BigDecimal amount;

    @NotNull
    private Currency currency;

    @NotNull
    private TransactionType type;

    private String reference;
}
