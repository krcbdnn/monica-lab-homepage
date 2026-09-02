package com.monicalab.menu.controller;

import com.monicalab.board.controller.BoardViewController;
import com.monicalab.home.controller.HomeController;
import com.monicalab.menu.dto.HeaderMenuItem;
import com.monicalab.menu.service.MenuService;
import com.monicalab.page.controller.PageViewController;
import com.monicalab.program.controller.ProgramViewController;
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
}
