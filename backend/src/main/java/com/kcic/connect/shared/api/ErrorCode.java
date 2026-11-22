package com.kcic.connect.shared.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    RESOURCE_FORBIDDEN(HttpStatus.FORBIDDEN),
    ACTION_FORBIDDEN(HttpStatus.FORBIDDEN),
    CSRF(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
