package com.kcic.connect.shared.api;

import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldErrorItem> fieldErrors;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<FieldErrorItem> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldErrorItem> fieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.RESOURCE_FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "Data tidak valid.",
                List.of(new FieldErrorItem(field, message)));
    }
}
