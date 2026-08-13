package com.monicalab.admin.dto;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.BoardType;
import java.time.LocalDateTime;

public record BoardSummaryResponse(Long id, BoardType boardType, String title, boolean isPublic,
        LocalDateTime createdAt) {

    public static BoardSummaryResponse from(BoardResponse board) {
        return new BoardSummaryResponse(
                board.id(), board.boardType(), board.title(), board.isPublic(), board.createdAt());
    }
}
