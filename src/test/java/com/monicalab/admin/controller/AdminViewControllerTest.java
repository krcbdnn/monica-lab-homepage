package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.support.AbstractIntegrationTest;
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
                .andExpect(redirectedUrl("http://localhost/admin/login"));
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
