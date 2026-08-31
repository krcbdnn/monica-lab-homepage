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
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
                .isPublic(true)
                .build()).getId();

        mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("home/board/detail"));
    }

    @Test
    void listExcludesPrivateBoards() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공개 공지").isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("비공개 공지").isPublic(false).build());

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
                .boardType(BoardType.ARCHIVE).title("비공개 자료").isPublic(false).build()).getId();

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
    void detailRendersExternalContentLinkWithTargetBlankAndRelNoopener() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("외부 링크 포함 게시글")
                .content("<p>본문 <a href=\"https://example.com\">외부 링크</a></p>")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements link = document.select("#board-detail-content a[href=https://example.com]");
        assertThat(link).hasSize(1);
        assertThat(link.attr("target")).isEqualTo("_blank");
        assertThat(link.attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailRendersInternalContentLinkWithoutTargetBlank() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("내부 링크 포함 게시글")
                .content("<p>본문 <a href=\"/boards/1\">내부 링크</a></p>")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements link = document.select("#board-detail-content a[href=/boards/1]");
        assertThat(link).hasSize(1);
        assertThat(link.attr("target")).isEmpty();
        assertThat(link.attr("rel")).isEmpty();
    }

    @Test
    void detailShowsAttachmentLinkWhenPresent() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE)
                .title("자료실 첨부")
                .content("내용")
                .attachment("/api/files/1")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#attachment-link")).isNotEmpty();
        assertThat(document.select("#attachment-link").attr("href")).isEqualTo("/api/files/1");
        assertThat(document.select("#attachment-link").attr("target")).isEqualTo("_blank");
        assertThat(document.select("#attachment-link").attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailHidesAttachmentLinkWhenNull() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("첨부 없는 공지")
                .content("내용")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#attachment-link")).isEmpty();
    }

    // P13-T19: 조회수 기능 완전 제거. 상세 페이지의 기존 조회수 메타 요소(뱃지 옆의
    // "조회 N" span)가 더 이상 렌더링되지 않는지 확인한다.
    @Test
    void detailDoesNotRenderViewCountMetaElement() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("조회수 없는 상세 확인")
                .content("내용")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".text-muted.small")).isEmpty();
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

    @Test
    void listUsesDefaultPageSizeOfTenForPublicView() throws Exception {
        seedNBoards(12);

        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#board-list > li.list-group-item")).hasSize(10);
        assertThat(document.select("#next-page")).isNotEmpty();
    }

    @Test
    void boardTypeFilterMarksSelectedOptionActiveAndOthersNot() throws Exception {
        String body = mockMvc.perform(get("/boards").param("boardType", "NOTICE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements active = document.select("#board-type-filter .filter-nav__link.is-active");
        assertThat(active).hasSize(1);
        assertThat(active.text()).isEqualTo("공지사항");
    }

    @Test
    void boardTypeFilterMarksAllActiveWhenBoardTypeIsNull() throws Exception {
        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements active = document.select("#board-type-filter .filter-nav__link.is-active");
        assertThat(active).hasSize(1);
        assertThat(active.text()).isEqualTo("전체");
    }

    @Test
    void listRendersBoardTypeTitleAndCreatedAtInsideTheItemLink() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("운영 안내")
                .content("내용")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#board-list > li.list-group-item > a.board-list__link")).hasSize(1);
        assertThat(document.select(".board-list__type").text()).isEqualTo("NOTICE");
        assertThat(document.select(".board-list__title").text()).isEqualTo("운영 안내");
        assertThat(document.select(".board-list__date").text()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(document.select(".board-list__link").attr("href")).isEqualTo("/boards/" + id);
    }

    // P13-T27: GALLERY/REVIEW는 이미지 중심 게시판 타입이라 메인 페이지(#latest-gallery/#latest-reviews)와
    // 동일한 .gallery-grid/.gallery-card 마크업(#board-grid)으로 표시하고, 기존 텍스트 목록(#board-list)은
    // 렌더링하지 않는다. NOTICE/ARCHIVE/전체는 반대로 #board-list만 렌더링하고 #board-grid는 렌더링하지 않는다.
    @Test
    void listRendersGalleryGridInsteadOfTextListWhenBoardTypeIsGallery() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("갤러리 그리드 확인")
                .content("내용")
                .thumbnail("/api/files/11")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards").param("boardType", "GALLERY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("ul#board-grid.gallery-grid")).isNotEmpty();
        assertThat(document.select("#board-grid > li.gallery-card > a.gallery-card__link")).hasSize(1);
        assertThat(document.select("#board-grid .gallery-card__thumb img").attr("src")).isEqualTo("/api/files/11");
        assertThat(document.select("#board-grid .gallery-card__thumb img").attr("loading")).isEqualTo("lazy");
        assertThat(document.select("#board-grid .gallery-card__title").text()).isEqualTo("갤러리 그리드 확인");
        assertThat(document.select("#board-grid .gallery-card__link").attr("href")).isEqualTo("/boards/" + id);
        assertThat(document.select("#board-list")).isEmpty();
    }

    @Test
    void listRendersGalleryGridInsteadOfTextListWhenBoardTypeIsReview() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW)
                .title("강의 후기 그리드 확인")
                .content("내용")
                .thumbnail("/api/files/12")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards").param("boardType", "REVIEW"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("ul#board-grid.gallery-grid")).isNotEmpty();
        assertThat(document.select("#board-grid .gallery-card__thumb img").attr("src")).isEqualTo("/api/files/12");
        assertThat(document.select("#board-grid .gallery-card__title").text()).isEqualTo("강의 후기 그리드 확인");
        assertThat(document.select("#board-grid .gallery-card__link").attr("href")).isEqualTo("/boards/" + id);
        assertThat(document.select("#board-list")).isEmpty();
    }

    @Test
    void listKeepsTextListInsteadOfGalleryGridWhenBoardTypeIsNoticeArchiveOrAll() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공지 텍스트 목록 유지 확인").isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE).title("자료실 텍스트 목록 유지 확인").isPublic(true).build());

        for (String boardType : new String[] {"NOTICE", "ARCHIVE", null}) {
            var requestBuilder = get("/boards");
            if (boardType != null) {
                requestBuilder = requestBuilder.param("boardType", boardType);
            }
            String body = mockMvc.perform(requestBuilder)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            Document document = Jsoup.parse(body);
            assertThat(document.select("ul#board-list.list-group")).isNotEmpty();
            assertThat(document.select("#board-grid")).isEmpty();
        }
    }

    @Test
    void listShowsPlaceholderWhenThumbnailIsNull() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("썸네일 null 게시글")
                .content("내용")
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/boards").param("boardType", "GALLERY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#board-grid .gallery-card__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#board-grid .gallery-card__thumb img")).isEmpty();
    }

    @Test
    void listShowsPlaceholderWhenThumbnailIsEmptyString() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("썸네일 빈 문자열 게시글")
                .content("내용")
                .thumbnail("")
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/boards").param("boardType", "GALLERY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#board-grid .gallery-card__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#board-grid .gallery-card__thumb img")).isEmpty();
    }

    @Test
    void listShowsPlaceholderWhenThumbnailIsWhitespaceOnly() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("썸네일 공백 문자열 게시글")
                .content("내용")
                .thumbnail("   ")
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/boards").param("boardType", "GALLERY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#board-grid .gallery-card__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#board-grid .gallery-card__thumb img")).isEmpty();
    }

    // P13-T19: 조회수 기능 완전 제거. 목록 아이템의 조회수 표시 요소(.board-list__views)가
    // 남아있지 않은지 확인한다(과도하게 넓은 "조회"라는 단어 전체 페이지 텍스트 검사 대신,
    // 실제로 존재했던 형식/요소만 targeted로 확인).
    @Test
    void listDoesNotRenderViewCountElement() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("조회수 없는 목록 확인")
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".board-list__views")).isEmpty();
    }

    @Test
    void paginationShowsUpToTenPageNumbersInFirstGroup() throws Exception {
        // size=1로 줄여 totalPages=15를 15건만으로 값싸게 재현한다(기본 size=10이면 141건이 필요).
        seedNBoards(15);

        String body = mockMvc.perform(get("/boards").param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements numbers = document.select(".pagination-bar__number");
        assertThat(numbers).hasSize(10);
        assertThat(numbers.first().text()).isEqualTo("1");
        assertThat(numbers.last().text()).isEqualTo("10");
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("1");
    }

    @Test
    void paginationSwitchesToNextGroupAfterPageTen() throws Exception {
        seedNBoards(15);

        String body = mockMvc.perform(get("/boards").param("size", "1").param("page", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements numbers = document.select(".pagination-bar__number");
        assertThat(numbers.first().text()).isEqualTo("11");
        assertThat(numbers.last().text()).isEqualTo("15");
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("11");
    }

    @Test
    void pageJumpNavigatesToRequestedOneBasedPage() throws Exception {
        seedNBoards(15);

        String body = mockMvc.perform(get("/boards").param("size", "1").param("pageJump", "3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("3");
    }

    @Test
    void pageJumpClampsValueOfOneOrLessToFirstPage() throws Exception {
        seedNBoards(15);

        String body = mockMvc.perform(get("/boards").param("size", "1").param("pageJump", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("1");
    }

    @Test
    void pageJumpClampsValueBeyondTotalPagesToLastValidPage() throws Exception {
        seedNBoards(3);

        String body = mockMvc.perform(get("/boards").param("size", "1").param("pageJump", "999"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("3");
    }

    @Test
    void pageJumpWithNonNumericValueIsIgnoredSafelyWithoutError() throws Exception {
        seedNBoards(3);

        mockMvc.perform(get("/boards").param("pageJump", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/board/list"));
    }

    @Test
    void pageJumpIsSafeWhenThereIsNoData() throws Exception {
        mockMvc.perform(get("/boards").param("pageJump", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/board/list"));
    }

    private void seedTwoSummerNoticeBoards() {
        for (int i = 0; i < 2; i++) {
            boardRepository.saveAndFlush(Board.builder()
                    .boardType(BoardType.NOTICE)
                    .title("summer notice " + i)
                    .content("summer content")
                    .isPublic(true)
                    .build());
        }
    }

    private void seedNBoards(int count) {
        List<Board> boards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boards.add(Board.builder()
                    .boardType(BoardType.NOTICE)
                    .title("board " + i)
                    .isPublic(true)
                    .build());
        }
        boardRepository.saveAll(boards);
        boardRepository.flush();
    }
}
