package com.monicalab.theme.controller;

import com.monicalab.theme.entity.AccentPreset;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// P14-T8B: 화면은 진입만 담당하고, 실제 현재 설정 값은 admin/page/form.html과 동일하게 화면의 JS가
// GET /api/admin/theme를 호출해 채운다(서버 사이드 Model과 JS 초기화를 혼합하지 않는다). AccentPreset
// 목록만 Model로 내려줘 template이 enum을 기반으로 option을 렌더링하게 하고, T8C에서 enum 값이
// 늘어나도 template 구조 변경 없이 자연히 확장되도록 한다.
@Controller
@RequestMapping("/admin/theme")
public class AdminThemeViewController {

    @GetMapping
    public String form(Model model) {
        model.addAttribute("accentPresets", AccentPreset.values());
        return "admin/theme/form";
    }
}
