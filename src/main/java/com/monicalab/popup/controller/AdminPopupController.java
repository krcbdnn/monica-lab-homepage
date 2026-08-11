package com.monicalab.popup.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.popup.dto.PopupRequest;
import com.monicalab.popup.dto.PopupResponse;
import com.monicalab.popup.dto.PopupVisibilityRequest;
import com.monicalab.popup.service.PopupService;
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
@RequestMapping("/api/admin/popups")
@RequiredArgsConstructor
public class AdminPopupController {

    private final PopupService popupService;

    @GetMapping
    public ApiResponse<List<PopupResponse>> list(
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sort) {
        return ApiResponse.success(popupService.getAdminList(sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<PopupResponse> get(@PathVariable Long id) {
        return ApiResponse.success(popupService.getAdminById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PopupResponse> create(@Valid @RequestBody PopupRequest request) {
        return ApiResponse.success(popupService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PopupResponse> update(@PathVariable Long id,
            @Validated({Default.class, PopupRequest.OnUpdate.class}) @RequestBody PopupRequest request) {
        return ApiResponse.success(popupService.update(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<PopupResponse> updateVisibility(@PathVariable Long id,
            @Valid @RequestBody PopupVisibilityRequest request) {
        return ApiResponse.success(popupService.updateVisibility(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        popupService.delete(id);
    }
}
