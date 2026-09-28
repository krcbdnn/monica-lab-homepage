package com.monicalab.admin.dto;

import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import java.time.LocalDateTime;

// P14-T9E: dashboard 최근 프로그램 항목. 목록/상세 이동과 badge 표시에 필요한 필드만 싣고 본문(content)/파일 URL은 싣지 않는다.
public record ProgramSummaryResponse(Long id, ProgramType programType, String title, RecruitStatus recruitStatus,
        boolean isPublic, LocalDateTime createdAt) {

    public static ProgramSummaryResponse from(ProgramResponse program) {
        return new ProgramSummaryResponse(
                program.id(), program.programType(), program.title(), program.recruitStatus(), program.isPublic(),
                program.createdAt());
    }
}
