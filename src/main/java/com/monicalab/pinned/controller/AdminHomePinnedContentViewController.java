package com.monicalab.pinned.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 별도 등록/수정 폼 View가 없다 - 추가/순서변경/노출변경/고정해제를 전부 이 목록 화면 하나에서 처리한다
// (HomePinnedContent는 "수정 폼"이 필요한 자체 콘텐츠가 아니라 기존 Board/Program에 대한 참조이므로).
@Controller
@RequestMapping("/admin/home-pinned-contents")
public class AdminHomePinnedContentViewController {

    @GetMapping
    public String list() {
        return "admin/homepinned/list";
    }
}
