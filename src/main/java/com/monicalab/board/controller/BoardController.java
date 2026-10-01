package com.monicalab.board.controller;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.response.ApiResponse;
import com.monicalab.program.entity.ProgramType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public ApiResponse<PageResponse<BoardResponse>> list(
            @RequestParam(required = false) BoardType boardType,
            @RequestParam(required = false) ProgramType programType,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(boardService.getPublicList(boardType, programType, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BoardResponse> get(@PathVariable Long id) {
        return ApiResponse.success(boardService.getPublicById(id));
    }
}
