package com.monicalab.theme.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.theme.dto.SiteThemeSettingRequest;
import com.monicalab.theme.dto.SiteThemeSettingView;
import com.monicalab.theme.service.SiteThemeSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// P14-T8B: Theme Settings는 SITE_THEME 하나뿐인 singleton이라 Page(/api/admin/pages/{pageType})와
// 달리 경로에 id/타입을 노출하지 않는다. Request에도 id/settingKey를 두지 않아 client가 singleton
// key를 조작할 여지 자체가 없다.
@RestController
@RequestMapping("/api/admin/theme")
@RequiredArgsConstructor
public class AdminThemeController {

    private final SiteThemeSettingService siteThemeSettingService;

    @GetMapping
    public ApiResponse<SiteThemeSettingView> get() {
        return ApiResponse.success(siteThemeSettingService.getSetting());
    }

    @PutMapping
    public ApiResponse<SiteThemeSettingView> update(@Valid @RequestBody SiteThemeSettingRequest request) {
        return ApiResponse.success(siteThemeSettingService.updateSetting(request));
    }
}
