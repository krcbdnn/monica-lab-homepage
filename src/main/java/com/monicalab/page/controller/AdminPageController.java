package com.monicalab.page.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.page.dto.PageRequest;
import com.monicalab.page.dto.PageResponse;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.service.PageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pages")
@RequiredArgsConstructor
public class AdminPageController {

    private final PageService pageService;

    @GetMapping("/{pageType}")
    public ApiResponse<PageResponse> get(@PathVariable PageType pageType) {
        return ApiResponse.success(pageService.getByType(pageType));
    }

    @PutMapping("/{pageType}")
    public ApiResponse<PageResponse> update(@PathVariable PageType pageType, @Valid @RequestBody PageRequest request) {
        return ApiResponse.success(pageService.update(pageType, request));
    }
}
