package com.monicalab.pinned.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.pinned.entity.HomePinnedContent;
import com.monicalab.pinned.entity.HomeTargetType;
import com.monicalab.pinned.repository.HomePinnedContentRepository;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminHomePinnedContentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HomePinnedContentRepository homePinnedContentRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ProgramRepository programRepository;

    @BeforeEach
    void setUp() {
        homePinnedContentRepository.deleteAll();
        boardRepository.deleteAll();
        programRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessToAdminListReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/home-pinned-contents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBoardPinReturns201WithDefaultVisibleTrue() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));

        String body = "{\"targetType\":\"BOARD\",\"targetId\":" + board.getId() + ",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetType").value("BOARD"))
                .andExpect(jsonPath("$.data.targetId").value(board.getId()))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.sourceStatus").value("PUBLIC"))
                .andExpect(jsonPath("$.data.sourceTitle").value("공지 제목"))
                .andExpect(jsonPath("$.data.sourceUrl").value("/admin/boards/" + board.getId() + "/edit"));
    }

    @Test
    void createProgramPinReturns201() throws Exception {
        Program program = programRepository.saveAndFlush(publicProgram("정규 강좌"));

        String body = "{\"targetType\":\"PROGRAM\",\"targetId\":" + program.getId() + ",\"sortOrder\":0,\"visible\":false}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetType").value("PROGRAM"))
                .andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.data.sourceUrl").value("/admin/programs/" + program.getId() + "/edit"));
    }

    @Test
    void createWithNonExistentSourceReturns400() throws Exception {
        String body = "{\"targetType\":\"BOARD\",\"targetId\":999999,\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithPrivateSourceReturns400() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("비공개 공지").isPublic(false).build());

        String body = "{\"targetType\":\"BOARD\",\"targetId\":" + board.getId() + ",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // HomeTargetType은 BOARD/PROGRAM만 허용한다. enum에 없는 값(PAGE 등)을 JSON body로 보내면 Jackson
    // 역직렬화가 실패한다 - GlobalExceptionHandler의 HttpMessageNotReadableException 처리(이번 Task에서
    // 추가)로 400을 반환하는지 검증한다.
    @Test
    void createWithEnumValueOutsideHomeTargetTypeReturns400() throws Exception {
        String body = "{\"targetType\":\"PAGE\",\"targetId\":1,\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createDuplicateTargetReturns409() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        String body = "{\"targetType\":\"BOARD\",\"targetId\":" + board.getId() + ",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOME_PINNED_CONTENT_DUPLICATE"));
    }

    @Test
    void createWithNullSortOrderReturns400() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        String body = "{\"targetType\":\"BOARD\",\"targetId\":" + board.getId() + "}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithNegativeSortOrderReturns400() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        String body = "{\"targetType\":\"BOARD\",\"targetId\":" + board.getId() + ",\"sortOrder\":-1}";

        mockMvc.perform(admin(post("/api/admin/home-pinned-contents")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void listReturnsPinsOrderedBySortOrderThenId() throws Exception {
        Board board1 = boardRepository.saveAndFlush(publicBoard("첫번째"));
        Board board2 = boardRepository.saveAndFlush(publicBoard("두번째"));
        Board board3 = boardRepository.saveAndFlush(publicBoard("세번째"));
        homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board1.getId(), 1));
        homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board2.getId(), 0));
        homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board3.getId(), 0));

        mockMvc.perform(admin(get("/api/admin/home-pinned-contents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetId").value(board2.getId()))
                .andExpect(jsonPath("$.data[1].targetId").value(board3.getId()))
                .andExpect(jsonPath("$.data[2].targetId").value(board1.getId()));
    }

    @Test
    void updateOrderChangesSortOrder() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        HomePinnedContent saved = homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board.getId(), 0));

        mockMvc.perform(admin(patch("/api/admin/home-pinned-contents/{id}/order", saved.getId()))
                        .content("{\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(5));
    }

    @Test
    void updateVisibilityChangesVisibleFlag() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        HomePinnedContent saved = homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board.getId(), 0));

        mockMvc.perform(admin(patch("/api/admin/home-pinned-contents/{id}/visibility", saved.getId()))
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(false));
    }

    @Test
    void deleteRemovesPinButNotSource() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        HomePinnedContent saved = homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board.getId(), 0));

        mockMvc.perform(admin(delete("/api/admin/home-pinned-contents/{id}", saved.getId())))
                .andExpect(status().isNoContent());

        assertThat(homePinnedContentRepository.findById(saved.getId())).isEmpty();
        assertThat(boardRepository.findById(board.getId())).isPresent();
    }

    @Test
    void updateOrderOnNonExistentPinReturns404() throws Exception {
        mockMvc.perform(admin(patch("/api/admin/home-pinned-contents/{id}/order", 999_999L))
                        .content("{\"sortOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HOME_PINNED_CONTENT_NOT_FOUND"));
    }

    @Test
    void adminListShowsPrivateStatusWhenSourceBecomesPrivateAfterPinning() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board.getId(), 0));

        board.updateVisibility(false);
        boardRepository.saveAndFlush(board);

        mockMvc.perform(admin(get("/api/admin/home-pinned-contents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceStatus").value("PRIVATE"))
                .andExpect(jsonPath("$.data[0].sourceUrl").value("/admin/boards/" + board.getId() + "/edit"));
    }

    // 원본이 물리 삭제된 orphan pin은 DELETED로 표시되며, 관리자 목록 조회 자체가 500 없이 정상
    // 응답해야 한다(공개 렌더링은 P13-T38B에서 별도 처리하지만, 관리자 조회 경로도 동일 원칙 적용).
    @Test
    void adminListShowsDeletedStatusWithoutErrorWhenSourceIsPhysicallyDeleted() throws Exception {
        Board board = boardRepository.saveAndFlush(publicBoard("공지 제목"));
        homePinnedContentRepository.saveAndFlush(pin(HomeTargetType.BOARD, board.getId(), 0));

        boardRepository.delete(board);
        boardRepository.flush();

        mockMvc.perform(admin(get("/api/admin/home-pinned-contents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceStatus").value("DELETED"))
                .andExpect(jsonPath("$.data[0].sourceTitle").doesNotExist())
                .andExpect(jsonPath("$.data[0].sourceUrl").doesNotExist());
    }

    private Board publicBoard(String title) {
        return Board.builder().boardType(BoardType.NOTICE).title(title).isPublic(true).build();
    }

    private Program publicProgram(String title) {
        return Program.builder().programType(ProgramType.COURSE).title(title)
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build();
    }

    private HomePinnedContent pin(HomeTargetType targetType, Long targetId, int sortOrder) {
        return HomePinnedContent.builder()
                .targetType(targetType).targetId(targetId).sortOrder(sortOrder).isVisible(true).build();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
