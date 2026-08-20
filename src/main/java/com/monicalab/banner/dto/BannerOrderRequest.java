package com.monicalab.banner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BannerOrderRequest(@NotNull @Min(0) Integer sortOrder) {
}
