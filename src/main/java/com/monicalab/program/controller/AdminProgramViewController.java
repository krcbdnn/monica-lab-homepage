package com.monicalab.program.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/programs")
public class AdminProgramViewController {

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
}
