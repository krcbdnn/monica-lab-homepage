package com.monicalab.admin.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class DashboardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ProgramRepository programRepository;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
        programRepository.deleteAll();
    }

    @Test
    void dashboardWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardReturnsAtMostFiveRecentBoardsRegardlessOfVisibility() throws Exception {
        for (int i = 0; i < 6; i++) {
            boardRepository.saveAndFlush(Board.builder()
                    .boardType(BoardType.NOTICE)
                    .title("게시글 " + i)
                    .isPublic(i % 2 == 0)
                    .build());
        }

        mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentBoards.length()").value(5));
    }

    @Test
    void dashboardReturnsProgramStatusCountsForOpenAndClosed() throws Exception {
        programRepository.saveAndFlush(program(RecruitStatus.OPEN));
        programRepository.saveAndFlush(program(RecruitStatus.OPEN));
        programRepository.saveAndFlush(program(RecruitStatus.OPEN));
        programRepository.saveAndFlush(program(RecruitStatus.CLOSED));
        programRepository.saveAndFlush(program(RecruitStatus.CLOSED));

        mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.programStatus.OPEN").value(3))
                .andExpect(jsonPath("$.data.programStatus.CLOSED").value(2));
    }

    @Test
    void dashboardReturnsFixedQuickMenusMatchingApiContract() throws Exception {
        mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quickMenus.length()").value(6))
                .andExpect(jsonPath("$.data.quickMenus[0].label").value("기관소개 관리"))
                .andExpect(jsonPath("$.data.quickMenus[0].url").value("/admin/pages"))
                .andExpect(jsonPath("$.data.quickMenus[1].label").value("프로그램 관리"))
                .andExpect(jsonPath("$.data.quickMenus[1].url").value("/admin/programs"))
                .andExpect(jsonPath("$.data.quickMenus[2].label").value("게시판 관리"))
                .andExpect(jsonPath("$.data.quickMenus[2].url").value("/admin/boards"))
                .andExpect(jsonPath("$.data.quickMenus[3].label").value("배너 관리"))
                .andExpect(jsonPath("$.data.quickMenus[3].url").value("/admin/banners"))
                .andExpect(jsonPath("$.data.quickMenus[4].label").value("팝업 관리"))
                .andExpect(jsonPath("$.data.quickMenus[4].url").value("/admin/popups"))
                .andExpect(jsonPath("$.data.quickMenus[5].label").value("파일 관리"))
                .andExpect(jsonPath("$.data.quickMenus[5].url").value("/admin/files"));
    }

    private Program program(RecruitStatus recruitStatus) {
        return Program.builder()
                .programType(ProgramType.COURSE)
                .title("프로그램")
                .recruitStatus(recruitStatus)
                .isPublic(true)
                .build();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
