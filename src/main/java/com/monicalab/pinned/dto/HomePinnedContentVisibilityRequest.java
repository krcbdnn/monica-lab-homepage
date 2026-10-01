package com.monicalab.pinned.dto;

import jakarta.validation.constraints.NotNull;

public record HomePinnedContentVisibilityRequest(@NotNull Boolean visible) {
}
