package com.monicalab.board.dto;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import java.time.LocalDateTime;

public record BoardResponse(
        Long id,
        BoardType boardType,
        String title,
        String content,
        String thumbnail,
        String attachment,
        boolean isPublic,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(
                board.getId(),
                board.getBoardType(),
                board.getTitle(),
                board.getContent(),
                board.getThumbnail(),
                board.getAttachment(),
                board.isPublic(),
                board.getCreatedAt(),
                board.getUpdatedAt());
    }
}
