package com.monicalab.board.controller;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class BoardViewController {

    private final BoardService boardService;

    @GetMapping("/boards")
    public String list(
            @RequestParam(required = false) BoardType boardType,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        PageResponse<BoardResponse> boards = boardService.getPublicList(boardType, keyword, pageable);
        model.addAttribute("boards", boards);
        model.addAttribute("boardType", boardType);
        model.addAttribute("keyword", keyword);
        return "home/board/list";
    }

    @GetMapping("/boards/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("board", boardService.getPublicById(id));
        return "home/board/detail";
    }
}
