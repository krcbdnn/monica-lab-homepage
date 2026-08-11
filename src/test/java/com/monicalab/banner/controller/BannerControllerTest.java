package com.monicalab.banner.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.banner.entity.Banner;
import com.monicalab.banner.repository.BannerRepository;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class BannerControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BannerRepository bannerRepository;

    @BeforeEach
    void setUp() {
        bannerRepository.deleteAll();
    }

    @Test
    void publicListReturnsOnlyVisibleBannersWithoutAuthentication() throws Exception {
        bannerRepository.saveAndFlush(Banner.builder()
                .title("노출 배너").image("/api/files/1").sortOrder(0).isVisible(true).build());
        bannerRepository.saveAndFlush(Banner.builder()
                .title("비노출 배너").image("/api/files/1").sortOrder(1).isVisible(false).build());

        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("노출 배너"));
    }

    @Test
    void publicListOrdersBySortOrderAscThenCreatedAtDesc() throws Exception {
        Banner second = bannerRepository.saveAndFlush(Banner.builder()
                .title("두번째").image("/api/files/1").sortOrder(1).isVisible(true).build());
        Banner first = bannerRepository.saveAndFlush(Banner.builder()
                .title("첫번째").image("/api/files/1").sortOrder(0).isVisible(true).build());

        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data[1].id").value(second.getId()));
    }

    @Test
    void visibilityFalseTransitionExcludesBannerFromPublicList() throws Exception {
        Banner banner = bannerRepository.saveAndFlush(Banner.builder()
                .title("노출이었던 배너").image("/api/files/1").sortOrder(0).isVisible(true).build());

        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(admin(patch("/api/admin/banners/{id}/visibility", banner.getId()))
                        .content("{\"isVisible\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
