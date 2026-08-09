package com.monicalab.program.dto;

import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ProgramRequest(
        @NotNull ProgramType programType,
        @NotBlank @Size(max = 200) String title,
        String content,
        @Size(max = 255) String thumbnail,
        @Size(max = 255) String attachment,
        @URL(regexp = "^$|^https?://.+", message = "googleFormUrl은 http 또는 https URL 형식이어야 합니다.")
        @Size(max = 500) String googleFormUrl,
        @NotNull(groups = ProgramRequest.OnUpdate.class) RecruitStatus recruitStatus,
        @NotNull(groups = ProgramRequest.OnUpdate.class) Boolean isPublic) {

    /**
     * PUT은 recruitStatus/isPublic까지 필수(API.md 계약)이므로, POST(Default 그룹)와
     * 구분되는 validation group. 단일 ProgramRequest DTO를 POST/PUT에 재사용하기 위함.
     */
    public interface OnUpdate {
    }
}
