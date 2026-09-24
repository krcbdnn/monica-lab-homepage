package com.monicalab.theme.controller;

import com.monicalab.board.controller.BoardViewController;
import com.monicalab.home.controller.HomeController;
import com.monicalab.page.controller.PageViewController;
import com.monicalab.program.controller.ProgramViewController;
import com.monicalab.theme.dto.SiteThemeSettingView;
import com.monicalab.theme.service.SiteThemeSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// P14-T8C: 공개 View Controller에 공통 theme model을 공급한다. HeaderMenuControllerAdvice와 정확히
// 동일한 4개 Controller로 scope를 한정한다(home/layout/default를 사용하는 Controller가 이 4개뿐) -
// 관리자/REST API Controller에는 이 model attribute가 주입되지 않고 SiteThemeSettingService 조회도
// 발생하지 않는다. 메뉴 관심사(HeaderMenuControllerAdvice)와 테마 관심사를 한 클래스에 섞지 않기 위해
// 별도 클래스로 분리했다.
//
// Model에는 accentPreset 하나만이 아니라 SiteThemeSettingView 전체를 "siteTheme"로 공급한다 - 이번
// P14-T8C는 accentPreset만 사용하지만, showPinned/showPrograms/showReviews/showNotices/showGallery의
// public 적용(P14-T8D)이 동일한 model attribute를 재사용할 수 있도록 하기 위함이다. 단 이번 Task의
// public template(home/index.html 등) 어디에서도 accentPreset 외의 필드는 참조하지 않는다.
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        PageViewController.class,
        ProgramViewController.class,
        BoardViewController.class
})
@RequiredArgsConstructor
public class ThemeControllerAdvice {

    private final SiteThemeSettingService siteThemeSettingService;

    @ModelAttribute("siteTheme")
    public SiteThemeSettingView siteTheme() {
        return siteThemeSettingService.getSetting();
    }
}
