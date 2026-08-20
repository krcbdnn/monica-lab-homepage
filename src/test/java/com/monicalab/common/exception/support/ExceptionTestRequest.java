package com.monicalab.common.exception.support;

import jakarta.validation.constraints.NotBlank;

public record ExceptionTestRequest(@NotBlank String name) {
}
