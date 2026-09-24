package com.monicalab.theme.service;

import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.theme.dto.SiteThemeSettingRequest;
import com.monicalab.theme.dto.SiteThemeSettingView;
import com.monicalab.theme.entity.SiteThemeSetting;
import com.monicalab.theme.repository.SiteThemeSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiteThemeSettingService {

    private final SiteThemeSettingRepository repository;

    @Transactional(readOnly = true)
    public SiteThemeSettingView getSetting() {
        return repository.findBySettingKey(SiteThemeSetting.SITE_THEME_KEY)
                .map(SiteThemeSettingView::from)
                .orElseGet(SiteThemeSettingView::defaultValue);
    }

    // P14-T8B: SITE_THEME row 생성 책임은 V13 마이그레이션 시드에만 있다 - 정상 환경이라면 이 row는
    // 항상 존재하므로, CmsPage/PageService.update()와 동일하게 row가 없으면 upsert/create하지 않고
    // 404(SITE_THEME_SETTING_NOT_FOUND)로 처리한다. read fallback(getSetting())과 달리 이 메서드는
    // DB에 쓰기가 필요한 경로이므로 둘의 "row 없음" 처리를 의도적으로 다르게 유지한다.
    @Transactional
    public SiteThemeSettingView updateSetting(SiteThemeSettingRequest request) {
        SiteThemeSetting setting = repository.findBySettingKey(SiteThemeSetting.SITE_THEME_KEY)
                .orElseThrow(() -> new CustomException(ErrorCode.SITE_THEME_SETTING_NOT_FOUND));
        setting.update(request.accentPreset(), request.showPinned(), request.showPrograms(),
                request.showReviews(), request.showNotices(), request.showGallery());
        return SiteThemeSettingView.from(setting);
    }
}
