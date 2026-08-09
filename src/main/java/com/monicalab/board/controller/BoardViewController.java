package com.monicalab.board.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class BoardViewController {

    @GetMapping("/boards")
    public String list() {
        return "home/board/list";
    }

    @GetMapping("/boards/{id}")
    public String detail(@PathVariable Long id) {
        return "home/board/detail";
    }
}
