package com.monicalab.theme.dto;

import com.monicalab.theme.entity.AccentPreset;
import com.monicalab.theme.entity.SiteThemeSetting;

// SiteThemeSettingService의 반환 타입. DB에 설정 행이 아직 없을 때의 fallback을 저장되지 않은
// (id == null) SiteThemeSetting Entity로 반환하면, 호출 측이 "영속화된 값인지" 여부를 매번 별도로
// 확인해야 하는 persistence-state 혼동이 생긴다. 이 프로젝트에서 다른 도메인들도 Service가 Entity를
// 그대로 반환하지 않고 읽기 전용 값으로 변환해 돌려주는 관례(HomePinnedContentResponse 등)를 따르되,
// 아직 Controller/API 소비자가 없는 P14-T8A 단계에서는 "API Response DTO"가 아니라 순수한
// 도메인/서비스 read model로 정의한다.
public record SiteThemeSettingView(
        AccentPreset accentPreset,
        boolean showPinned,
        boolean showPrograms,
        boolean showReviews,
        boolean showNotices,
        boolean showGallery) {

    // P14-T3D가 구현한 홈 화면의 실제 기본 동작(포인트 컬러 없음/전 섹션 노출)과 값을 일치시킨
    // 단일 기준점이다. DB에 SITE_THEME 행이 없을 때만 사용되며, 이 fallback을 반환하는 것 자체는
    // 어떤 DB 쓰기도 유발하지 않는다("읽다가 없으면 만들어 저장" 방식은 채택하지 않는다).
    public static SiteThemeSettingView defaultValue() {
        return new SiteThemeSettingView(AccentPreset.TERRACOTTA, true, true, true, true, true);
    }

    public static SiteThemeSettingView from(SiteThemeSetting setting) {
        return new SiteThemeSettingView(
                setting.getAccentPreset(),
                setting.isShowPinned(),
                setting.isShowPrograms(),
                setting.isShowReviews(),
                setting.isShowNotices(),
                setting.isShowGallery());
    }
}
