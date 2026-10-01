package com.monicalab.theme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.theme.dto.SiteThemeSettingView;
import com.monicalab.theme.entity.AccentPreset;
import com.monicalab.theme.entity.SiteThemeSetting;
import com.monicalab.theme.repository.SiteThemeSettingRepository;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ThemeControllerAdviceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteThemeSettingRepository siteThemeSettingRepository;

    @BeforeEach
    void setUp() {
        siteThemeSettingRepository.deleteAll();
    }

    @Test
    void siteThemeModelAttributeIsAppliedToAllFourPublicViewControllers() throws Exception {
        for (String path : new String[] {"/", "/pages/INTRODUCTION", "/programs", "/boards"}) {
            MvcResult result = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getModelAndView()).isNotNull();
            assertThat(result.getModelAndView().getModel()).containsKey("siteTheme");
            assertThat(result.getModelAndView().getModel().get("siteTheme")).isInstanceOf(SiteThemeSettingView.class);

            Document document = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(document.select("html").attr("data-theme")).isEqualTo("TERRACOTTA");
        }
    }

    @Test
    void siteThemeModelAttributeIsNotAppliedToAdminViewControllers() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/banners")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("siteTheme");
    }

    @Test
    void dataThemeReflectsStoredBurgundyAccentPreset() throws Exception {
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.BURGUNDY)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());

        Document document = renderHome();

        assertThat(document.select("html").attr("data-theme")).isEqualTo("BURGUNDY");
    }

    @Test
    void dataThemeReflectsStoredForestAccentPreset() throws Exception {
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.FOREST)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());

        Document document = renderHome();

        assertThat(document.select("html").attr("data-theme")).isEqualTo("FOREST");
    }

    // row가 없을 때도 public render read path가 DB write를 만들지 않는지(=fallback이 여전히 read-only인지)
    // 함께 고정한다. Service 레벨의 동일 계약은 SiteThemeSettingServiceTest가 이미 보호하고 있지만, 이
    // Advice를 통과하는 실제 public 요청 경로에서도 회귀가 없는지 별도로 확인한다.
    @Test
    void dataThemeFallsBackToTerracottaWhenRowMissingAndCausesNoWrite() throws Exception {
        Document document = renderHome();

        assertThat(document.select("html").attr("data-theme")).isEqualTo("TERRACOTTA");
        assertThat(siteThemeSettingRepository.count()).isZero();
    }

    private Document renderHome() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Jsoup.parse(body);
    }
}
