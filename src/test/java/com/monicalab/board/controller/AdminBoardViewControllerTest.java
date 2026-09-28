package com.monicalab.board.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminBoardViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    private final List<Long> createdBoardIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        boardRepository.deleteAllById(createdBoardIds);
        createdBoardIds.clear();
    }

    @Test
    void listWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/boards"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void listWithAuthenticationReturns200AndResolvesToBoardListView() throws Exception {
        mockMvc.perform(get("/admin/boards")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/board/list"));
    }

    @Test
    void newFormWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/boards/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void newFormWithAuthenticationReturns200AndResolvesToBoardFormView() throws Exception {
        mockMvc.perform(get("/admin/boards/new")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/board/form"));
    }

    @Test
    void editFormWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/boards/{id}/edit", 999_999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void editFormWithAuthenticationReturns200AndResolvesToBoardFormViewEvenForNonExistentId() throws Exception {
        mockMvc.perform(get("/admin/boards/{id}/edit", 999_999L)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/board/form"));
    }

    @Test
    void listRendersCommonAdminLayout() throws Exception {
        Document document = render(get("/admin/boards"));

        assertThat(document.select("#admin-header")).isNotEmpty();
        assertThat(document.select("#admin-sidebar")).isNotEmpty();
    }

    @Test
    void formRendersCommonAdminLayout() throws Exception {
        Document document = render(get("/admin/boards/new"));

        assertThat(document.select("#admin-header")).isNotEmpty();
        assertThat(document.select("#admin-sidebar")).isNotEmpty();
    }

    // P14-T9D: 목록 state query(유효/무효/모르는 값 포함)가 붙은 등록/수정 form GET도 200이고, 서버가 렌더하는 취소
    // 링크는 query를 복사하지 않은 고정 fallback(/admin/boards)이다 - 목록 state 반영은 admin-list-state.js가 검증 후에만 한다.
    @Test
    void formIgnoresListStateQueryAndKeepsTheFixedCancelFallback() throws Exception {
        for (String url : new String[] {"/admin/boards/new", "/admin/boards/1/edit"}) {
            Document document = render(get(url)
                    .param("boardType", "INVALID")
                    .param("keyword", "a&b")
                    .param("page", "-1")
                    .param("returnUrl", "https://evil.example"));

            assertThat(document.select("#board-form-content.admin-form")).hasSize(1);
            assertThat(document.select("#admin-form-heading").text()).isEqualTo("게시글 등록");
            assertThat(document.select("#admin-form-cancel-link").attr("href")).isEqualTo("/admin/boards");
            assertThat(document.select("script[src=/js/admin/admin-list-state.js]")).hasSize(1);
            assertThat(document.html()).doesNotContain("evil.example");
        }
    }

    // P14-T10: 읽기 전용 관리자 상세 View. 목록/등록/수정 View와 달리 Controller가 getAdminById로 직접
    // 조회해 서버 렌더링한다(ARCHITECTURE.md 읽기 전용 상세 예외).

    @Test
    void detailWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        Long id = saveBoard(true, "<p>본문</p>");

        mockMvc.perform(get("/admin/boards/{id}", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void detailWithAuthenticationResolvesToBoardDetailViewWithBoardModel() throws Exception {
        Long id = saveBoard(true, "<p>본문</p>");

        MvcResult result = mockMvc.perform(get("/admin/boards/{id}", id)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/board/detail"))
                .andExpect(model().attributeExists("renderedContent"))
                .andReturn();

        BoardResponse board = (BoardResponse) result.getModelAndView().getModel().get("board");
        assertThat(board.id()).isEqualTo(id);
        assertThat(board.title()).isEqualTo("관리자 상세 테스트");
    }

    @Test
    void detailRendersMetadataContentThumbnailAttachmentAndActionLinks() throws Exception {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW)
                .programType(ProgramType.COURSE)
                .title("관리자 상세 테스트")
                .content("<p>본문 문단</p>")
                .thumbnail("/api/files/11")
                .attachment("/api/files/12")
                .isPublic(true)
                .build()).getId();
        createdBoardIds.add(id);

        Document document = render(get("/admin/boards/{id}", id));

        assertThat(document.select("#admin-board-detail-content h2").text()).isEqualTo("관리자 상세 테스트");
        assertThat(document.select("#admin-detail-board-type").text()).isEqualTo("REVIEW COURSE");
        assertThat(document.select("#admin-detail-visibility").text()).isEqualTo("공개");
        // P14-T9C-1: raw 텍스트는 JS 실패 시 fallback으로 유지하고, 관리자 표시명/badge는 data-* 값으로 JS가 만든다.
        assertThat(document.select("#admin-detail-board-type").attr("data-board-type")).isEqualTo("REVIEW");
        assertThat(document.select("#admin-detail-board-type").attr("data-program-type")).isEqualTo("COURSE");
        assertThat(document.select("#admin-detail-visibility").attr("data-public")).isEqualTo("true");
        assertThat(document.select("#admin-detail-created-at").text()).isNotBlank();
        assertThat(document.select("#admin-detail-updated-at").text()).isNotBlank();
        assertThat(document.select("#admin-detail-thumbnail").attr("src")).isEqualTo("/api/files/11");
        assertThat(document.select(".admin-content-detail .ckeditor-content p").text()).isEqualTo("본문 문단");
        assertThat(document.select("#admin-detail-attachment-link").attr("href")).isEqualTo("/api/files/12");
        assertThat(document.select("#admin-detail-list-link").attr("href")).isEqualTo("/admin/boards");
        assertThat(document.select("#admin-detail-edit-link").attr("href")).isEqualTo("/admin/boards/" + id + "/edit");
    }

    @Test
    void detailShowsPrivateBoardToAdminWithoutPublicPageLink() throws Exception {
        Long id = saveBoard(false, "<p>비공개 본문</p>");

        Document document = render(get("/admin/boards/{id}", id));

        assertThat(document.select("#admin-detail-visibility").text()).isEqualTo("비공개");
        assertThat(document.select("#admin-detail-visibility").attr("data-public")).isEqualTo("false");
        // programType이 없는 게시글은 data-program-type 속성 자체를 렌더하지 않는다.
        assertThat(document.select("#admin-detail-board-type").hasAttr("data-program-type")).isFalse();
        assertThat(document.select(".admin-content-detail .ckeditor-content").text()).isEqualTo("비공개 본문");
        assertThat(document.select("#admin-detail-public-link")).isEmpty();
        assertThat(document.select("#admin-detail-thumbnail")).isEmpty();
        assertThat(document.select("#admin-detail-attachment-link")).isEmpty();
    }

    @Test
    void detailShowsPublicPageLinkInNewTabForPublicBoard() throws Exception {
        Long id = saveBoard(true, "<p>본문</p>");

        Document document = render(get("/admin/boards/{id}", id));

        Elements publicLink = document.select("#admin-detail-public-link");
        assertThat(publicLink.attr("href")).isEqualTo("/boards/" + id);
        assertThat(publicLink.attr("target")).isEqualTo("_blank");
        assertThat(publicLink.attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailRendersContentThroughContentLinkRenderer() throws Exception {
        Long id = saveBoard(true,
                "<p><a href=\"https://example.com\">외부 링크</a> <a href=\"/boards\">내부 링크</a></p>");

        Document document = render(get("/admin/boards/{id}", id));

        Elements external = document.select(".ckeditor-content a[href=https://example.com]");
        assertThat(external.attr("target")).isEqualTo("_blank");
        assertThat(external.attr("rel")).isEqualTo("noopener noreferrer");
        assertThat(document.select(".ckeditor-content a[href=/boards]").attr("target")).isEmpty();
    }

    // P14-T9C-2: 목록 state query(유효/무효/모르는 값 포함)가 붙은 상세 요청도 그대로 조회되고, 서버가 렌더하는
    // "목록으로"는 query를 복사하지 않은 고정 fallback(/admin/boards)이다 - 목록 state 반영은 admin-list-state.js가
    // allowlist 검증 후 브라우저에서만 한다.
    @Test
    void detailIgnoresListStateQueryAndKeepsTheFixedListLinkFallback() throws Exception {
        Long id = saveBoard(true, "<p>본문</p>");

        Document document = render(get("/admin/boards/{id}", id)
                .param("boardType", "INVALID")
                .param("keyword", "<script>alert(1)</script>")
                .param("page", "-1")
                .param("returnUrl", "https://evil.example"));

        assertThat(document.select("#admin-board-detail-content h2").text()).isEqualTo("관리자 상세 테스트");
        assertThat(document.select("#admin-detail-list-link").attr("href")).isEqualTo("/admin/boards");
        assertThat(document.select("#admin-detail-edit-link").attr("href")).isEqualTo("/admin/boards/" + id + "/edit");
        assertThat(document.select("script[src=/js/admin/admin-list-state.js]")).hasSize(1);
        assertThat(document.html()).doesNotContain("evil.example");
    }

    @Test
    void detailReturnsNotFoundForNonExistentBoard() throws Exception {
        mockMvc.perform(get("/admin/boards/{id}", 999_999_999L)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    void detailRendersCommonAdminLayoutWithBoardsSidebarItemActive() throws Exception {
        Long id = saveBoard(true, "<p>본문</p>");

        Document document = render(get("/admin/boards/{id}", id));

        assertThat(document.select("#admin-header")).isNotEmpty();
        Elements activeLinks = document.select("#admin-sidebar a.is-active");
        assertThat(activeLinks).hasSize(1);
        assertThat(activeLinks.attr("href")).isEqualTo("/admin/boards");
    }

    private Long saveBoard(boolean isPublic, String content) {
        Long id = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("관리자 상세 테스트")
                .content(content)
                .isPublic(isPublic)
                .build()).getId();
        createdBoardIds.add(id);
        return id;
    }

    private Document render(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return Jsoup.parse(body);
    }
}
