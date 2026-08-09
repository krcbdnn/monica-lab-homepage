package com.monicalab.program.dto;

import com.monicalab.program.entity.ProgramType;

public record ProgramSearchCondition(ProgramType programType, String keyword, Boolean isPublic) {
}
