package com.monicalab.admin.dto;

import com.monicalab.program.entity.RecruitStatus;
import java.util.List;
import java.util.Map;

// P14-T9E: 기존 필드(recentBoards/programStatus/quickMenus)는 그대로 두고 recentPrograms/visiblePopupCount/
// visibleBannerCount를 additive하게 추가했다. 두 count는 공개 화면 노출 기준과 같다(Popup: 노출 + 현재 기간 안,
// Banner: 노출).
public record DashboardResponse(List<BoardSummaryResponse> recentBoards, Map<RecruitStatus, Long> programStatus,
        List<QuickMenuResponse> quickMenus, List<ProgramSummaryResponse> recentPrograms, long visiblePopupCount,
        long visibleBannerCount) {
}
