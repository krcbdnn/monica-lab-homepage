package com.monicalab.banner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record BannerRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 255) String image,
        @URL(regexp = "^$|^https?://.+", message = "linkUrl은 http 또는 https URL 형식이어야 합니다.")
        @Size(max = 500) String linkUrl,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull(groups = BannerRequest.OnUpdate.class) Boolean isVisible) {

    /**
     * PUT은 isVisible까지 필수(API.md 계약)이므로, POST(Default 그룹)와
     * 구분되는 validation group. 단일 BannerRequest DTO를 POST/PUT에 재사용하기 위함.
     */
    public interface OnUpdate {
    }
}
