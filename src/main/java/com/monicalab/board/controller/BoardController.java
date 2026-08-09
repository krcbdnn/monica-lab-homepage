package com.monicalab.board.controller;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public ApiResponse<PageResponse<BoardResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(boardService.getPublicList(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BoardResponse> get(@PathVariable Long id) {
        return ApiResponse.success(boardService.getPublicById(id));
    }
}
