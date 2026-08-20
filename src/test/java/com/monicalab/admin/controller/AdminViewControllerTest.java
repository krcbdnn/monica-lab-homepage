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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

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
                "/admin/files");
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

    private Document renderDashboard() throws Exception {
        String body = mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return Jsoup.parse(body);
    }
}
