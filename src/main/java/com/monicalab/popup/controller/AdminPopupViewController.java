package com.monicalab.popup.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/popups")
public class AdminPopupViewController {

    @GetMapping
    public String list() {
        return "admin/popup/list";
    }

    @GetMapping("/new")
    public String create() {
        return "admin/popup/form";
    }

    @GetMapping("/{id}/edit")
    public String edit() {
        return "admin/popup/form";
    }
}
