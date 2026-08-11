package com.monicalab.popup.controller;

import com.monicalab.common.response.ApiResponse;
import com.monicalab.popup.dto.PopupResponse;
import com.monicalab.popup.service.PopupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/popups")
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;

    @GetMapping
    public ApiResponse<List<PopupResponse>> list() {
        return ApiResponse.success(popupService.getPublicList());
    }
}
