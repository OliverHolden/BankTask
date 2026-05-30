package com.OliverHolden.BankApplication.utility;

import com.OliverHolden.BankApplication.dto.AddressDto;
import com.OliverHolden.BankApplication.model.Address;

public final class AddressMapper {

    private AddressMapper() {}

    public static Address toEntity(AddressDto dto) {
        if (dto == null) return null;
        return Address.builder()
                .line1(dto.getLine1())
                .line2(dto.getLine2())
                .line3(dto.getLine3())
                .town(dto.getTown())
                .county(dto.getCounty())
                .postcode(dto.getPostcode())
                .build();
    }

    public static AddressDto toDto(Address address) {
        if (address == null) return null;
        return AddressDto.builder()
                .line1(address.getLine1())
                .line2(address.getLine2())
                .line3(address.getLine3())
                .town(address.getTown())
                .county(address.getCounty())
                .postcode(address.getPostcode())
                .build();
    }
}
