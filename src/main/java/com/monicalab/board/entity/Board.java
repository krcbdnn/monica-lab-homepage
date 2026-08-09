package com.monicalab.board.entity;

import com.monicalab.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "board")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "thumbnail", length = 255)
    private String thumbnail;

    @Column(name = "attachment", length = 255)
    private String attachment;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Builder
    private Board(BoardType boardType, String title, String content, String thumbnail, String attachment,
            int viewCount, boolean isPublic) {
        this.boardType = boardType;
        this.title = title;
        this.content = content;
        this.thumbnail = thumbnail;
        this.attachment = attachment;
        this.viewCount = viewCount;
        this.isPublic = isPublic;
    }
}
