package com.monicalab.pinned.dto;

import com.monicalab.pinned.entity.HomeTargetType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HomePinnedContentRequest(
        @NotNull HomeTargetType targetType,
        @NotNull Long targetId,
        @NotNull @Min(0) Integer sortOrder,
        Boolean visible) {
}
