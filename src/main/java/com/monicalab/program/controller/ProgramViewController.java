package com.monicalab.program.controller;

import com.monicalab.common.dto.PageResponse;
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
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        PageResponse<ProgramResponse> programs = programService.getPublicList(programType, keyword, pageable);
        model.addAttribute("programs", programs);
        model.addAttribute("programType", programType);
        model.addAttribute("keyword", keyword);
        return "home/program/list";
    }

    @GetMapping("/programs/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("program", programService.getPublicById(id));
        return "home/program/detail";
    }
}
