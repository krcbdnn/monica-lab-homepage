package com.monicalab.board.dto;

import com.monicalab.board.entity.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BoardRequest(
        @NotNull BoardType boardType,
        @NotBlank @Size(max = 200) String title,
        String content,
        @Size(max = 255) String thumbnail,
        @Size(max = 255) String attachment,
        Boolean isPublic) {
}
