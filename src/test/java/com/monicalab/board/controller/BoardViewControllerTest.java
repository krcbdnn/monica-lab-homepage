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
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

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

    // P13-T41: 대표 이미지(thumbnail)는 목록/홈/HomePinnedContent 카드 전용 메타데이터로 역할이
    // 분리됐다 - 상세 본문에는 더 이상 자동으로 <img>가 삽입되지 않는다(CKEditor content가 상세
    // 이미지의 실제 표현 영역). thumbnail 필드/데이터 자체는 계속 저장되며(목록/홈에서 계속 사용),
    // 이 테스트는 상세 화면에서만 자동 렌더링이 사라졌음을 검증한다.
    @Test
    void detailDoesNotAutoRenderThumbnailImageOutsideContent() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("대표 이미지 있는 게시글")
                .content("<p>본문 내용</p>")
                .thumbnail("/api/files/1")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#board-detail-content > img")).isEmpty();
        assertThat(document.select(".ckeditor-content").text()).contains("본문 내용");
    }

    // 대표 이미지와 별개로, 본문(CKEditor content)에 삽입된 이미지는 정상적으로 그대로 렌더링돼야
    // 한다 - 상세 화면에는 본문 이미지 1개만 존재하고 thumbnail에 대한 별도 <img>가 추가되지 않는다.
    @Test
    void detailRendersOnlyContentImageWhenBothThumbnailAndContentImageExist() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("본문 이미지 게시글")
                .content("<figure class=\"image\"><img src=\"/api/files/2\"></figure>")
                .thumbnail("/api/files/1")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements images = document.select("#board-detail-content img");

        assertThat(images).hasSize(1);
        assertThat(images.attr("src")).isEqualTo("/api/files/2");
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

    // P14-T2A: legacy(/boards, boardType 없음)에서는 기존 7개 필터가 전부 그대로 노출돼야 한다
    // (기존 URL/필터 호환성 유지).
    @Test
    void legacyContextExposesAllSevenFilterLinks() throws Exception {
        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Elements links = Jsoup.parse(body).select("#board-type-filter .filter-nav__link");
        assertThat(links.eachText()).containsExactly(
                "전체", "공지사항", "갤러리", "자료실", "강의 후기", "수강 후기", "특강 후기");
    }

    // P14-T2A: 소식·자료 context(boardType=NOTICE/GALLERY/ARCHIVE)는 공지사항/갤러리/자료실 3개만
    // 노출하고, "전체"와 강의 후기 계열 필터는 노출하지 않는다.
    @Test
    void newsContextExposesOnlyNoticeGalleryArchiveFilters() throws Exception {
        String body = mockMvc.perform(get("/boards").param("boardType", "GALLERY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Elements links = Jsoup.parse(body).select("#board-type-filter .filter-nav__link");
        assertThat(links.eachText()).containsExactly("공지사항", "갤러리", "자료실");
    }

    // P14-T2A: 강의 후기 context(boardType=REVIEW)는 전체/수강 후기/특강 후기 3개만 노출하고,
    // 공지사항/갤러리/자료실 필터는 노출하지 않는다. programType이 없으면 "전체"가 active다.
    @Test
    void reviewContextExposesOnlyAllCourseSpecialFiltersAndAllLabelIsActiveWithoutProgramType() throws Exception {
        String body = mockMvc.perform(get("/boards").param("boardType", "REVIEW"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements links = document.select("#board-type-filter .filter-nav__link");
        assertThat(links.eachText()).containsExactly("전체", "수강 후기", "특강 후기");

        Elements active = document.select("#board-type-filter .filter-nav__link.is-active");
        assertThat(active).hasSize(1);
        assertThat(active.text()).isEqualTo("전체");
        // keyword가 null이면 Thymeleaf @{}가 파라미터를 생략하지 않고 빈 값(key=)으로 렌더링한다
        // (home/board/list.html 기존 주석과 동일한 실측 동작).
        assertThat(active.attr("href")).isEqualTo("/boards?boardType=REVIEW&keyword=");
    }

    // 강의 후기 context에서 programType=COURSE면 "수강 후기"가 active이고 "전체"는 active가 아니다.
    @Test
    void reviewContextWithCourseProgramTypeMarksCourseReviewActiveNotAll() throws Exception {
        String body = mockMvc.perform(get("/boards").param("boardType", "REVIEW").param("programType", "COURSE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Elements active = Jsoup.parse(body).select("#board-type-filter .filter-nav__link.is-active");
        assertThat(active).hasSize(1);
        assertThat(active.text()).isEqualTo("수강 후기");
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
        // P14-T4B: raw enum(NOTICE)이 아니라 한글 표시명(공지사항)으로 노출된다(domain enum/DB 값 자체는
        // 무변경 - presentation layer에서만 매핑). BoardType 4값 전체 mapping은 아래
        // listShowsKoreanLabelsForAllFourBoardTypesInsteadOfRawEnumNames가 검증한다.
        assertThat(document.select(".board-list__type").text()).isEqualTo("공지사항");
        assertThat(document.select(".board-list__title").text()).isEqualTo("운영 안내");
        // P14-T4B: 작성일시 표기를 Home(#latest-notices)과 동일한 'yyyy.MM.dd'로 통일했다(시간 없음).
        assertThat(document.select(".board-list__date").text()).matches("\\d{4}\\.\\d{2}\\.\\d{2}");
        // P13-T28: 상세 링크에 목록 복귀 상태(boardType/keyword/page)가 쿼리 파라미터로 함께
        // 실리므로 정확히 "/boards/{id}"가 아니라 그 값으로 시작하는지만 확인한다.
        assertThat(document.select(".board-list__link").attr("href")).startsWith("/boards/" + id);
    }

    // P14-T4B: BoardType 4값(NOTICE/GALLERY/ARCHIVE/REVIEW) 모두 raw enum이 아니라 한글 표시명으로
    // 노출되는지 확인한다. GALLERY/REVIEW로 필터링하면 #board-grid로 전환되어 배지 자체가 렌더링되지
    // 않으므로(P13-T27), 4값이 전부 #board-list 배지로 나타나는 legacy(boardType 파라미터 없음) 목록으로
    // 검증한다(legacy 목록은 boardType과 무관하게 모든 글이 #board-list 텍스트 목록에 섞여 나온다).
    @Test
    void listShowsKoreanLabelsForAllFourBoardTypesInsteadOfRawEnumNames() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공지 한글 확인").isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("갤러리 한글 확인").isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE).title("자료실 한글 확인").isPublic(true).build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW).title("강의 후기 한글 확인").isPublic(true).build());

        String body = mockMvc.perform(get("/boards"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String typeText = document.select("#board-list .board-list__type").text();

        assertThat(typeText).contains("공지사항", "갤러리", "자료실", "강의 후기");
        assertThat(typeText).doesNotContain("NOTICE", "GALLERY", "ARCHIVE", "REVIEW");
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
        // P13-T28: 상세 링크에 목록 복귀 상태가 쿼리 파라미터로 함께 실리므로 "startsWith"로 확인한다.
        assertThat(document.select("#board-grid .gallery-card__link").attr("href")).startsWith("/boards/" + id);
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
        assertThat(document.select("#board-grid .gallery-card__link").attr("href")).startsWith("/boards/" + id);
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

    // P13-T28: 상세 → 목록 복귀 시 boardType/keyword/page(canonical, pageJump 아님)를 보존한다.
    // href의 query parameter 순서에 의존하지 않도록 UriComponentsBuilder로 파싱해 값 단위로 검증한다.
    private static MultiValueMap<String, String> queryParamsOf(String href) {
        return UriComponentsBuilder.fromUriString(href).build().getQueryParams();
    }

    @Test
    void listItemLinkInGalleryGridCarriesBoardTypeKeywordAndPageForListReturnState() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("abc 복귀 상태 확인용 갤러리")
                .content("내용")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards")
                        .param("boardType", "GALLERY")
                        .param("keyword", "abc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String href = document.select("#board-grid .gallery-card__link").attr("href");
        assertThat(href).startsWith("/boards/" + id);

        MultiValueMap<String, String> params = queryParamsOf(href);
        assertThat(params.getFirst("boardType")).isEqualTo("GALLERY");
        assertThat(params.getFirst("keyword")).isEqualTo("abc");
        assertThat(params.getFirst("page")).isEqualTo("0");
    }

    @Test
    void listItemLinkInTextListCarriesBoardTypeKeywordAndPageForListReturnState() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("xyz 복귀 상태 확인용 공지")
                .content("내용")
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/boards")
                        .param("boardType", "NOTICE")
                        .param("keyword", "xyz"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String href = document.select(".board-list__link").attr("href");
        assertThat(href).startsWith("/boards/" + id);

        MultiValueMap<String, String> params = queryParamsOf(href);
        assertThat(params.getFirst("boardType")).isEqualTo("NOTICE");
        assertThat(params.getFirst("keyword")).isEqualTo("xyz");
        assertThat(params.getFirst("page")).isEqualTo("0");
    }

    // Thymeleaf @{}는 null 값을 파라미터 생략이 아니라 빈 값(key=)으로 렌더링한다(pagination.html
    // 기존 주석과 동일한 실측 동작). keyword가 없을 때 href에 리터럴 "null" 문자열이 섞이지 않고,
    // keyword 값 자체가 빈 값(공백 없는 실제로 비어있는 값)으로 안전하게 처리되는지만 확인한다.
    @Test
    void listItemLinkKeepsKeywordEmptyRatherThanLiteralNullWhenKeywordIsAbsent() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("keyword 없음 확인").isPublic(true).build());

        String body = mockMvc.perform(get("/boards").param("boardType", "GALLERY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String href = document.select("#board-grid .gallery-card__link").attr("href");

        assertThat(href).doesNotContain("null");
        MultiValueMap<String, String> params = queryParamsOf(href);
        String keywordValue = params.getFirst("keyword");
        assertThat(keywordValue == null || keywordValue.isEmpty()).isTrue();
        assertThat(params.getFirst("boardType")).isEqualTo("GALLERY");
    }

    @Test
    void detailBackToListLinkPreservesBoardTypeKeywordAndPageFromQueryParams() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("상세 복귀 링크 확인").isPublic(true).build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id)
                        .param("boardType", "GALLERY")
                        .param("keyword", "summer")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String href = document.select("a:containsOwn(목록으로)").attr("href");
        assertThat(href).startsWith("/boards");

        MultiValueMap<String, String> params = queryParamsOf(href);
        assertThat(params.getFirst("boardType")).isEqualTo("GALLERY");
        assertThat(params.getFirst("keyword")).isEqualTo("summer");
        assertThat(params.getFirst("page")).isEqualTo("2");
    }

    @Test
    void detailBackToListLinkFallsBackToPlainBoardsWhenNoStateParametersProvided() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("파라미터 없는 상세 접근 확인").isPublic(true).build()).getId();

        String body = mockMvc.perform(get("/boards/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String href = document.select("a:containsOwn(목록으로)").attr("href");
        assertThat(href).isEqualTo("/boards");
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
