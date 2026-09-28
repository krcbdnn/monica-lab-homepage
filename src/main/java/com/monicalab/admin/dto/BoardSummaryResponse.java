package com.monicalab.admin.dto;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.BoardType;
import com.monicalab.program.entity.ProgramType;
import java.time.LocalDateTime;

// P14-T9E: programType은 REVIEW 하위유형(강의 후기(정규 강좌)/강의 후기(특강)) 표시를 위한 additive 필드다(REVIEW가 아니거나
// legacy 미지정이면 null). 본문(content)은 싣지 않는다.
public record BoardSummaryResponse(Long id, BoardType boardType, String title, boolean isPublic,
        LocalDateTime createdAt, ProgramType programType) {

    public static BoardSummaryResponse from(BoardResponse board) {
        return new BoardSummaryResponse(
                board.id(), board.boardType(), board.title(), board.isPublic(), board.createdAt(),
                board.programType());
    }
}
