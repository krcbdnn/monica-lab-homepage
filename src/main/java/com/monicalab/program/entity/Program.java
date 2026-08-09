package com.monicalab.program.entity;

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
@Table(name = "program")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false, length = 20)
    private ProgramType programType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "thumbnail", length = 255)
    private String thumbnail;

    @Column(name = "attachment", length = 255)
    private String attachment;

    @Column(name = "google_form_url", length = 500)
    private String googleFormUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "recruit_status", nullable = false, length = 20)
    private RecruitStatus recruitStatus;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Builder
    private Program(ProgramType programType, String title, String content, String thumbnail, String attachment,
            String googleFormUrl, RecruitStatus recruitStatus, boolean isPublic) {
        this.programType = programType;
        this.title = title;
        this.content = content;
        this.thumbnail = thumbnail;
        this.attachment = attachment;
        this.googleFormUrl = googleFormUrl;
        this.recruitStatus = recruitStatus;
        this.isPublic = isPublic;
    }
}
