package com.monicalab.theme.entity;

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
@Table(name = "site_theme_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteThemeSetting extends BaseEntity {

    // 이 테이블은 여러 종류의 설정을 담는 범용 key-value 저장소가 아니라 "SITE_THEME"라는 단일
    // 논리적 설정 그룹 1건만 다루도록 설계했다. settingKey는 Controller나 Request DTO 등 어떤
    // 외부 입력 경로로도 받지 않고, 이 상수를 통해서만 참조한다(P14-T8A 기준 이 값을 쓰는 유일한
    // 코드는 SiteThemeSettingService, 그리고 테스트 코드뿐이다). DB의 UNIQUE(setting_key) 제약은
    // 같은 key의 중복 저장만 막을 뿐 테이블 전체가 물리적으로 1행만 가질 수 있다는 것을 보장하지는
    // 않는다 - 이 프로젝트 규모에서는 CHECK 제약이나 고정 PK 같은 추가 장치 없이, "Java 코드가
    // settingKey를 외부 입력으로 받지 않는다"는 사실만으로 충분한 안전장치로 판단했다.
    public static final String SITE_THEME_KEY = "SITE_THEME";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 50)
    private String settingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "accent_preset", nullable = false, length = 20)
    private AccentPreset accentPreset;

    @Column(name = "show_pinned", nullable = false)
    private boolean showPinned;

    @Column(name = "show_programs", nullable = false)
    private boolean showPrograms;

    @Column(name = "show_reviews", nullable = false)
    private boolean showReviews;

    @Column(name = "show_notices", nullable = false)
    private boolean showNotices;

    @Column(name = "show_gallery", nullable = false)
    private boolean showGallery;

    @Builder
    private SiteThemeSetting(String settingKey, AccentPreset accentPreset, boolean showPinned,
            boolean showPrograms, boolean showReviews, boolean showNotices, boolean showGallery) {
        this.settingKey = settingKey;
        this.accentPreset = accentPreset;
        this.showPinned = showPinned;
        this.showPrograms = showPrograms;
        this.showReviews = showReviews;
        this.showNotices = showNotices;
        this.showGallery = showGallery;
    }

    // P14-T8B: Admin이 저장할 수 있는 6개 값만 변경한다. id/settingKey/createdAt/updatedAt은
    // 이 메서드로 손댈 수 없다 - settingKey는 계속 불변 논리 singleton key이고, createdAt/updatedAt은
    // BaseEntity의 auditing이 전담한다. CmsPage.update(title, content)와 동일한 필드 재대입 방식이며
    // public setter는 추가하지 않는다.
    public void update(AccentPreset accentPreset, boolean showPinned, boolean showPrograms,
            boolean showReviews, boolean showNotices, boolean showGallery) {
        this.accentPreset = accentPreset;
        this.showPinned = showPinned;
        this.showPrograms = showPrograms;
        this.showReviews = showReviews;
        this.showNotices = showNotices;
        this.showGallery = showGallery;
    }
}
