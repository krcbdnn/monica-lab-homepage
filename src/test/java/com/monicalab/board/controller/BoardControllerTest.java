package com.monicalab.board.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class BoardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
    }

    @Test
    void publicListReturnsOnlyPublicBoardsWithoutAuthentication() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공개 공지").viewCount(0).isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("비공개 공지").viewCount(0).isPublic(false).build());

        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("공개 공지"));
    }

    @Test
    void publicDetailReturnsOkForPublicBoard() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("공개 갤러리").viewCount(0).isPublic(true).build());

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("공개 갤러리"));
    }

    @Test
    void publicDetailReturnsNotFoundForPrivateBoard() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE).title("비공개 자료").viewCount(0).isPublic(false).build());

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    void publicDetailReturnsNotFoundForNonExistentBoard() throws Exception {
        mockMvc.perform(get("/api/boards/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    void visibilityFalseTransitionExcludesBoardFromPublicListAndDetail() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공개였던 공지").viewCount(0).isPublic(true).build());

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(admin(patch("/api/admin/boards/{id}/visibility", board.getId()))
                        .content("{\"isPublic\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
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
