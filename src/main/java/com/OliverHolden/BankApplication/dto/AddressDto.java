package com.OliverHolden.BankApplication.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {

    @NotBlank
    private String line1;
    private String line2;
    private String line3;
    @NotBlank
    private String town;
    @NotBlank
    private String county;
    @NotBlank
    private String postcode;
}
