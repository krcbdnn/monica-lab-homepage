package com.monicalab.banner.dto;

import com.monicalab.banner.entity.Banner;
import java.time.LocalDateTime;

public record BannerResponse(
        Long id,
        String title,
        String image,
        String linkUrl,
        int sortOrder,
        boolean isVisible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static BannerResponse from(Banner banner) {
        return new BannerResponse(
                banner.getId(),
                banner.getTitle(),
                banner.getImage(),
                banner.getLinkUrl(),
                banner.getSortOrder(),
                banner.isVisible(),
                banner.getCreatedAt(),
                banner.getUpdatedAt());
    }
}
