package com.monicalab.board.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/boards")
public class AdminBoardViewController {

    @GetMapping
    public String list() {
        return "admin/board/list";
    }

    @GetMapping("/new")
    public String create() {
        return "admin/board/form";
    }

    @GetMapping("/{id}/edit")
    public String edit() {
        return "admin/board/form";
    }
}
