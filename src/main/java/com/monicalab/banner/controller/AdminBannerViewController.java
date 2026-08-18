package com.monicalab.banner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/banners")
public class AdminBannerViewController {

    @GetMapping
    public String list() {
        return "admin/banner/list";
    }

    @GetMapping("/new")
    public String create() {
        return "admin/banner/form";
    }

    @GetMapping("/{id}/edit")
    public String edit() {
        return "admin/banner/form";
    }
}
