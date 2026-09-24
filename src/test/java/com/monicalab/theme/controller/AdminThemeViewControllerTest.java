package com.monicalab.theme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.theme.entity.AccentPreset;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminThemeViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getWithoutAuthenticationRedirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/theme"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void getWithAuthenticationReturns200AndResolvesToThemeFormView() throws Exception {
        mockMvc.perform(get("/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/theme/form"));
    }

    @Test
    void modelContainsAccentPresetsIncludingTerracotta() throws Exception {
        mockMvc.perform(get("/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("accentPresets"))
                .andExpect(model().attribute("accentPresets", AccentPreset.values()));
    }

    @Test
    void formRendersCommonAdminLayout() throws Exception {
        Document document = render(get("/admin/theme"));

        assertThat(document.select("#admin-header")).isNotEmpty();
        assertThat(document.select("#admin-sidebar")).isNotEmpty();
    }

    @Test
    void sidebarContainsThemeSettingsLink() throws Exception {
        Document document = render(get("/admin/theme"));

        assertThat(document.select("#admin-sidebar a[href=/admin/theme]")).isNotEmpty();
    }

    @Test
    void formContainsAccentPresetRadioAndVisibilityCheckboxes() throws Exception {
        Document document = render(get("/admin/theme"));

        assertThat(document.select("input[type=radio][name=accentPreset][value=TERRACOTTA]")).isNotEmpty();
        assertThat(document.select("#showPinned[type=checkbox]")).isNotEmpty();
        assertThat(document.select("#showPrograms[type=checkbox]")).isNotEmpty();
        assertThat(document.select("#showReviews[type=checkbox]")).isNotEmpty();
        assertThat(document.select("#showNotices[type=checkbox]")).isNotEmpty();
        assertThat(document.select("#showGallery[type=checkbox]")).isNotEmpty();
    }

    private Document render(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return Jsoup.parse(body);
    }
}
