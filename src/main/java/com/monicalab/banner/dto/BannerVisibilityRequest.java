package com.monicalab.banner.dto;

import jakarta.validation.constraints.NotNull;

public record BannerVisibilityRequest(@NotNull Boolean isVisible) {
}
