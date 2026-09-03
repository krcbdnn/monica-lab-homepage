package com.monicalab.home.controller;

import com.monicalab.banner.service.BannerService;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.util.ContentLinkRenderer;
import com.monicalab.popup.dto.PopupResponse;
import com.monicalab.popup.service.PopupService;
import com.monicalab.program.service.ProgramService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int LATEST_PROGRAM_LIMIT = 3;
    private static final int LATEST_REVIEW_LIMIT = 3;

    private final BannerService bannerService;
    private final PopupService popupService;
    private final BoardService boardService;
    private final ProgramService programService;

    @GetMapping("/")
    public String index(Model model) {
        List<PopupResponse> popups = popupService.getPublicList();
        model.addAttribute("banners", bannerService.getPublicList());
        model.addAttribute("popups", popups);
        model.addAttribute("popupRenderedContents", popupRenderedContents(popups));
        model.addAttribute("latestPrograms",
                programService.getPublicList(null, null, latestProgramPageable()).getContent());
        model.addAttribute("latestReviews",
                boardService.getPublicList(BoardType.REVIEW, null, null, latestReviewPageable()).getContent());
        model.addAttribute("latestNotices",
                boardService.getPublicList(BoardType.NOTICE, null, null, latestBoardPageable()).getContent());
        model.addAttribute("latestGallery",
                boardService.getPublicList(BoardType.GALLERY, null, null, latestBoardPageable()).getContent());
        return "home/index";
    }

    private PageRequest latestBoardPageable() {
        return PageRequest.of(0, LATEST_BOARD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageRequest latestProgramPageable() {
        return PageRequest.of(0, LATEST_PROGRAM_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageRequest latestReviewPageable() {
        return PageRequest.of(0, LATEST_REVIEW_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Map<Long, String> popupRenderedContents(List<PopupResponse> popups) {
        Map<Long, String> renderedContents = new HashMap<>();
        for (PopupResponse popup : popups) {
            renderedContents.put(popup.id(), ContentLinkRenderer.externalLinksOpenInNewTab(popup.content()));
        }
        return renderedContents;
    }
}
