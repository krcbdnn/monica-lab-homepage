package com.monicalab.theme.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.theme.dto.SiteThemeSettingRequest;
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

    @Test
    void updateSettingPersistsNewAccentPresetAndVisibilityFlags() {
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());

        SiteThemeSettingRequest request = new SiteThemeSettingRequest(
                AccentPreset.TERRACOTTA, false, true, false, true, false);

        SiteThemeSettingView view = siteThemeSettingService.updateSetting(request);

        assertThat(view).isEqualTo(new SiteThemeSettingView(AccentPreset.TERRACOTTA, false, true, false, true, false));
        assertThat(siteThemeSettingRepository.count()).isEqualTo(1);
        assertThat(siteThemeSettingService.getSetting())
                .isEqualTo(new SiteThemeSettingView(AccentPreset.TERRACOTTA, false, true, false, true, false));
    }

    // SITE_THEME row 생성 책임은 V13 마이그레이션 시드에만 있다 - updateSetting()은 CmsPage/PageService와
    // 동일하게 row가 없으면 upsert/create하지 않고 404로 처리해야 하며, 이 정책을 회귀시키지 않도록
    // repository.count()가 여전히 0임을 함께 고정한다.
    @Test
    void updateSettingWhenRowMissingThrowsNotFoundAndCreatesNoRow() {
        SiteThemeSettingRequest request = new SiteThemeSettingRequest(
                AccentPreset.TERRACOTTA, true, true, true, true, true);

        assertThatThrownBy(() -> siteThemeSettingService.updateSetting(request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SITE_THEME_SETTING_NOT_FOUND));

        assertThat(siteThemeSettingRepository.count()).isZero();
    }

    // P14-T8C: AccentPreset이 3개로 늘어난 뒤에도 저장/조회가 정상 동작하는지 확인한다. row 없음 시
    // fallback이 여전히 TERRACOTTA+0-write임을 고정하는 위 두 테스트(returnsDefaultFallbackWhenRowIsMissing/
    // fallbackReadDoesNotWriteToDatabase)는 이미 이 계약을 보호하고 있어 별도로 반복하지 않는다.
    @Test
    void updateSettingPersistsBurgundyAccentPreset() {
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());

        siteThemeSettingService.updateSetting(
                new SiteThemeSettingRequest(AccentPreset.BURGUNDY, true, true, true, true, true));

        assertThat(siteThemeSettingService.getSetting().accentPreset()).isEqualTo(AccentPreset.BURGUNDY);
    }

    @Test
    void updateSettingPersistsForestAccentPreset() {
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());

        siteThemeSettingService.updateSetting(
                new SiteThemeSettingRequest(AccentPreset.FOREST, true, true, true, true, true));

        assertThat(siteThemeSettingService.getSetting().accentPreset()).isEqualTo(AccentPreset.FOREST);
    }
}
