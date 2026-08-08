package com.monicalab.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.monicalab.common.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorInfo error;

    private ApiResponse(boolean success, T data, ErrorInfo error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorInfo.of(errorCode, null));
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, List<FieldError> fields) {
        return new ApiResponse<>(false, null, ErrorInfo.of(errorCode, fields));
    }

    @Getter
    public static class ErrorInfo {

        private final String code;
        private final String message;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<FieldError> fields;

        private ErrorInfo(String code, String message, List<FieldError> fields) {
            this.code = code;
            this.message = message;
            this.fields = fields;
        }

        private static ErrorInfo of(ErrorCode errorCode, List<FieldError> fields) {
            return new ErrorInfo(errorCode.name(), errorCode.getMessage(), fields);
        }
    }

    @Getter
    public static class FieldError {

        private final String field;
        private final String reason;

        private FieldError(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }

        public static FieldError of(String field, String reason) {
            return new FieldError(field, reason);
        }
    }
}
