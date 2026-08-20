package com.monicalab.popup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PopupRequest(
        @NotBlank @Size(max = 100) String title,
        String content,
        @NotNull LocalDateTime startDate,
        @NotNull LocalDateTime endDate,
        @NotNull(groups = PopupRequest.OnUpdate.class) Boolean isVisible) {

    @AssertTrue(message = "startDate는 endDate보다 이후일 수 없습니다.")
    public boolean isValidDateRange() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    /**
     * PUT은 isVisible까지 필수(API.md 계약)이므로, POST(Default 그룹)와
     * 구분되는 validation group. 단일 PopupRequest DTO를 POST/PUT에 재사용하기 위함.
     */
    public interface OnUpdate {
    }
}
