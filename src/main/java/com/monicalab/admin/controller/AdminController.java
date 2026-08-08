package com.monicalab.admin.controller;

import com.monicalab.admin.dto.AdminResponse;
import com.monicalab.admin.service.AdminService;
import com.monicalab.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/me")
    public ApiResponse<AdminResponse> me(@AuthenticationPrincipal Long adminId) {
        return ApiResponse.success(AdminResponse.from(adminService.getById(adminId)));
    }
}
