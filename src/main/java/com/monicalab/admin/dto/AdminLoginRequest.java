package com.monicalab.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank String password) {
}
