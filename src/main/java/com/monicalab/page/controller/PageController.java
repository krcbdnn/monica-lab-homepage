package com.monicalab.page.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.page.dto.PageResponse;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.service.PageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @GetMapping("/{pageType}")
    public ApiResponse<PageResponse> get(@PathVariable PageType pageType) {
        return ApiResponse.success(pageService.getByType(pageType));
    }
}
