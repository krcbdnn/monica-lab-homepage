package com.monicalab.theme.dto;

import com.monicalab.theme.entity.AccentPreset;
import jakarta.validation.constraints.NotNull;

// P14-T8B: id/settingKey/createdAt/updatedAt은 절대 받지 않는다 - SITE_THEME key는 서버 내부
// 상수(SiteThemeSetting.SITE_THEME_KEY)로만 참조되고, client가 이를 조작할 수 있는 입력 경로 자체를
// 두지 않는다. boolean은 primitive가 아니라 boxed Boolean + @NotNull로 선언해, JSON 필드 누락이
// Jackson에 의해 false로 조용히 바인딩되는 것을 막고 명시적으로 400 INVALID_INPUT_VALUE가 되도록 한다
// (HomePinnedContentVisibilityRequest와 동일한 관례).
public record SiteThemeSettingRequest(
        @NotNull AccentPreset accentPreset,
        @NotNull Boolean showPinned,
        @NotNull Boolean showPrograms,
        @NotNull Boolean showReviews,
        @NotNull Boolean showNotices,
        @NotNull Boolean showGallery) {
}
