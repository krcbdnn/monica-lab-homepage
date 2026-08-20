package com.monicalab.page.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/pages")
public class AdminPageViewController {

    @GetMapping
    public String list() {
        return "admin/page/list";
    }

    @GetMapping("/{pageType}/edit")
    public String edit() {
        return "admin/page/form";
    }
}
