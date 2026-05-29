package com.OliverHolden.BankApplication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BadRequestErrorResponse {

    private String message;
    private List<FieldError> details;

    @Data
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private String type;
    }
}
