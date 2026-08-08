package com.monicalab.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.page.config.PageInitializer;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.repository.PageRepository;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PageIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageInitializer pageInitializer;

    @Test
    void fixedPagesAreInitializedExactlyOncePerType() {
        assertThat(pageRepository.count()).isEqualTo(4);
        for (PageType pageType : PageType.values()) {
            assertThat(pageRepository.existsByPageType(pageType)).isTrue();
        }
    }

    @Test
    void reinitializingDoesNotCreateDuplicates() {
        long before = pageRepository.count();

        pageInitializer.run(new DefaultApplicationArguments());

        long after = pageRepository.count();
        assertThat(after).isEqualTo(before);
        assertThat(after).isEqualTo(4);
    }

    @Test
    void adminCanRetrieveFixedPage() throws Exception {
        mockMvc.perform(get("/api/admin/pages/GREETING")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageType").value("GREETING"))
                .andExpect(jsonPath("$.data.title").value("인사말"));
    }

    @Test
    void unauthenticatedAccessToAdminPageApiReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/pages/GREETING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPageTypeReturnsInvalidInputValue() throws Exception {
        mockMvc.perform(get("/api/admin/pages/NOT_A_TYPE")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void adminUpdatePersistsSanitizedContent() throws Exception {
        String requestBody = "{\"title\":\"새 인사말\",\"content\":\"<p>hello</p><script>alert(1)</script>\"}";

        mockMvc.perform(put("/api/admin/pages/INTRODUCTION")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("새 인사말"))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.containsString("hello")))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script"))))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("alert(1)"))));

        mockMvc.perform(get("/api/admin/pages/INTRODUCTION")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("새 인사말"))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script"))));
    }
}
