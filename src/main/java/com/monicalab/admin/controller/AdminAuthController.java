package com.monicalab.admin.controller;

import com.monicalab.admin.dto.AdminLoginRequest;
import com.monicalab.admin.dto.AdminResponse;
import com.monicalab.admin.entity.Admin;
import com.monicalab.admin.service.AdminService;
import com.monicalab.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminService adminService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/login")
    public ApiResponse<AdminResponse> login(@Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Admin admin = adminService.authenticate(request.loginId(), request.password());

        // 세션 고정(session fixation) 공격 방어: 로그인 이전에 이미 세션이 존재했다면
        // 인증 컨텍스트를 저장하기 전에 세션 ID를 회전한다.
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            httpRequest.changeSessionId();
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                admin.getId(), null, List.of(new SimpleGrantedAuthority(admin.getRole().name())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ApiResponse.success(AdminResponse.from(admin));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiResponse.success(null);
    }
}
