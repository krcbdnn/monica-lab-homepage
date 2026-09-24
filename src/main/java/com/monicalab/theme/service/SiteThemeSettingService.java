package com.monicalab.theme.service;

import com.monicalab.theme.dto.SiteThemeSettingView;
import com.monicalab.theme.entity.SiteThemeSetting;
import com.monicalab.theme.repository.SiteThemeSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// P14-T8A는 읽기 전용 도메인 기반만 제공한다. 설정을 만들거나 바꾸는 API(updateSetting 등)는
// 아직 이를 사용할 Controller/Request DTO가 없으므로 YAGNI 원칙에 따라 이번 단계에서는 추가하지
// 않고, Admin API와 함께 도입하는 P14-T8B로 미룬다.
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
}
