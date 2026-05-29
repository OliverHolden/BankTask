package com.OliverHolden.BankApplication.dto.response;

import com.OliverHolden.BankApplication.dto.AddressDto;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class UserResponse {

    private String id;
    private String name;
    private AddressDto address;
    private String phoneNumber;
    private String email;
    private OffsetDateTime createdTimestamp;
    private OffsetDateTime updatedTimestamp;
}
