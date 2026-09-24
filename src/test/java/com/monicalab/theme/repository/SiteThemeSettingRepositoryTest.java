package com.monicalab.theme.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.theme.entity.AccentPreset;
import com.monicalab.theme.entity.SiteThemeSetting;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class SiteThemeSettingRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SiteThemeSettingRepository siteThemeSettingRepository;

    @BeforeEach
    void setUp() {
        siteThemeSettingRepository.deleteAll();
    }

    @Test
    void savedSettingRoundTripsAccentPresetAndVisibilityFlags() {
        SiteThemeSetting saved = siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(false)
                .showReviews(true)
                .showNotices(false)
                .showGallery(true)
                .build());

        Optional<SiteThemeSetting> found = siteThemeSettingRepository.findBySettingKey(SiteThemeSetting.SITE_THEME_KEY);

        assertThat(found).isPresent();
        SiteThemeSetting setting = found.get();
        assertThat(setting.getId()).isEqualTo(saved.getId());
        assertThat(setting.getAccentPreset()).isEqualTo(AccentPreset.TERRACOTTA);
        assertThat(setting.isShowPinned()).isTrue();
        assertThat(setting.isShowPrograms()).isFalse();
        assertThat(setting.isShowReviews()).isTrue();
        assertThat(setting.isShowNotices()).isFalse();
        assertThat(setting.isShowGallery()).isTrue();
    }

    @Test
    void findBySettingKeyReturnsEmptyWhenNoRowExists() {
        assertThat(siteThemeSettingRepository.findBySettingKey(SiteThemeSetting.SITE_THEME_KEY)).isEmpty();
    }

    @Test
    void savingDuplicateSettingKeyThrowsDataIntegrityViolationException() {
        siteThemeSettingRepository.saveAndFlush(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());

        SiteThemeSetting duplicate = SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(false)
                .showPrograms(false)
                .showReviews(false)
                .showNotices(false)
                .showGallery(false)
                .build();

        assertThatThrownBy(() -> siteThemeSettingRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
