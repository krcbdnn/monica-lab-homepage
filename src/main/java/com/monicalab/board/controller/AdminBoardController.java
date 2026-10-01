package com.monicalab.board.controller;

import com.monicalab.board.dto.BoardRequest;
import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.dto.BoardVisibilityRequest;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.response.ApiResponse;
import com.monicalab.program.entity.ProgramType;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/boards")
@RequiredArgsConstructor
public class AdminBoardController {

    private final BoardService boardService;

    @GetMapping
    public ApiResponse<PageResponse<BoardResponse>> list(
            @RequestParam(required = false) BoardType boardType,
            @RequestParam(required = false) ProgramType programType,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(boardService.getAdminList(boardType, programType, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BoardResponse> get(@PathVariable Long id) {
        return ApiResponse.success(boardService.getAdminById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> create(@Valid @RequestBody BoardRequest request) {
        return ApiResponse.success(boardService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BoardResponse> update(@PathVariable Long id,
            @Validated({Default.class, BoardRequest.OnUpdate.class}) @RequestBody BoardRequest request) {
        return ApiResponse.success(boardService.update(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<BoardResponse> updateVisibility(@PathVariable Long id,
            @Valid @RequestBody BoardVisibilityRequest request) {
        return ApiResponse.success(boardService.updateVisibility(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        boardService.delete(id);
    }
}
