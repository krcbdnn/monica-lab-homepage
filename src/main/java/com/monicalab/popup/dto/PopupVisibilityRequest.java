package com.monicalab.popup.dto;

import jakarta.validation.constraints.NotNull;

public record PopupVisibilityRequest(@NotNull Boolean isVisible) {
}
