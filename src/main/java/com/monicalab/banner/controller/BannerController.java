package com.monicalab.banner.controller;

import com.monicalab.banner.dto.BannerResponse;
import com.monicalab.banner.service.BannerService;
import com.monicalab.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    public ApiResponse<List<BannerResponse>> list() {
        return ApiResponse.success(bannerService.getPublicList());
    }
}
