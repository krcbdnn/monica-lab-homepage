package com.monicalab.page.controller;

import com.monicalab.page.entity.PageType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageViewController {

    @GetMapping("/pages/{type}")
    public String view(@PathVariable PageType type) {
        return "home/page/detail";
    }
}
