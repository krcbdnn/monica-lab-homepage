package com.monicalab.program.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
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
class AdminProgramViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProgramRepository programRepository;

    private final List<Long> createdProgramIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        programRepository.deleteAllById(createdProgramIds);
        createdProgramIds.clear();
    }

    @Test
    void listWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/programs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void listWithAuthenticationReturns200AndResolvesToProgramListView() throws Exception {
        mockMvc.perform(get("/admin/programs")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/program/list"));
    }

    @Test
    void newFormWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/programs/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void newFormWithAuthenticationReturns200AndResolvesToProgramFormView() throws Exception {
        mockMvc.perform(get("/admin/programs/new")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/program/form"));
    }

    @Test
    void editFormWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/programs/{id}/edit", 999_999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void editFormWithAuthenticationReturns200AndResolvesToProgramFormViewEvenForNonExistentId() throws Exception {
        mockMvc.perform(get("/admin/programs/{id}/edit", 999_999L)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/program/form"));
    }

    @Test
    void listRendersCommonAdminLayout() throws Exception {
        Document document = render(get("/admin/programs"));

        assertThat(document.select("#admin-header")).isNotEmpty();
        assertThat(document.select("#admin-sidebar")).isNotEmpty();
    }

    @Test
    void formRendersCommonAdminLayout() throws Exception {
        Document document = render(get("/admin/programs/new"));

        assertThat(document.select("#admin-header")).isNotEmpty();
        assertThat(document.select("#admin-sidebar")).isNotEmpty();
    }

    // P14-T9D: 목록 state query(유효/무효/모르는 값 포함)가 붙은 등록/수정 form GET도 200이고, 서버가 렌더하는 취소
    // 링크는 query를 복사하지 않은 고정 fallback(/admin/programs)이다 - 목록 state 반영은 admin-list-state.js가 검증 후에만 한다.
    @Test
    void formIgnoresListStateQueryAndKeepsTheFixedCancelFallback() throws Exception {
        for (String url : new String[] {"/admin/programs/new", "/admin/programs/1/edit"}) {
            Document document = render(get(url)
                    .param("programType", "COURSE")
                    .param("keyword", "a&b")
                    .param("page", "2")
                    .param("returnUrl", "https://evil.example"));

            assertThat(document.select("#program-form-content.admin-form")).hasSize(1);
            assertThat(document.select("#admin-form-heading").text()).isEqualTo("프로그램 등록");
            assertThat(document.select("#admin-form-cancel-link").attr("href")).isEqualTo("/admin/programs");
            assertThat(document.select("script[src=/js/admin/admin-list-state.js]")).hasSize(1);
            assertThat(document.html()).doesNotContain("evil.example");
        }
    }

    // P14-T10: 읽기 전용 관리자 상세 View. 목록/등록/수정 View와 달리 Controller가 getAdminById로 직접
    // 조회해 서버 렌더링한다(ARCHITECTURE.md 읽기 전용 상세 예외).

    @Test
    void detailWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        Long id = saveProgram(true, RecruitStatus.OPEN, "<p>본문</p>");

        mockMvc.perform(get("/admin/programs/{id}", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void detailWithAuthenticationResolvesToProgramDetailViewWithProgramModel() throws Exception {
        Long id = saveProgram(true, RecruitStatus.CLOSED, "<p>본문</p>");

        MvcResult result = mockMvc.perform(get("/admin/programs/{id}", id)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/program/detail"))
                .andExpect(model().attributeExists("renderedContent"))
                .andReturn();

        ProgramResponse program = (ProgramResponse) result.getModelAndView().getModel().get("program");
        assertThat(program.id()).isEqualTo(id);
        assertThat(program.programType()).isEqualTo(ProgramType.SPECIAL);
        assertThat(program.recruitStatus()).isEqualTo(RecruitStatus.CLOSED);
    }

    @Test
    void detailRendersMetadataContentThumbnailAttachmentAndActionLinks() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("관리자 프로그램 상세 테스트")
                .content("<p>본문 문단</p>")
                .thumbnail("/api/files/21")
                .attachment("/api/files/22")
                .googleFormUrl("https://forms.gle/example")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();
        createdProgramIds.add(id);

        Document document = render(get("/admin/programs/{id}", id));

        assertThat(document.select("#admin-program-detail-content h2").text()).isEqualTo("관리자 프로그램 상세 테스트");
        assertThat(document.select("#admin-detail-program-type").text()).isEqualTo("SPECIAL");
        assertThat(document.select("#admin-detail-recruit-status").text()).isEqualTo("OPEN");
        assertThat(document.select("#admin-detail-visibility").text()).isEqualTo("공개");
        // P14-T9C-1: raw 텍스트는 JS 실패 시 fallback으로 유지하고, 관리자 표시명/badge는 data-* 값으로 JS가 만든다.
        assertThat(document.select("#admin-detail-program-type").attr("data-program-type")).isEqualTo("SPECIAL");
        assertThat(document.select("#admin-detail-recruit-status").attr("data-recruit-status")).isEqualTo("OPEN");
        assertThat(document.select("#admin-detail-visibility").attr("data-public")).isEqualTo("true");
        assertThat(document.select("#admin-detail-created-at").text()).isNotBlank();
        assertThat(document.select("#admin-detail-updated-at").text()).isNotBlank();
        Elements applyLink = document.select("#admin-detail-apply-link");
        assertThat(applyLink.attr("href")).isEqualTo("https://forms.gle/example");
        assertThat(applyLink.attr("target")).isEqualTo("_blank");
        assertThat(applyLink.attr("rel")).isEqualTo("noopener noreferrer");
        assertThat(document.select("#admin-detail-thumbnail").attr("src")).isEqualTo("/api/files/21");
        assertThat(document.select(".admin-content-detail .ckeditor-content p").text()).isEqualTo("본문 문단");
        assertThat(document.select("#admin-detail-attachment-link").attr("href")).isEqualTo("/api/files/22");
        assertThat(document.select("#admin-detail-list-link").attr("href")).isEqualTo("/admin/programs");
        assertThat(document.select("#admin-detail-edit-link").attr("href")).isEqualTo("/admin/programs/" + id + "/edit");
    }

    @Test
    void detailShowsPrivateProgramToAdminWithoutPublicPageLinkOrApplyLink() throws Exception {
        Long id = saveProgram(false, RecruitStatus.OPEN, "<p>비공개 본문</p>");

        Document document = render(get("/admin/programs/{id}", id));

        assertThat(document.select("#admin-detail-visibility").text()).isEqualTo("비공개");
        assertThat(document.select("#admin-detail-visibility").attr("data-public")).isEqualTo("false");
        assertThat(document.select(".admin-content-detail .ckeditor-content").text()).isEqualTo("비공개 본문");
        assertThat(document.select("#admin-detail-public-link")).isEmpty();
        assertThat(document.select("#admin-detail-apply-link")).isEmpty();
        assertThat(document.select("#admin-detail-thumbnail")).isEmpty();
        assertThat(document.select("#admin-detail-attachment-link")).isEmpty();
    }

    @Test
    void detailShowsPublicPageLinkForPublicProgramEvenWhenRecruitmentIsClosed() throws Exception {
        Long id = saveProgram(true, RecruitStatus.CLOSED, "<p>본문</p>");

        Document document = render(get("/admin/programs/{id}", id));

        Elements publicLink = document.select("#admin-detail-public-link");
        assertThat(publicLink.attr("href")).isEqualTo("/programs/" + id);
        assertThat(publicLink.attr("target")).isEqualTo("_blank");
        assertThat(publicLink.attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailRendersContentThroughContentLinkRenderer() throws Exception {
        Long id = saveProgram(true, RecruitStatus.OPEN,
                "<p><a href=\"https://example.com\">외부 링크</a> <a href=\"/programs\">내부 링크</a></p>");

        Document document = render(get("/admin/programs/{id}", id));

        Elements external = document.select(".ckeditor-content a[href=https://example.com]");
        assertThat(external.attr("target")).isEqualTo("_blank");
        assertThat(external.attr("rel")).isEqualTo("noopener noreferrer");
        assertThat(document.select(".ckeditor-content a[href=/programs]").attr("target")).isEmpty();
    }

    // P14-T9C-2: 목록 state query(유효/무효/모르는 값 포함)가 붙은 상세 요청도 그대로 조회되고, 서버가 렌더하는
    // "목록으로"는 query를 복사하지 않은 고정 fallback(/admin/programs)이다 - 목록 state 반영은 admin-list-state.js가
    // allowlist 검증 후 브라우저에서만 한다.
    @Test
    void detailIgnoresListStateQueryAndKeepsTheFixedListLinkFallback() throws Exception {
        Long id = saveProgram(true, RecruitStatus.OPEN, "<p>본문</p>");

        Document document = render(get("/admin/programs/{id}", id)
                .param("programType", "COURSE")
                .param("keyword", "a&b=c")
                .param("page", "2")
                .param("returnUrl", "https://evil.example"));

        assertThat(document.select("#admin-program-detail-content h2").text()).isEqualTo("관리자 프로그램 상세 테스트");
        assertThat(document.select("#admin-detail-list-link").attr("href")).isEqualTo("/admin/programs");
        assertThat(document.select("#admin-detail-edit-link").attr("href")).isEqualTo("/admin/programs/" + id + "/edit");
        assertThat(document.select("script[src=/js/admin/admin-list-state.js]")).hasSize(1);
        assertThat(document.html()).doesNotContain("evil.example");
    }

    @Test
    void detailReturnsNotFoundForNonExistentProgram() throws Exception {
        mockMvc.perform(get("/admin/programs/{id}", 999_999_999L)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
    }

    @Test
    void detailRendersCommonAdminLayoutWithProgramsSidebarItemActive() throws Exception {
        Long id = saveProgram(true, RecruitStatus.OPEN, "<p>본문</p>");

        Document document = render(get("/admin/programs/{id}", id));

        assertThat(document.select("#admin-header")).isNotEmpty();
        Elements activeLinks = document.select("#admin-sidebar a.is-active");
        assertThat(activeLinks).hasSize(1);
        assertThat(activeLinks.attr("href")).isEqualTo("/admin/programs");
    }

    private Long saveProgram(boolean isPublic, RecruitStatus recruitStatus, String content) {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("관리자 프로그램 상세 테스트")
                .content(content)
                .recruitStatus(recruitStatus)
                .isPublic(isPublic)
                .build()).getId();
        createdProgramIds.add(id);
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
