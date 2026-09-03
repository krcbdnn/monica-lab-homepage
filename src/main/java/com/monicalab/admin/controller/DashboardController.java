package com.monicalab.admin.controller;

import com.monicalab.admin.dto.BoardSummaryResponse;
import com.monicalab.admin.dto.DashboardResponse;
import com.monicalab.admin.dto.QuickMenuResponse;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.response.ApiResponse;
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

    private final BoardService boardService;
    private final ProgramService programService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        PageRequest recentBoardsPageable =
                PageRequest.of(0, RECENT_BOARD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<BoardSummaryResponse> recentBoards = boardService.getAdminList(null, null, null, recentBoardsPageable)
                .getContent().stream()
                .map(BoardSummaryResponse::from)
                .toList();

        DashboardResponse response = new DashboardResponse(
                recentBoards,
                programService.getRecruitStatusCounts(),
                QuickMenuResponse.fixedMenus());

        return ApiResponse.success(response);
    }
}
