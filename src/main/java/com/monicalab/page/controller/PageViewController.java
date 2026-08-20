package com.monicalab.page.controller;

import com.monicalab.page.entity.PageType;
import com.monicalab.page.service.PageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PageViewController {

    private final PageService pageService;

    @GetMapping("/pages/{type}")
    public String view(@PathVariable PageType type, Model model) {
        model.addAttribute("page", pageService.getByType(type));
        return "home/page/detail";
    }
}
