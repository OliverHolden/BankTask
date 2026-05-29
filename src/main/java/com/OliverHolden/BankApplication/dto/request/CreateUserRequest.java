package com.OliverHolden.BankApplication.dto.request;

import com.OliverHolden.BankApplication.dto.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank
    private String name;

    @NotNull
    @Valid
    private AddressDto address;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$")
    private String phoneNumber;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
