package com.monicalab.menu.controller;

import com.monicalab.board.controller.BoardViewController;
import com.monicalab.home.controller.HomeController;
import com.monicalab.menu.dto.HeaderMenuItem;
import com.monicalab.menu.service.MenuService;
import com.monicalab.menu.support.CurrentLocation;
import com.monicalab.page.controller.PageViewController;
import com.monicalab.program.controller.ProgramViewController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// P13-T30B: 공개 View Controller에 공통 header menu model을 공급한다. home/layout/default를 사용하는
// 템플릿을 렌더링하는 Controller가 정확히 이 4개뿐이라 assignableTypes로 한정한다 - 관리자/API
// 컨트롤러에는 이 model attribute가 주입되지 않고 Menu 조회도 발생하지 않는다.
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        PageViewController.class,
        ProgramViewController.class,
        BoardViewController.class
})
@RequiredArgsConstructor
public class HeaderMenuControllerAdvice {

    private final MenuService menuService;

    @ModelAttribute("headerMenuItems")
    public List<HeaderMenuItem> headerMenuItems() {
        return menuService.getPublicMenuTree();
    }

    // P13-T37: Header active(현재 페이지) 판정을 위해 request 시점에만 알 수 있는 원시 정보(경로,
    // raw query string)만 담아 넘긴다. targetType 판정/URL matching/semantic query 비교는 전혀 하지
    // 않는다 - 전부 HeaderActiveResolver(view-support)가 header.html 렌더링 시점에 담당한다. 이
    // advice가 실행되는 시점에는 BoardViewController.detail()/ProgramViewController.detail()이
    // 아직 board/program 모델을 채우기 전이라(Spring MVC의 ControllerAdvice @ModelAttribute는 대상
    // 핸들러보다 먼저 실행됨) 그 값들에 의존하는 판정은 여기서 시도하지 않는다.
    @ModelAttribute("currentLocation")
    public CurrentLocation currentLocation(HttpServletRequest request) {
        return new CurrentLocation(request.getRequestURI(), request.getQueryString());
    }
}
