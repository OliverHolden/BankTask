package com.OliverHolden.BankApplication.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListTransactionsResponse {

    private List<TransactionResponse> transactions;
}
