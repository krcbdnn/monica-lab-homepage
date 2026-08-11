package com.monicalab.board.dto;

import com.monicalab.board.entity.BoardType;

public record BoardSearchCondition(BoardType boardType, String keyword, Boolean isPublic) {
}
