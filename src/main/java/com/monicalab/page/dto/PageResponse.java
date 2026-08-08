package com.monicalab.page.dto;

import com.monicalab.page.entity.CmsPage;
import com.monicalab.page.entity.PageType;
import java.time.LocalDateTime;

public record PageResponse(
        Long id,
        PageType pageType,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PageResponse from(CmsPage page) {
        return new PageResponse(
                page.getId(),
                page.getPageType(),
                page.getTitle(),
                page.getContent(),
                page.getCreatedAt(),
                page.getUpdatedAt());
    }
}
