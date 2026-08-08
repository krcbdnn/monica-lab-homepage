package com.monicalab.common.exception.support;

import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExceptionTestController {

    @GetMapping("/test/exceptions/ok")
    public ApiResponse<String> ok() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/test/exceptions/custom")
    public ApiResponse<Void> custom() {
        throw new CustomException(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @GetMapping("/test/exceptions/unhandled")
    public ApiResponse<Void> unhandled() {
        throw new IllegalStateException("boom");
    }

    @PostMapping("/test/exceptions/validate")
    public ApiResponse<Void> validate(@Valid @RequestBody ExceptionTestRequest request) {
        return ApiResponse.success(null);
    }
}
