package com.monicalab.program.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProgramViewController {

    @GetMapping("/programs")
    public String list() {
        return "home/program/list";
    }

    @GetMapping("/programs/{id}")
    public String detail(@PathVariable Long id) {
        return "home/program/detail";
    }
}
