package com.monicalab.board.entity;

import com.monicalab.common.entity.BaseEntity;
import com.monicalab.program.entity.ProgramType;
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

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    // P13-T30D(Task C): boardType=REVIEW일 때만 의미를 갖는 subtype(강의 후기/특강 후기 구분).
    // 별도 ReviewType enum이나 별도 Review entity를 만들지 않고 program.ProgramType을 재사용한다.
    // REVIEW가 아닌 boardType에서는 항상 NULL이어야 하며, 이는 BoardService가 강제한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", length = 20)
    private ProgramType programType;

    @Builder
    private Board(BoardType boardType, String title, String content, String thumbnail, String attachment,
            boolean isPublic, ProgramType programType) {
        this.boardType = boardType;
        this.title = title;
        this.content = content;
        this.thumbnail = thumbnail;
        this.attachment = attachment;
        this.isPublic = isPublic;
        this.programType = programType;
    }

    public void update(BoardType boardType, String title, String content, String thumbnail, String attachment,
            boolean isPublic, ProgramType programType) {
        this.boardType = boardType;
        this.title = title;
        this.content = content;
        this.thumbnail = thumbnail;
        this.attachment = attachment;
        this.isPublic = isPublic;
        this.programType = programType;
    }

    public void updateVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }
}
