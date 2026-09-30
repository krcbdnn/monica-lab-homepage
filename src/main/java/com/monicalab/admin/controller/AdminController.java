package com.monicalab.admin.controller;

import com.monicalab.admin.dto.AdminPasswordChangeRequest;
import com.monicalab.admin.dto.AdminResponse;
import com.monicalab.admin.service.AdminService;
import com.monicalab.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // P15-T6: 본인 비밀번호 변경. 성공 후 로그인과 같은 세션 고정 방어로 세션 ID만 교체한다 - 세션 속성
    // (SecurityContext)은 그대로 유지되므로 로그인 상태가 이어진다. 세션 교체는 servlet 관심사라 Controller에 둔다.
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Long adminId,
            @Valid @RequestBody AdminPasswordChangeRequest request, HttpServletRequest httpRequest) {
        adminService.changePassword(adminId, request.currentPassword(), request.newPassword());
        httpRequest.changeSessionId();
        return ApiResponse.success(null);
    }
}
