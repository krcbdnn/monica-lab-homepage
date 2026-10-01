package com.monicalab.program.controller;

import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.util.ContentLinkRenderer;
import com.monicalab.common.util.PaginationSupport;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ProgramViewController {

    private final ProgramService programService;

    @GetMapping("/programs")
    public String list(
            @RequestParam(required = false) ProgramType programType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String pageJump,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        PageResponse<ProgramResponse> programs = PaginationSupport.resolve(pageable, pageJump,
                p -> programService.getPublicList(programType, keyword, p));
        model.addAttribute("programs", programs);
        model.addAttribute("programType", programType);
        model.addAttribute("keyword", keyword);
        return "home/program/list";
    }

    @GetMapping("/programs/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProgramResponse program = programService.getPublicById(id);
        model.addAttribute("program", program);
        model.addAttribute("renderedContent", ContentLinkRenderer.externalLinksOpenInNewTab(program.content()));
        return "home/program/detail";
    }
}
