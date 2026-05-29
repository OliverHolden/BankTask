package com.OliverHolden.BankApplication.dto.request;

import com.OliverHolden.BankApplication.dto.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {

    private String name;

    @Valid
    private AddressDto address;

    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$")
    private String phoneNumber;

    @Email
    private String email;
}
