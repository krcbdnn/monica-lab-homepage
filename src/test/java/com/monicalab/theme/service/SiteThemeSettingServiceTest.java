package com.monicalab.theme.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.theme.dto.SiteThemeSettingView;
import com.monicalab.theme.entity.AccentPreset;
import com.monicalab.theme.entity.SiteThemeSetting;
import com.monicalab.theme.repository.SiteThemeSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// getSetting()이 실제로 읽기 전용인지(= DB에 행이 없을 때 자동으로 만들어 저장하지 않는지)는
// Mockito verify(never())가 아니라 real repository.count()로 확인해야 신뢰할 수 있으므로, 이
// 테스트는 AbstractIntegrationTest를 확장해 실제 MariaDB Testcontainers 위에서 검증한다.
class SiteThemeSettingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SiteThemeSettingService siteThemeSettingService;

    @Autowired
    private SiteThemeSettingRepository siteThemeSettingRepository;

    @BeforeEach
    void setUp() {
        siteThemeSettingRepository.deleteAll();
    }

    @Test
    void returnsStoredSettingWhenRowExists() {
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(false)
                .showReviews(true)
                .showNotices(false)
                .showGallery(true)
                .build());

        SiteThemeSettingView view = siteThemeSettingService.getSetting();

        assertThat(view).isEqualTo(new SiteThemeSettingView(AccentPreset.TERRACOTTA, true, false, true, false, true));
    }

    @Test
    void returnsDefaultFallbackWhenRowIsMissing() {
        SiteThemeSettingView view = siteThemeSettingService.getSetting();

        assertThat(view).isEqualTo(SiteThemeSettingView.defaultValue());
        assertThat(view.accentPreset()).isEqualTo(AccentPreset.TERRACOTTA);
        assertThat(view.showPinned()).isTrue();
        assertThat(view.showPrograms()).isTrue();
        assertThat(view.showReviews()).isTrue();
        assertThat(view.showNotices()).isTrue();
        assertThat(view.showGallery()).isTrue();
    }

    @Test
    void fallbackReadDoesNotWriteToDatabase() {
        siteThemeSettingService.getSetting();

        assertThat(siteThemeSettingRepository.count()).isZero();
    }
}
