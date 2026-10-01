package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.support.PublicHeadAssertions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void dashboardWithAuthenticationReturns200AndResolvesToAdminDashboardView() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    @Test
    void dashboardRendersCommonHeaderWithAdminNamePlaceholder() throws Exception {
        Document document = renderDashboard();

        assertThat(document.select("#admin-header")).isNotEmpty();
        assertThat(document.select("#admin-name")).isNotEmpty();
    }

    @Test
    void dashboardRendersSidebarWithAllAdminDomainLinks() throws Exception {
        Document document = renderDashboard();

        assertThat(document.select("#admin-sidebar")).isNotEmpty();
        List<String> hrefs = document.select("#admin-sidebar a").eachAttr("href");
        assertThat(hrefs).containsExactly(
                "/admin/dashboard",
                "/admin/pages",
                "/admin/programs",
                "/admin/boards",
                "/admin/banners",
                "/admin/popups",
                "/admin/files",
                "/admin/menus",
                "/admin/home-pinned-contents",
                "/admin/theme");
    }

    @Test
    void dashboardLoadsCommonFetchBeforeAdminHeaderScript() throws Exception {
        Document document = renderDashboard();

        Elements scripts = document.select("script[src]");
        List<String> srcs = scripts.eachAttr("src");

        int commonFetchIndex = srcs.indexOf("/js/admin/common-fetch.js");
        int adminHeaderIndex = srcs.indexOf("/js/admin/admin-header.js");

        assertThat(commonFetchIndex).isNotEqualTo(-1);
        assertThat(adminHeaderIndex).isNotEqualTo(-1);
        assertThat(commonFetchIndex).isLessThan(adminHeaderIndex);
    }

    // P14-T9A: list/new/edit가 같은 sidebar 항목을 공유해야 하는 대표 route들. href/label/순서는
    // dashboardRendersSidebarWithAllAdminDomainLinks가 이미 고정하고 있으므로 여기서는 "정확히 그
    // href 하나만 active인지"만 검증한다.
    static Stream<Arguments> activeNavigationCases() {
        return Stream.of(
                Arguments.of("/admin/dashboard", "/admin/dashboard"),
                Arguments.of("/admin/pages", "/admin/pages"),
                Arguments.of("/admin/programs", "/admin/programs"),
                Arguments.of("/admin/programs/new", "/admin/programs"),
                Arguments.of("/admin/boards", "/admin/boards"),
                Arguments.of("/admin/boards/new", "/admin/boards"),
                Arguments.of("/admin/banners", "/admin/banners"),
                Arguments.of("/admin/popups", "/admin/popups"),
                Arguments.of("/admin/files", "/admin/files"),
                Arguments.of("/admin/menus", "/admin/menus"),
                Arguments.of("/admin/menus/new", "/admin/menus"),
                Arguments.of("/admin/home-pinned-contents", "/admin/home-pinned-contents"),
                Arguments.of("/admin/theme", "/admin/theme"));
    }

    @ParameterizedTest
    @MethodSource("activeNavigationCases")
    void sidebarMarksExactlyOneMatchingItemActive(String path, String expectedActiveHref) throws Exception {
        Document document = render(get(path));

        Elements activeLinks = document.select("#admin-sidebar a.is-active");
        assertThat(activeLinks).hasSize(1);
        assertThat(activeLinks.attr("href")).isEqualTo(expectedActiveHref);
    }

    @Test
    void adminHeaderContainsLogoutButtonAndMobileSidebarToggle() throws Exception {
        Document document = renderDashboard();

        assertThat(document.select("#admin-logout-button")).isNotEmpty();
        Elements toggle = document.select("#admin-sidebar-toggle");
        assertThat(toggle).isNotEmpty();
        assertThat(toggle.attr("aria-controls")).isEqualTo("admin-sidebar");
        assertThat(toggle.attr("aria-expanded")).isEqualTo("false");
    }

    // P15-T6: header의 로그아웃 옆에 본인 비밀번호 변경 진입 링크가 있다(sidebar 항목은 추가하지 않는다).
    @Test
    void adminHeaderContainsPasswordChangeLinkNextToLogoutAndSidebarDoesNot() throws Exception {
        Document document = renderDashboard();

        Elements link = document.select("#admin-header .admin-header__end a#admin-password-link");
        assertThat(link).hasSize(1);
        assertThat(link.attr("href")).isEqualTo("/admin/password");
        assertThat(link.text()).isEqualTo("비밀번호 변경");
        assertThat(link.first().nextElementSibling().id()).isEqualTo("admin-logout-button");
        assertThat(document.select("#admin-sidebar a[href=/admin/password]")).isEmpty();
    }

    @Test
    void passwordPageWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    // P15-T6: 비밀번호 변경 화면은 현재/새/새 확인 3개 입력(autocomplete 지정)과 정책 안내를 렌더링하고,
    // sidebar에는 active 항목이 없다(sidebar 메뉴가 아닌 화면).
    @Test
    void passwordPageRendersThreePasswordFieldsWithPolicyHelp() throws Exception {
        mockMvc.perform(get("/admin/password")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/password"));

        Document document = render(get("/admin/password"));

        assertThat(document.select("#passwordForm input[type=password]")).hasSize(3);
        assertThat(document.select("#currentPassword").attr("autocomplete")).isEqualTo("current-password");
        assertThat(document.select("#newPassword").attr("autocomplete")).isEqualTo("new-password");
        assertThat(document.select("#newPasswordConfirm").attr("autocomplete")).isEqualTo("new-password");
        assertThat(document.select("label[for=newPasswordConfirm]").text()).isEqualTo("새 비밀번호 확인");
        assertThat(document.select("#newPasswordHelp").text())
                .contains("8자 이상 64자 이하", "2종류 이상", "공백과 한글");
        assertThat(document.select("#saveButton").text()).isEqualTo("변경");
        assertThat(document.select("#admin-logout-button")).isNotEmpty();
        assertThat(document.select("#admin-sidebar a.is-active")).isEmpty();
    }

    // P15-T7B: 관리자 화면(공통 layout/로그인)은 별도 <head>라 공개 layout의 OG/apple-touch-icon 메타가 들어가지 않는다.
    @Test
    void adminPagesDoNotRenderPublicBrandMetadata() throws Exception {
        PublicHeadAssertions.assertNoPublicBrandMeta(renderDashboard());
        PublicHeadAssertions.assertNoPublicBrandMeta(render(get("/admin/password")));

        String login = mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        PublicHeadAssertions.assertNoPublicBrandMeta(Jsoup.parse(login));
    }

    private Document render(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return Jsoup.parse(body);
    }

    private Document renderDashboard() throws Exception {
        String body = mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return Jsoup.parse(body);
    }
}
