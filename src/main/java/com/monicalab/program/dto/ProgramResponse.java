package com.monicalab.program.dto;

import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import java.time.LocalDateTime;

public record ProgramResponse(
        Long id,
        ProgramType programType,
        String title,
        String content,
        String thumbnail,
        String attachment,
        String googleFormUrl,
        RecruitStatus recruitStatus,
        boolean isPublic,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProgramResponse from(Program program) {
        return new ProgramResponse(
                program.getId(),
                program.getProgramType(),
                program.getTitle(),
                program.getContent(),
                program.getThumbnail(),
                program.getAttachment(),
                program.getGoogleFormUrl(),
                program.getRecruitStatus(),
                program.isPublic(),
                program.getCreatedAt(),
                program.getUpdatedAt());
    }
}
