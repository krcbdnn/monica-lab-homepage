package com.monicalab.program.controller;

import com.monicalab.common.util.ContentLinkRenderer;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/programs")
@RequiredArgsConstructor
public class AdminProgramViewController {

    private final ProgramService programService;

    @GetMapping
    public String list() {
        return "admin/program/list";
    }

    @GetMapping("/new")
    public String create() {
        return "admin/program/form";
    }

    @GetMapping("/{id}/edit")
    public String edit() {
        return "admin/program/form";
    }

    // 읽기 전용 상세만 예외적으로 서버에서 조회한다(ARCHITECTURE.md "Admin 화면(View) / API 컨트롤러 명명 규칙"
    // 예외): 본문을 공개 상세와 같은 renderedContent + th:utext 경로로 출력하기 위함. 공개 여부와 무관하게 조회한다.
    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        ProgramResponse program = programService.getAdminById(id);
        model.addAttribute("program", program);
        model.addAttribute("renderedContent", ContentLinkRenderer.externalLinksOpenInNewTab(program.content()));
        return "admin/program/detail";
    }
}
