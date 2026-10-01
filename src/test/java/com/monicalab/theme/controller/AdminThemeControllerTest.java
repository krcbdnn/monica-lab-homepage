package com.monicalab.theme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.theme.entity.AccentPreset;
import com.monicalab.theme.entity.SiteThemeSetting;
import com.monicalab.theme.repository.SiteThemeSettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminThemeControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteThemeSettingRepository siteThemeSettingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        siteThemeSettingRepository.deleteAll();
        siteThemeSettingRepository.save(SiteThemeSetting.builder()
                .settingKey(SiteThemeSetting.SITE_THEME_KEY)
                .accentPreset(AccentPreset.TERRACOTTA)
                .showPinned(true)
                .showPrograms(true)
                .showReviews(true)
                .showNotices(true)
                .showGallery(true)
                .build());
    }

    @Test
    void adminCanRetrieveCurrentSetting() throws Exception {
        mockMvc.perform(get("/api/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accentPreset").value("TERRACOTTA"))
                .andExpect(jsonPath("$.data.showPinned").value(true))
                .andExpect(jsonPath("$.data.showPrograms").value(true))
                .andExpect(jsonPath("$.data.showReviews").value(true))
                .andExpect(jsonPath("$.data.showNotices").value(true))
                .andExpect(jsonPath("$.data.showGallery").value(true));
    }

    @Test
    void unauthenticatedGetReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/theme"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedPutReturns401() throws Exception {
        mockMvc.perform(put("/api/admin/theme")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdateReturns200AndPersistsValues() throws Exception {
        mockMvc.perform(put("/api/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showPinned").value(false))
                .andExpect(jsonPath("$.data.showGallery").value(false));

        mockMvc.perform(get("/api/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showPinned").value(false))
                .andExpect(jsonPath("$.data.showPrograms").value(true))
                .andExpect(jsonPath("$.data.showReviews").value(false))
                .andExpect(jsonPath("$.data.showNotices").value(true))
                .andExpect(jsonPath("$.data.showGallery").value(false));

        assertThat(siteThemeSettingRepository.count()).isEqualTo(1);
    }

    @Test
    void missingAccentPresetReturnsInvalidInputValue() throws Exception {
        performPutAndExpectInvalidInputValue(requestJsonOmitting("accentPreset"));
    }

    @Test
    void invalidAccentPresetStringReturnsInvalidInputValue() throws Exception {
        Map<String, Object> body = validRequestMap();
        body.put("accentPreset", "NOT_A_PRESET");

        performPutAndExpectInvalidInputValue(objectMapper.writeValueAsString(body));
    }

    static Stream<String> booleanFieldNames() {
        return Stream.of("showPinned", "showPrograms", "showReviews", "showNotices", "showGallery");
    }

    @ParameterizedTest
    @MethodSource("booleanFieldNames")
    void missingBooleanFieldReturnsInvalidInputValue(String fieldName) throws Exception {
        performPutAndExpectInvalidInputValue(requestJsonOmitting(fieldName));
    }

    @Test
    void updateWhenRowMissingReturns404WithSiteThemeSettingNotFound() throws Exception {
        siteThemeSettingRepository.deleteAll();

        mockMvc.perform(put("/api/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SITE_THEME_SETTING_NOT_FOUND"));

        assertThat(siteThemeSettingRepository.count()).isZero();
    }

    private void performPutAndExpectInvalidInputValue(String requestBody) throws Exception {
        mockMvc.perform(put("/api/admin/theme")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    private Map<String, Object> validRequestMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accentPreset", "TERRACOTTA");
        body.put("showPinned", false);
        body.put("showPrograms", true);
        body.put("showReviews", false);
        body.put("showNotices", true);
        body.put("showGallery", false);
        return body;
    }

    private String validRequestJson() throws Exception {
        return objectMapper.writeValueAsString(validRequestMap());
    }

    private String requestJsonOmitting(String fieldName) throws Exception {
        Map<String, Object> body = validRequestMap();
        body.remove(fieldName);
        return objectMapper.writeValueAsString(body);
    }
}
