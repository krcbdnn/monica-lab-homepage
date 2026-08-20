package com.monicalab.admin.dto;

import com.monicalab.program.entity.RecruitStatus;
import java.util.List;
import java.util.Map;

public record DashboardResponse(List<BoardSummaryResponse> recentBoards, Map<RecruitStatus, Long> programStatus,
        List<QuickMenuResponse> quickMenus) {
}
