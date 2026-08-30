package com.monicalab.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDateTime;
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
                .boardType(BoardType.NOTICE).title("공개 공지").isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("비공개 공지").isPublic(false).build());

        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("공개 공지"));
    }

    @Test
    void publicDetailReturnsOkForPublicBoard() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("공개 갤러리").isPublic(true).build());

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("공개 갤러리"));
    }

    // P13-T19: 조회수 기능 완전 제거. increaseViewCount 자체가 사라졌으므로 여러 번 조회해도
    // updatedAt이 그대로임을(=UPDATE 미발생) 이 코드 구조에 대한 회귀 테스트로 확인하고,
    // 응답에 viewCount 필드가 더 이상 존재하지 않음을 함께 확인한다.
    @Test
    void publicDetailDoesNotMutateTheBoardRowAndResponseHasNoViewCountField() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("순수 조회 대상 공지").isPublic(true).build());
        LocalDateTime updatedAtBeforeRequests = boardRepository.findById(board.getId())
                .orElseThrow().getUpdatedAt();

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").doesNotExist());

        mockMvc.perform(get("/api/boards/{id}", board.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").doesNotExist());

        LocalDateTime updatedAtAfterRequests = boardRepository.findById(board.getId())
                .orElseThrow().getUpdatedAt();
        assertThat(updatedAtAfterRequests).isEqualTo(updatedAtBeforeRequests);
    }

    // P13-T19: viewCount 필드/컬럼 제거로 더 이상 지원하는 정렬 필드가 아니다. 허용 목록에 없는
    // 정렬 필드에 대한 기존 정책(INVALID_INPUT_VALUE 400)을 그대로 따르는지만 확인한다.
    @Test
    void publicListRejectsSortByViewCountAsInvalidInputValue() throws Exception {
        mockMvc.perform(get("/api/boards").param("sort", "viewCount,DESC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void publicDetailReturnsNotFoundForPrivateBoard() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE).title("비공개 자료").isPublic(false).build());

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
    void publicSearchAppliesBoardTypeAndKeywordAndExcludesPrivateBoards() throws Exception {
        Board matching = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("여름 공지 공개")
                .isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("여름 공지 비공개")
                .isPublic(false).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("여름 갤러리 공개")
                .isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("겨울 공지 공개")
                .isPublic(true).build());

        mockMvc.perform(get("/api/boards")
                        .param("boardType", "NOTICE")
                        .param("keyword", "여름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matching.getId()));
    }

    @Test
    void visibilityFalseTransitionExcludesBoardFromPublicListAndDetail() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공개였던 공지").isPublic(true).build());

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
