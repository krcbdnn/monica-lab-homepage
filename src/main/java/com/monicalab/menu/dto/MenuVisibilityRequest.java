package com.monicalab.menu.dto;

import jakarta.validation.constraints.NotNull;

public record MenuVisibilityRequest(@NotNull Boolean visible) {
}
