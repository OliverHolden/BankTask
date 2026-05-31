package com.OliverHolden.BankApplication.dto.response;

import com.OliverHolden.BankApplication.model.Currency;
import com.OliverHolden.BankApplication.model.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class TransactionResponse {

    private String id;
    private BigDecimal amount;
    private Currency currency;
    private TransactionType type;
    private String reference;
    private String userId;
    private OffsetDateTime createdTimestamp;
}
