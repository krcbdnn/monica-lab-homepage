package com.monicalab.admin.controller;

import com.monicalab.admin.dto.BoardSummaryResponse;
import com.monicalab.admin.dto.DashboardResponse;
import com.monicalab.admin.dto.ProgramSummaryResponse;
import com.monicalab.admin.dto.QuickMenuResponse;
import com.monicalab.banner.service.BannerService;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.response.ApiResponse;
import com.monicalab.popup.service.PopupService;
import com.monicalab.program.service.ProgramService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DashboardController {

    private static final int RECENT_BOARD_LIMIT = 5;
    private static final int RECENT_PROGRAM_LIMIT = 5;

    private final BoardService boardService;
    private final ProgramService programService;
    private final PopupService popupService;
    private final BannerService bannerService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        PageRequest recentBoardsPageable =
                PageRequest.of(0, RECENT_BOARD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<BoardSummaryResponse> recentBoards = boardService.getAdminList(null, null, null, recentBoardsPageable)
                .getContent().stream()
                .map(BoardSummaryResponse::from)
                .toList();

        // P14-T9E: 최근 프로그램도 최근 게시글과 같은 방식으로 기존 관리자 목록 조회(DB에서 5건 제한, 공개/비공개 전체)를
        // 재사용하고, 응답에는 본문 없는 summary만 싣는다.
        PageRequest recentProgramsPageable =
                PageRequest.of(0, RECENT_PROGRAM_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ProgramSummaryResponse> recentPrograms = programService.getAdminList(null, null, recentProgramsPageable)
                .getContent().stream()
                .map(ProgramSummaryResponse::from)
                .toList();

        // P14-T9E: "현재 노출 중" 판정은 공개 화면과 같은 기존 공개 목록 조회를 그대로 재사용한다(Popup: 노출 + 서버 현재
        // 시각이 시작일~종료일 안, Banner: 노출). 둘 다 소량 테이블이라 별도 count query를 두지 않는다.
        DashboardResponse response = new DashboardResponse(
                recentBoards,
                programService.getRecruitStatusCounts(),
                QuickMenuResponse.fixedMenus(),
                recentPrograms,
                popupService.getPublicList().size(),
                bannerService.getPublicList().size());

        return ApiResponse.success(response);
    }
}
