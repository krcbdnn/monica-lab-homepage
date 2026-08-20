package com.monicalab.program.dto;

import jakarta.validation.constraints.NotNull;

public record ProgramVisibilityRequest(@NotNull Boolean isPublic) {
}
