package com.monicalab.board.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminBoardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessToAdminListReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/boards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns201WithPostDefaults() throws Exception {
        String body = "{\"boardType\":\"NOTICE\",\"title\":\"신규 공지\"}";

        mockMvc.perform(admin(post("/api/admin/boards")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        String body = "{\"boardType\":\"NOTICE\",\"title\":\" \"}";

        mockMvc.perform(admin(post("/api/admin/boards")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createSanitizesScriptTagInContent() throws Exception {
        String body = "{\"boardType\":\"NOTICE\",\"title\":\"신규 공지\","
                + "\"content\":\"<p>hello</p><script>alert(1)</script>\"}";

        mockMvc.perform(admin(post("/api/admin/boards")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.containsString("hello")))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script"))));
    }

    @Test
    void adminListAndDetailReturnPrivateBoards() throws Exception {
        Board privateBoard = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE)
                .title("비공개 자료")
                .isPublic(false)
                .build());

        mockMvc.perform(admin(get("/api/admin/boards")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(admin(get("/api/admin/boards/{id}", privateBoard.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andExpect(jsonPath("$.data.title").value("비공개 자료"));
    }

    // P13-T19: viewCount 필드/컬럼 제거로 더 이상 지원하는 정렬 필드가 아니다. 허용 목록에 없는
    // 정렬 필드에 대한 기존 정책(INVALID_INPUT_VALUE 400)을 그대로 따르는지만 확인한다.
    @Test
    void adminListRejectsSortByViewCountAsInvalidInputValue() throws Exception {
        mockMvc.perform(admin(get("/api/admin/boards")).param("sort", "viewCount,DESC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void adminSearchAppliesBoardTypeAndKeywordAndIncludesPrivateBoards() throws Exception {
        Board matching = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("여름 공지 비공개")
                .isPublic(false).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("여름 갤러리 공개")
                .isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("겨울 공지 공개")
                .isPublic(true).build());

        mockMvc.perform(admin(get("/api/admin/boards"))
                        .param("boardType", "NOTICE")
                        .param("keyword", "여름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.data.content[0].isPublic").value(false));
    }

    @Test
    void putUpdatesBoardAndRequiresIsPublic() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("원래 제목")
                .isPublic(false)
                .build());

        String validBody = "{\"boardType\":\"NOTICE\",\"title\":\"수정된 제목\",\"isPublic\":true}";

        mockMvc.perform(admin(put("/api/admin/boards/{id}", board.getId())).content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.isPublic").value(true));

        String missingIsPublicBody = "{\"boardType\":\"NOTICE\",\"title\":\"수정된 제목\"}";

        mockMvc.perform(admin(put("/api/admin/boards/{id}", board.getId())).content(missingIsPublicBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void putWithBlankTitleReturns400EvenWithIsPublicProvided() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("원래 제목")
                .isPublic(false)
                .build());

        String blankTitleBody = "{\"boardType\":\"NOTICE\",\"title\":\" \",\"isPublic\":true}";

        mockMvc.perform(admin(put("/api/admin/boards/{id}", board.getId())).content(blankTitleBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patchVisibilityUpdatesSingleField() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("상태 변경 대상")
                .isPublic(false)
                .build());

        mockMvc.perform(admin(patch("/api/admin/boards/{id}/visibility", board.getId()))
                        .content("{\"isPublic\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    @Test
    void deleteRemovesBoardAndSubsequentGetReturns404() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("삭제 대상")
                .isPublic(false)
                .build());

        mockMvc.perform(admin(delete("/api/admin/boards/{id}", board.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(admin(get("/api/admin/boards/{id}", board.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    // P13-T16: BoardType.REVIEW는 기존 Board CRUD/공개 조회 로직을 그대로 재사용하므로(타입 분기 없음),
    // 등록/수정/삭제/공개 여부 자체는 위의 기존 테스트들이 이미 모든 BoardType에 대해 검증하고 있다.
    // 이 테스트는 그 전제 위에서 "관리자가 REVIEW로 등록 -> 비공개 상태에서는 공개 목록에 없음 ->
    // 공개 전환 -> 공개 목록/상세에 노출"이라는 새로 생긴 핵심 경로 하나만 스모크로 확인한다.
    @Test
    void reviewCreatedByAdminBecomesVisibleInPublicListAndDetailAfterVisibilityIsToggled() throws Exception {
        String createBody = "{\"boardType\":\"REVIEW\",\"title\":\"강의 후기 스모크\",\"thumbnail\":\"/api/files/9\"}";

        String responseBody = mockMvc.perform(admin(post("/api/admin/boards")).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.boardType").value("REVIEW"))
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Long id = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        mockMvc.perform(get("/api/boards").param("boardType", "REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(admin(patch("/api/admin/boards/{id}/visibility", id))
                        .content("{\"isPublic\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true));

        mockMvc.perform(get("/api/boards").param("boardType", "REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id));

        mockMvc.perform(get("/api/boards/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("강의 후기 스모크"))
                .andExpect(jsonPath("$.data.thumbnail").value("/api/files/9"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
