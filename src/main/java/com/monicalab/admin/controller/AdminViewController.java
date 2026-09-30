package com.monicalab.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminViewController {

    @GetMapping("/admin/login")
    public String loginPage() {
        return "admin/login";
    }

    @GetMapping("/admin/dashboard")
    public String dashboard() {
        return "admin/dashboard";
    }

    // P15-T6: 본인 비밀번호 변경 화면. 실제 변경은 화면의 JS가 PUT /api/admin/me/password를 호출한다.
    @GetMapping("/admin/password")
    public String password() {
        return "admin/password";
    }
}
