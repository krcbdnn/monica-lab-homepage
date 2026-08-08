package com.monicalab.common.exception;

import com.monicalab.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.warn("CustomException: {}", e.getErrorCode(), e);
        return response(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<ApiResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ApiResponse.FieldError.of(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        log.warn("Validation failed: {}", fields);
        return response(ErrorCode.INVALID_INPUT_VALUE, fields);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        List<ApiResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ApiResponse.FieldError.of(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        log.warn("Validation failed: {}", fields);
        return response(ErrorCode.INVALID_INPUT_VALUE, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return response(ErrorCode.INVALID_INPUT_VALUE);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        log.warn("No handler found: {}", e.getMessage());
        return response(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode));
    }

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode, List<ApiResponse.FieldError> fields) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(errorCode, fields));
    }
}
