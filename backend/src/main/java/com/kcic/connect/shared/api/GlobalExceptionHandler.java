package com.kcic.connect.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.kcic.connect.shared.web.RequestIdFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.code(), ex.getMessage(), ex.fieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorItem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), safeMessage(error)))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Data tidak valid.", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorItem> fields = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Data tidak valid.", fields, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "Permintaan tidak valid.", List.of(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(ErrorCode.FILE_TOO_LARGE, "Ukuran file melebihi batas yang diizinkan.", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation requestId={}", RequestIdFilter.currentRequestId(request));
        return build(ErrorCode.CONFLICT, "Data bertentangan dengan aturan yang berlaku atau sudah digunakan.",
                List.of(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                        HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, "Data telah diubah oleh pengguna lain. Muat ulang lalu coba lagi.",
                List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.ACTION_FORBIDDEN, "Anda tidak memiliki izin untuk tindakan ini.", List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, "Sesi tidak valid atau telah berakhir.", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Resource tidak ditemukan.", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error requestId={}", RequestIdFilter.currentRequestId(request), ex);
        return build(ErrorCode.INTERNAL_ERROR, "Terjadi kesalahan internal. Hubungi administrator.", List.of(), request);
    }

    private static String safeMessage(FieldError error) {
        return error.getDefaultMessage() == null ? "Nilai tidak valid." : error.getDefaultMessage();
    }

    private static ResponseEntity<ApiError> build(ErrorCode code, String message, List<FieldErrorItem> fields,
                                                  HttpServletRequest request) {
        HttpStatus status = code.status();
        ApiError error = ApiError.of(code.name(), message, fields, RequestIdFilter.currentRequestId(request));
        return ResponseEntity.status(status).body(error);
    }
}
