package com.monicalab.popup.entity;

import com.monicalab.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "popup")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible;

    @Builder
    private Popup(String title, String content, LocalDateTime startDate, LocalDateTime endDate,
            boolean isVisible) {
        this.title = title;
        this.content = content;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isVisible = isVisible;
    }

    public void update(String title, String content, LocalDateTime startDate, LocalDateTime endDate,
            boolean isVisible) {
        this.title = title;
        this.content = content;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isVisible = isVisible;
    }

    public void updateVisibility(boolean isVisible) {
        this.isVisible = isVisible;
    }
}
