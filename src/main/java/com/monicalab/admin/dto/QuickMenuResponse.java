package com.monicalab.admin.dto;

import java.util.List;

public record QuickMenuResponse(String label, String url) {

    public static List<QuickMenuResponse> fixedMenus() {
        return List.of(
                new QuickMenuResponse("기관소개 관리", "/admin/pages"),
                new QuickMenuResponse("프로그램 관리", "/admin/programs"),
                new QuickMenuResponse("게시판 관리", "/admin/boards"),
                new QuickMenuResponse("배너 관리", "/admin/banners"),
                new QuickMenuResponse("팝업 관리", "/admin/popups"),
                new QuickMenuResponse("파일 관리", "/admin/files"));
    }
}
