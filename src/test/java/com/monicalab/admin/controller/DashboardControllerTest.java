package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.monicalab.banner.entity.Banner;
import com.monicalab.banner.repository.BannerRepository;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.popup.entity.Popup;
import com.monicalab.popup.repository.PopupRepository;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private PopupRepository popupRepository;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
        programRepository.deleteAll();
        popupRepository.deleteAll();
        bannerRepository.deleteAll();
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

    // P14-T9E: 최근 게시글은 최신순 최대 5건이고, REVIEW 하위유형 표시를 위해 programType을 additive하게 싣는다.
    // createdAt은 초 단위 동률을 피하려고 테스트에서만 서로 다른 값으로 고정한다.
    @Test
    void dashboardReturnsNewestFiveBoardsWithProgramType() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
        for (int i = 0; i < 6; i++) {
            Board board = boardRepository.saveAndFlush(Board.builder()
                    .boardType(i == 5 ? BoardType.REVIEW : BoardType.NOTICE)
                    .programType(i == 5 ? ProgramType.COURSE : null)
                    .title("게시글 " + i)
                    .isPublic(true)
                    .build());
            setCreatedAt("board", board.getId(), base.plusMinutes(i));
        }

        String body = mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentBoards.length()").value(5))
                .andExpect(jsonPath("$.data.recentBoards[0].title").value("게시글 5"))
                .andExpect(jsonPath("$.data.recentBoards[0].boardType").value("REVIEW"))
                .andExpect(jsonPath("$.data.recentBoards[0].programType").value("COURSE"))
                .andExpect(jsonPath("$.data.recentBoards[1].programType").doesNotExist())
                .andExpect(jsonPath("$.data.recentBoards[0].content").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> titles = JsonPath.read(body, "$.data.recentBoards[*].title");
        assertThat(titles).containsExactly("게시글 5", "게시글 4", "게시글 3", "게시글 2", "게시글 1");
    }

    // P14-T9E: 최근 프로그램은 공개/비공개 전체 중 최신순 최대 5건이고, 본문/파일 URL 없는 summary만 싣는다.
    @Test
    void dashboardReturnsNewestFiveProgramSummariesWithoutContent() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
        for (int i = 0; i < 6; i++) {
            Program program = programRepository.saveAndFlush(Program.builder()
                    .programType(i % 2 == 0 ? ProgramType.COURSE : ProgramType.SPECIAL)
                    .title("프로그램 " + i)
                    .content("<p>본문 " + i + "</p>")
                    .recruitStatus(i == 5 ? RecruitStatus.CLOSED : RecruitStatus.OPEN)
                    .isPublic(i != 4)
                    .build());
            setCreatedAt("program", program.getId(), base.plusMinutes(i));
        }

        String body = mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentPrograms.length()").value(5))
                .andExpect(jsonPath("$.data.recentPrograms[0].title").value("프로그램 5"))
                .andExpect(jsonPath("$.data.recentPrograms[0].programType").value("SPECIAL"))
                .andExpect(jsonPath("$.data.recentPrograms[0].recruitStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.recentPrograms[1].isPublic").value(false))
                .andExpect(jsonPath("$.data.recentPrograms[0].createdAt").exists())
                .andExpect(jsonPath("$.data.recentPrograms[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.recentPrograms[0].thumbnail").doesNotExist())
                .andExpect(jsonPath("$.data.recentPrograms[0].googleFormUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> titles = JsonPath.read(body, "$.data.recentPrograms[*].title");
        assertThat(titles).containsExactly("프로그램 5", "프로그램 4", "프로그램 3", "프로그램 2", "프로그램 1");
    }

    // P14-T9E: "현재 노출 중인 팝업"은 공개 화면과 같은 기준(isVisible && startDate <= now <= endDate)이다 - 단순
    // isVisible 개수가 아니다. 기간 밖(종료/시작 전)과 비노출은 세지 않는다.
    @Test
    void dashboardCountsOnlyPopupsVisibleNowLikeThePublicLayer() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        popupRepository.saveAndFlush(popup(true, now.minusDays(1), now.plusDays(1)));
        popupRepository.saveAndFlush(popup(true, now.minusMinutes(1), now.plusMinutes(1)));
        popupRepository.saveAndFlush(popup(true, now.minusDays(2), now.minusHours(1)));
        popupRepository.saveAndFlush(popup(true, now.plusHours(1), now.plusDays(1)));
        popupRepository.saveAndFlush(popup(false, now.minusDays(1), now.plusDays(1)));

        mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visiblePopupCount").value(2));
    }

    // P14-T9E: "노출 배너"는 공개 Hero와 같은 기준(isVisible)이다.
    @Test
    void dashboardCountsVisibleBannersLikeThePublicHero() throws Exception {
        bannerRepository.saveAndFlush(banner(true));
        bannerRepository.saveAndFlush(banner(true));
        bannerRepository.saveAndFlush(banner(false));

        mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibleBannerCount").value(2));
    }

    @Test
    void dashboardReturnsZeroCountsAndEmptyListsWhenNothingExists() throws Exception {
        mockMvc.perform(admin(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentBoards.length()").value(0))
                .andExpect(jsonPath("$.data.recentPrograms.length()").value(0))
                .andExpect(jsonPath("$.data.programStatus.OPEN").value(0))
                .andExpect(jsonPath("$.data.visiblePopupCount").value(0))
                .andExpect(jsonPath("$.data.visibleBannerCount").value(0))
                .andExpect(jsonPath("$.data.quickMenus.length()").value(6));
    }

    private void setCreatedAt(String table, Long id, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE " + table + " SET created_at = ? WHERE id = ?", createdAt, id);
    }

    private Popup popup(boolean visible, LocalDateTime startDate, LocalDateTime endDate) {
        return Popup.builder()
                .title("팝업")
                .content("<p>팝업</p>")
                .startDate(startDate)
                .endDate(endDate)
                .isVisible(visible)
                .build();
    }

    private Banner banner(boolean visible) {
        return Banner.builder()
                .title("배너")
                .image("/api/files/1")
                .sortOrder(0)
                .isVisible(visible)
                .build();
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
