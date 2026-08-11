package com.monicalab.popup.dto;

import com.monicalab.popup.entity.Popup;
import java.time.LocalDateTime;

public record PopupResponse(
        Long id,
        String title,
        String content,
        LocalDateTime startDate,
        LocalDateTime endDate,
        boolean isVisible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PopupResponse from(Popup popup) {
        return new PopupResponse(
                popup.getId(),
                popup.getTitle(),
                popup.getContent(),
                popup.getStartDate(),
                popup.getEndDate(),
                popup.isVisible(),
                popup.getCreatedAt(),
                popup.getUpdatedAt());
    }
}
