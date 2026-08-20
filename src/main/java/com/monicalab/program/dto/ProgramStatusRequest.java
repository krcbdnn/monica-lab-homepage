package com.monicalab.program.dto;

import com.monicalab.program.entity.RecruitStatus;
import jakarta.validation.constraints.NotNull;

public record ProgramStatusRequest(@NotNull RecruitStatus recruitStatus) {
}
