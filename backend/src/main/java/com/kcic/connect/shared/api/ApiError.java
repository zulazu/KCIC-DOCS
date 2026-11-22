package com.kcic.connect.shared.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldErrorItem> fieldErrors, String requestId) {

    public static ApiError of(String code, String message, List<FieldErrorItem> fieldErrors, String requestId) {
        return new ApiError(code, message, fieldErrors == null ? List.of() : fieldErrors, requestId);
    }
}
