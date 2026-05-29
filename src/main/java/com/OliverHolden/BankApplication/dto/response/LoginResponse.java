package com.OliverHolden.BankApplication.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class LoginResponse {

    private String token;
    private String tokenType;
    private OffsetDateTime expiresAt;
}
