package com.monicalab.program.controller;

import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.response.ApiResponse;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.service.ProgramService;
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
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @GetMapping
    public ApiResponse<PageResponse<ProgramResponse>> list(
            @RequestParam(required = false) ProgramType programType,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(programService.getPublicList(programType, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProgramResponse> get(@PathVariable Long id) {
        return ApiResponse.success(programService.getPublicById(id));
    }
}
