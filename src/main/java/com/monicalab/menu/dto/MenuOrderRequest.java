package com.monicalab.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MenuOrderRequest(@NotNull @Min(0) Integer sortOrder) {
}
