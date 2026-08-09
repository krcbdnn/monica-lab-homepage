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
        RecruitStatus recruitStatus,
        Boolean isPublic) {
}
