package com.monicalab.menu.dto;

import com.monicalab.menu.entity.MenuTargetType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MenuRequest(
        @NotBlank @Size(max = 50) String label,
        Long parentId,
        @NotNull MenuTargetType targetType,
        @Size(max = 255) String targetValue,
        @Size(max = 50) String targetSubvalue,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull(groups = MenuRequest.OnUpdate.class) Boolean visible,
        @NotNull(groups = MenuRequest.OnUpdate.class) Boolean openInNewTab) {

    /**
     * PUT은 visible/openInNewTab까지 필수(Banner/Board/Program/Popup과 동일한 기존 계약)이므로,
     * POST(Default 그룹)와 구분되는 validation group. 단일 MenuRequest DTO를 POST/PUT에 재사용하기 위함.
     */
    public interface OnUpdate {
    }
}
