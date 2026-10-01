package com.monicalab.board.dto;

import com.monicalab.board.entity.BoardType;
import com.monicalab.program.entity.ProgramType;

public record BoardSearchCondition(BoardType boardType, ProgramType programType, String keyword, Boolean isPublic) {
}
