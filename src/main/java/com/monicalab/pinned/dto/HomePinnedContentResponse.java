package com.monicalab.pinned.dto;

import com.monicalab.pinned.entity.HomePinnedContent;
import com.monicalab.pinned.entity.HomeTargetType;
import java.time.LocalDateTime;

public record HomePinnedContentResponse(
        Long id,
        HomeTargetType targetType,
        Long targetId,
        int sortOrder,
        boolean visible,
        SourceStatus sourceStatus,
        String sourceTitle,
        String sourceUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static HomePinnedContentResponse of(HomePinnedContent pin, SourceStatus sourceStatus,
            String sourceTitle, String sourceUrl) {
        return new HomePinnedContentResponse(
                pin.getId(),
                pin.getTargetType(),
                pin.getTargetId(),
                pin.getSortOrder(),
                pin.isVisible(),
                sourceStatus,
                sourceTitle,
                sourceUrl,
                pin.getCreatedAt(),
                pin.getUpdatedAt());
    }
}
