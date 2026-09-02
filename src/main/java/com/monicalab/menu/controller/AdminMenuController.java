package com.monicalab.menu.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.menu.dto.MenuOrderRequest;
import com.monicalab.menu.dto.MenuRequest;
import com.monicalab.menu.dto.MenuResponse;
import com.monicalab.menu.dto.MenuVisibilityRequest;
import com.monicalab.menu.service.MenuService;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
public class AdminMenuController {

    private final MenuService menuService;

    // P13-T30A: Menu는 트리 구조라 정렬 기준이 고정되어야 하므로(부모 바로 뒤에 자식), Banner의
    // GET 목록과 달리 호출자가 임의로 정렬 필드를 고르는 sort 파라미터를 두지 않는다.
    @GetMapping
    public ApiResponse<List<MenuResponse>> list() {
        return ApiResponse.success(menuService.getAdminList());
    }

    @GetMapping("/{id}")
    public ApiResponse<MenuResponse> get(@PathVariable Long id) {
        return ApiResponse.success(menuService.getAdminById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MenuResponse> create(@Valid @RequestBody MenuRequest request) {
        return ApiResponse.success(menuService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MenuResponse> update(@PathVariable Long id,
            @Validated({Default.class, MenuRequest.OnUpdate.class}) @RequestBody MenuRequest request) {
        return ApiResponse.success(menuService.update(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<MenuResponse> updateVisibility(@PathVariable Long id,
            @Valid @RequestBody MenuVisibilityRequest request) {
        return ApiResponse.success(menuService.updateVisibility(id, request));
    }

    @PatchMapping("/{id}/order")
    public ApiResponse<MenuResponse> updateOrder(@PathVariable Long id,
            @Valid @RequestBody MenuOrderRequest request) {
        return ApiResponse.success(menuService.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        menuService.delete(id);
    }
}
