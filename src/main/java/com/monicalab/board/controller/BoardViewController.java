package com.monicalab.board.controller;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.service.BoardService;
import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.util.ContentLinkRenderer;
import com.monicalab.common.util.PaginationSupport;
import com.monicalab.program.entity.ProgramType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class BoardViewController {

    private final BoardService boardService;

    @GetMapping("/boards")
    public String list(
            @RequestParam(required = false) BoardType boardType,
            @RequestParam(required = false) ProgramType programType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String pageJump,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        PageResponse<BoardResponse> boards = PaginationSupport.resolve(pageable, pageJump,
                p -> boardService.getPublicList(boardType, programType, keyword, p));
        model.addAttribute("boards", boards);
        model.addAttribute("boardType", boardType);
        // BoardService가 boardType!=REVIEW면 programType을 검색에서 이미 무시하므로, 화면에도
        // boardType=REVIEW일 때만 programType을 실어 pagination/필터 링크에 stale 값이 남지 않게 한다.
        model.addAttribute("programType", boardType == BoardType.REVIEW ? programType : null);
        model.addAttribute("keyword", keyword);
        return "home/board/list";
    }

    // P13-T28: boardType/keyword/page는 목록에서 넘어온 "돌아갈 목록 상태"를 "목록으로" 링크
    // 생성에만 쓰기 위한 값이다. 상세 조회(getPublicById)에는 전혀 관여하지 않으며, 타입 변환이
    // 실패할 수 없도록 원시 String으로만 받는다(값 검증/정제는 이 Task 범위가 아니다 - 최종 해석은
    // 기존 /boards 엔드포인트의 기존 계약을 그대로 따른다). programType도 동일 원칙으로 원시 String
    // 그대로 passthrough한다(Task C).
    @GetMapping("/boards/{id}")
    public String detail(@PathVariable Long id,
            @RequestParam(required = false) String boardType,
            @RequestParam(required = false) String programType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String page,
            Model model) {
        BoardResponse board = boardService.getPublicById(id);
        model.addAttribute("board", board);
        model.addAttribute("renderedContent", ContentLinkRenderer.externalLinksOpenInNewTab(board.content()));
        model.addAttribute("boardType", boardType);
        model.addAttribute("programType", programType);
        model.addAttribute("keyword", keyword);
        model.addAttribute("page", page);
        return "home/board/detail";
    }
}
