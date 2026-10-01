package com.monicalab.menu.dto;

import com.monicalab.menu.entity.Menu;
import com.monicalab.menu.entity.MenuTargetType;
import java.time.LocalDateTime;

public record MenuResponse(
        Long id,
        String label,
        Long parentId,
        MenuTargetType targetType,
        String targetValue,
        String targetSubvalue,
        int sortOrder,
        boolean visible,
        boolean openInNewTab,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getLabel(),
                menu.getParentId(),
                menu.getTargetType(),
                menu.getTargetValue(),
                menu.getTargetSubvalue(),
                menu.getSortOrder(),
                menu.isVisible(),
                menu.isOpenInNewTab(),
                menu.getCreatedAt(),
                menu.getUpdatedAt());
    }
}
