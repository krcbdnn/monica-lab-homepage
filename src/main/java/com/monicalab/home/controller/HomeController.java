package com.monicalab.home.controller;

import com.monicalab.banner.service.BannerService;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.service.BoardService;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.service.PageService;
import com.monicalab.popup.service.PopupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int LATEST_BOARD_LIMIT = 5;

    private final BannerService bannerService;
    private final PopupService popupService;
    private final PageService pageService;
    private final BoardService boardService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("banners", bannerService.getPublicList());
        model.addAttribute("popups", popupService.getPublicList());
        model.addAttribute("greeting", pageService.getByType(PageType.GREETING));
        model.addAttribute("latestNotices",
                boardService.getPublicList(BoardType.NOTICE, null, latestBoardPageable()).getContent());
        model.addAttribute("latestGallery",
                boardService.getPublicList(BoardType.GALLERY, null, latestBoardPageable()).getContent());
        return "home/index";
    }

    private PageRequest latestBoardPageable() {
        return PageRequest.of(0, LATEST_BOARD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
