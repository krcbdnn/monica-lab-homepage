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

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
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
class AdminBoardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

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
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andExpect(jsonPath("$.data.viewCount").value(0));
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
                .viewCount(0)
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

    @Test
    void adminSearchAppliesBoardTypeAndKeywordAndIncludesPrivateBoards() throws Exception {
        Board matching = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("여름 공지 비공개")
                .viewCount(0).isPublic(false).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("여름 갤러리 공개")
                .viewCount(0).isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("겨울 공지 공개")
                .viewCount(0).isPublic(true).build());

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
                .viewCount(0)
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
                .viewCount(0)
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
                .viewCount(0)
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
                .viewCount(0)
                .isPublic(false)
                .build());

        mockMvc.perform(admin(delete("/api/admin/boards/{id}", board.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(admin(get("/api/admin/boards/{id}", board.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
