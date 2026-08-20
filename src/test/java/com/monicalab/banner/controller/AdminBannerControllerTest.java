package com.monicalab.banner.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AdminBannerControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BannerRepository bannerRepository;

    @BeforeEach
    void setUp() {
        bannerRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessToAdminListReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/banners"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns201WithPostDefaults() throws Exception {
        String body = "{\"title\":\"신규 배너\",\"image\":\"/api/files/1\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/banners")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isVisible").value(false));
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        String body = "{\"title\":\" \",\"image\":\"/api/files/1\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/banners")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithInvalidLinkUrlReturns400() throws Exception {
        String body = "{\"title\":\"신규 배너\",\"image\":\"/api/files/1\",\"sortOrder\":0,"
                + "\"linkUrl\":\"not-a-url\"}";

        mockMvc.perform(admin(post("/api/admin/banners")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithNegativeSortOrderReturns400() throws Exception {
        String body = "{\"title\":\"신규 배너\",\"image\":\"/api/files/1\",\"sortOrder\":-1}";

        mockMvc.perform(admin(post("/api/admin/banners")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void adminListAndDetailReturnInvisibleBanners() throws Exception {
        Banner invisible = bannerRepository.saveAndFlush(Banner.builder()
                .title("비노출 배너").image("/api/files/1").sortOrder(0).isVisible(false).build());

        mockMvc.perform(admin(get("/api/admin/banners")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(admin(get("/api/admin/banners/{id}", invisible.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVisible").value(false))
                .andExpect(jsonPath("$.data.title").value("비노출 배너"));
    }

    @Test
    void adminListDefaultsToSortOrderAscendingAndAcceptsSortParam() throws Exception {
        Banner second = bannerRepository.saveAndFlush(Banner.builder()
                .title("Zulu").image("/api/files/1").sortOrder(1).isVisible(true).build());
        Banner first = bannerRepository.saveAndFlush(Banner.builder()
                .title("Alpha").image("/api/files/1").sortOrder(0).isVisible(true).build());

        mockMvc.perform(admin(get("/api/admin/banners")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data[1].id").value(second.getId()));

        mockMvc.perform(admin(get("/api/admin/banners")).param("sort", "title,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data[1].id").value(first.getId()));
    }

    @Test
    void putUpdatesBannerAndRequiresIsVisible() throws Exception {
        Banner banner = bannerRepository.saveAndFlush(Banner.builder()
                .title("원래 제목").image("/api/files/1").sortOrder(0).isVisible(false).build());

        String validBody = "{\"title\":\"수정된 제목\",\"image\":\"/api/files/2\",\"sortOrder\":1,"
                + "\"isVisible\":true}";

        mockMvc.perform(admin(put("/api/admin/banners/{id}", banner.getId())).content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.isVisible").value(true));

        String missingIsVisibleBody = "{\"title\":\"수정된 제목\",\"image\":\"/api/files/2\",\"sortOrder\":1}";

        mockMvc.perform(admin(put("/api/admin/banners/{id}", banner.getId())).content(missingIsVisibleBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patchVisibilityUpdatesSingleField() throws Exception {
        Banner banner = bannerRepository.saveAndFlush(Banner.builder()
                .title("상태 변경 대상").image("/api/files/1").sortOrder(0).isVisible(false).build());

        mockMvc.perform(admin(patch("/api/admin/banners/{id}/visibility", banner.getId()))
                        .content("{\"isVisible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVisible").value(true));
    }

    @Test
    void patchOrderUpdatesSortOrderAndIsReflectedInListOrder() throws Exception {
        Banner a = bannerRepository.saveAndFlush(Banner.builder()
                .title("A").image("/api/files/1").sortOrder(0).isVisible(true).build());
        Banner b = bannerRepository.saveAndFlush(Banner.builder()
                .title("B").image("/api/files/1").sortOrder(1).isVisible(true).build());

        mockMvc.perform(admin(patch("/api/admin/banners/{id}/order", a.getId()))
                        .content("{\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(5));

        mockMvc.perform(admin(get("/api/admin/banners")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(b.getId()))
                .andExpect(jsonPath("$.data[1].id").value(a.getId()));
    }

    @Test
    void deleteRemovesBannerAndSubsequentGetReturns404() throws Exception {
        Banner banner = bannerRepository.saveAndFlush(Banner.builder()
                .title("삭제 대상").image("/api/files/1").sortOrder(0).isVisible(false).build());

        mockMvc.perform(admin(delete("/api/admin/banners/{id}", banner.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(admin(get("/api/admin/banners/{id}", banner.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BANNER_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
