package com.monicalab.board.dto;

import com.monicalab.board.entity.BoardType;
import com.monicalab.program.entity.ProgramType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BoardRequest(
        @NotNull BoardType boardType,
        @NotBlank @Size(max = 200) String title,
        String content,
        @Size(max = 255) String thumbnail,
        @Size(max = 255) String attachment,
        @NotNull(groups = BoardRequest.OnUpdate.class) Boolean isPublic,
        ProgramType programType) {

    /**
     * PUT은 isPublic까지 필수(API.md 계약)이므로, POST(Default 그룹)와
     * 구분되는 validation group. 단일 BoardRequest DTO를 POST/PUT에 재사용하기 위함.
     */
    public interface OnUpdate {
    }
}
