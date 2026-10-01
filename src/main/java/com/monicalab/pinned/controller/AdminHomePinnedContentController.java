package com.monicalab.pinned.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.pinned.dto.HomePinnedContentOrderRequest;
import com.monicalab.pinned.dto.HomePinnedContentRequest;
import com.monicalab.pinned.dto.HomePinnedContentResponse;
import com.monicalab.pinned.dto.HomePinnedContentVisibilityRequest;
import com.monicalab.pinned.service.HomePinnedContentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/home-pinned-contents")
@RequiredArgsConstructor
public class AdminHomePinnedContentController {

    private final HomePinnedContentService homePinnedContentService;

    // Menu와 동일하게 sortOrder ASC, id ASC 고정 정렬이라 별도 sort 쿼리 파라미터를 두지 않는다.
    @GetMapping
    public ApiResponse<List<HomePinnedContentResponse>> list() {
        return ApiResponse.success(homePinnedContentService.getAdminList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HomePinnedContentResponse> create(@Valid @RequestBody HomePinnedContentRequest request) {
        return ApiResponse.success(homePinnedContentService.create(request));
    }

    @PatchMapping("/{id}/order")
    public ApiResponse<HomePinnedContentResponse> updateOrder(@PathVariable Long id,
            @Valid @RequestBody HomePinnedContentOrderRequest request) {
        return ApiResponse.success(homePinnedContentService.updateOrder(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<HomePinnedContentResponse> updateVisibility(@PathVariable Long id,
            @Valid @RequestBody HomePinnedContentVisibilityRequest request) {
        return ApiResponse.success(homePinnedContentService.updateVisibility(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        homePinnedContentService.delete(id);
    }
}
