package com.monicalab.board.dto;

import jakarta.validation.constraints.NotNull;

public record BoardVisibilityRequest(@NotNull Boolean isPublic) {
}
