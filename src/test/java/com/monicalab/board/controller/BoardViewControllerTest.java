package com.monicalab.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class BoardViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();
    }

    @Test
    void listMapsToBoardsPathAndResolvesToHomeBoardListView() throws Exception {
        mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/board/list"));
    }

    @Test
    void detailMapsToBoardsIdPathAndResolvesToHomeBoardDetailView() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("상세보기 테스트")
                .content("내용")
                .viewCount(0)
                .isPublic(true)
                .build()).getId();

        mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("home/board/detail"));
    }

    @Test
    void listExcludesPrivateBoards() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공개 공지").viewCount(0).isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("비공개 공지").viewCount(0).isPublic(false).build());

        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#board-list li").text()).contains("공개 공지");
        assertThat(document.select("#board-list li").text()).doesNotContain("비공개 공지");
    }

    @Test
    void detailReturnsNotFoundForPrivateBoard() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE).title("비공개 자료").viewCount(0).isPublic(false).build()).getId();

        mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    void detailAppliesLazyLoadingToThumbnailImage() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("썸네일 있는 게시글")
                .content("내용")
                .thumbnail("/api/files/1")
                .viewCount(0)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("img").attr("src")).isEqualTo("/api/files/1");
        assertThat(document.select("img").attr("loading")).isEqualTo("lazy");
    }

    @Test
    void detailShowsAttachmentLinkWhenPresent() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE)
                .title("자료실 첨부")
                .content("내용")
                .attachment("/api/files/1")
                .viewCount(0)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#attachment-link")).isNotEmpty();
        assertThat(document.select("#attachment-link").attr("href")).isEqualTo("/api/files/1");
    }

    @Test
    void detailHidesAttachmentLinkWhenNull() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("첨부 없는 공지")
                .content("내용")
                .viewCount(0)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#attachment-link")).isEmpty();
    }

    @Test
    void nextPageLinkPreservesBoardTypeAndKeywordOnFirstPage() throws Exception {
        seedTwoSummerNoticeBoards();

        String body = mockMvc.perform(get("/boards")
                        .param("boardType", "NOTICE")
                        .param("keyword", "summer")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#prev-page")).isEmpty();
        String nextHref = document.select("#next-page").attr("href");
        assertThat(nextHref).contains("page=1", "size=1", "boardType=NOTICE", "keyword=summer");
    }

    @Test
    void prevPageLinkPreservesBoardTypeAndKeywordOnSecondPage() throws Exception {
        seedTwoSummerNoticeBoards();

        String body = mockMvc.perform(get("/boards")
                        .param("boardType", "NOTICE")
                        .param("keyword", "summer")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#next-page")).isEmpty();
        String prevHref = document.select("#prev-page").attr("href");
        assertThat(prevHref).contains("page=0", "size=1", "boardType=NOTICE", "keyword=summer");
    }

    private void seedTwoSummerNoticeBoards() {
        for (int i = 0; i < 2; i++) {
            boardRepository.saveAndFlush(Board.builder()
                    .boardType(BoardType.NOTICE)
                    .title("summer notice " + i)
                    .content("summer content")
                    .viewCount(0)
                    .isPublic(true)
                    .build());
        }
    }
}
