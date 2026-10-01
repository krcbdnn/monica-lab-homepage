package com.monicalab.pinned.dto;

import com.monicalab.pinned.entity.HomeTargetType;

public record HomePinnedContentPublicResponse(
        Long pinnedId,
        HomeTargetType targetType,
        Long targetId,
        String title,
        String thumbnail,
        String href) {
}
