package com.monicalab.common.exception;

import com.monicalab.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String API_PATH_PREFIX = "/api/";

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        int status = errorCode.getHttpStatus().value();
        // P15-T1: 5xx(서버 장애)만 stacktrace와 함께 ERROR로 남기고, 그 외(4xx 등 클라이언트 요청 문제)는
        // stacktrace 없는 WARN 한 줄로 남긴다. 요청 경로는 Nginx access log가 담당한다.
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("CustomException: code={}, status={}", errorCode, status, e);
        } else {
            log.warn("CustomException: code={}, status={}", errorCode, status);
        }
        return response(errorCode);
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

    // P15-T5: 미매핑 경로는 Accept header가 아니라 요청 경로로 나눈다. /api/로 시작하면 기존 JSON을 그대로,
    // 그 외(공개 주소, /admin/ 미매핑 주소 등)는 HTML 404 오류 페이지로 응답한다. @RestControllerAdvice여도
    // 실제 반환값이 ModelAndView면 ModelAndView return value handler가 먼저 선택되어 view로 렌더링된다.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception e, HttpServletRequest request) {
        log.warn("No handler found: {}", e.getMessage());
        if (request.getRequestURI().startsWith(API_PATH_PREFIX)) {
            return response(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return PublicViewExceptionHandler.errorPage(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return response(ErrorCode.INVALID_INPUT_VALUE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return response(ErrorCode.FILE_SIZE_EXCEEDED);
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
