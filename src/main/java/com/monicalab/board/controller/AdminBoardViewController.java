package com.monicalab.board.controller;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.util.ContentLinkRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/boards")
@RequiredArgsConstructor
public class AdminBoardViewController {

    private final BoardService boardService;

    @GetMapping
    public String list() {
        return "admin/board/list";
    }

    @GetMapping("/new")
    public String create() {
        return "admin/board/form";
    }

    @GetMapping("/{id}/edit")
    public String edit() {
        return "admin/board/form";
    }

    // 읽기 전용 상세만 예외적으로 서버에서 조회한다(ARCHITECTURE.md "Admin 화면(View) / API 컨트롤러 명명 규칙"
    // 예외): 본문을 공개 상세와 같은 renderedContent + th:utext 경로로 출력하기 위함. 공개 여부와 무관하게 조회한다.
    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        BoardResponse board = boardService.getAdminById(id);
        model.addAttribute("board", board);
        model.addAttribute("renderedContent", ContentLinkRenderer.externalLinksOpenInNewTab(board.content()));
        return "admin/board/detail";
    }
}
