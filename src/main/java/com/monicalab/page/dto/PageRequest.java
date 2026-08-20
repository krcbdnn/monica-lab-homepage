package com.monicalab.page.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PageRequest(
        @NotBlank @Size(max = 200) String title,
        String content) {
}
