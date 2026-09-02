package com.monicalab.menu.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/menus")
public class AdminMenuViewController {

    @GetMapping
    public String list() {
        return "admin/menu/list";
    }

    @GetMapping("/new")
    public String create() {
        return "admin/menu/form";
    }

    @GetMapping("/{id}/edit")
    public String edit() {
        return "admin/menu/form";
    }
}
