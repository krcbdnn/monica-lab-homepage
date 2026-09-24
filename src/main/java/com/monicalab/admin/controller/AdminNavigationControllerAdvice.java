package com.monicalab.admin.controller;

import com.monicalab.banner.controller.AdminBannerViewController;
import com.monicalab.board.controller.AdminBoardViewController;
import com.monicalab.file.controller.AdminFileViewController;
import com.monicalab.menu.controller.AdminMenuViewController;
import com.monicalab.page.controller.AdminPageViewController;
import com.monicalab.pinned.controller.AdminHomePinnedContentViewController;
import com.monicalab.popup.controller.AdminPopupViewController;
import com.monicalab.program.controller.AdminProgramViewController;
import com.monicalab.theme.controller.AdminThemeViewController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// P14-T9A: sidebar active navigation을 위한 최소 model attribute만 공급한다. 처음에는 Thymeleaf
// 기본 expression object #httpServletRequest로 해결하려 했으나, admin/layout/sidebar.html이
// th:replace fragment로 포함되는 경로에서 실제로 null을 반환해 SpelEvaluationException(EL1007E)이
// 발생함을 테스트 실행으로 확인했다(템플릿 hack으로 우회하지 않고 대신 이 작은 ControllerAdvice를
// 도입). HeaderMenuControllerAdvice(public)가 currentLocation을 공급하는 것과 정확히 동일한
// 원리·규모다. 대상은 sidebar fragment를 렌더링하는 10개 Admin View Controller 전부이며(로그인
// 화면을 렌더링하는 AdminViewController.login()도 포함되지만 login.html은 이 fragment를 쓰지
// 않으므로 무해하다), REST API Controller에는 적용되지 않는다.
@ControllerAdvice(assignableTypes = {
        AdminViewController.class,
        AdminPageViewController.class,
        AdminProgramViewController.class,
        AdminBoardViewController.class,
        AdminBannerViewController.class,
        AdminPopupViewController.class,
        AdminFileViewController.class,
        AdminMenuViewController.class,
        AdminHomePinnedContentViewController.class,
        AdminThemeViewController.class
})
public class AdminNavigationControllerAdvice {

    @ModelAttribute("currentAdminPath")
    public String currentAdminPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
