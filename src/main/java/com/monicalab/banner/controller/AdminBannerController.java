package com.monicalab.banner.controller;

import com.monicalab.banner.dto.BannerOrderRequest;
import com.monicalab.banner.dto.BannerRequest;
import com.monicalab.banner.dto.BannerResponse;
import com.monicalab.banner.dto.BannerVisibilityRequest;
import com.monicalab.banner.service.BannerService;
import com.monicalab.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public ApiResponse<List<BannerResponse>> list(
            @SortDefault(sort = "sortOrder", direction = Sort.Direction.ASC) Sort sort) {
        return ApiResponse.success(bannerService.getAdminList(sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<BannerResponse> get(@PathVariable Long id) {
        return ApiResponse.success(bannerService.getAdminById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BannerResponse> create(@Valid @RequestBody BannerRequest request) {
        return ApiResponse.success(bannerService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BannerResponse> update(@PathVariable Long id,
            @Validated({Default.class, BannerRequest.OnUpdate.class}) @RequestBody BannerRequest request) {
        return ApiResponse.success(bannerService.update(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<BannerResponse> updateVisibility(@PathVariable Long id,
            @Valid @RequestBody BannerVisibilityRequest request) {
        return ApiResponse.success(bannerService.updateVisibility(id, request));
    }

    @PatchMapping("/{id}/order")
    public ApiResponse<BannerResponse> updateOrder(@PathVariable Long id,
            @Valid @RequestBody BannerOrderRequest request) {
        return ApiResponse.success(bannerService.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bannerService.delete(id);
    }
}
