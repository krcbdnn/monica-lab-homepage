package com.monicalab.popup.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.popup.entity.Popup;
import com.monicalab.popup.repository.PopupRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminPopupControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PopupRepository popupRepository;

    @BeforeEach
    void setUp() {
        popupRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessToAdminListReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/popups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns201WithPostDefaults() throws Exception {
        String body = "{\"title\":\"신규 팝업\",\"startDate\":\"2026-01-01T00:00:00\","
                + "\"endDate\":\"2026-01-31T23:59:59\"}";

        mockMvc.perform(admin(post("/api/admin/popups")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isVisible").value(false));
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        String body = "{\"title\":\" \",\"startDate\":\"2026-01-01T00:00:00\","
                + "\"endDate\":\"2026-01-31T23:59:59\"}";

        mockMvc.perform(admin(post("/api/admin/popups")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithStartDateAfterEndDateReturns400() throws Exception {
        String body = "{\"title\":\"신규 팝업\",\"startDate\":\"2026-02-01T00:00:00\","
                + "\"endDate\":\"2026-01-01T00:00:00\"}";

        mockMvc.perform(admin(post("/api/admin/popups")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createSanitizesScriptTagInContent() throws Exception {
        String body = "{\"title\":\"신규 팝업\",\"content\":\"<p>hello</p><script>alert(1)</script>\","
                + "\"startDate\":\"2026-01-01T00:00:00\",\"endDate\":\"2026-01-31T23:59:59\"}";

        mockMvc.perform(admin(post("/api/admin/popups")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content", containsString("hello")))
                .andExpect(jsonPath("$.data.content", not(containsString("<script"))));
    }

    @Test
    void adminListAndDetailReturnInvisibleAndOutOfPeriodPopups() throws Exception {
        Popup invisible = popupRepository.saveAndFlush(Popup.builder()
                .title("비노출 팝업")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .isVisible(false)
                .build());
        popupRepository.saveAndFlush(Popup.builder()
                .title("기간 지난 팝업")
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(5))
                .isVisible(true)
                .build());

        mockMvc.perform(admin(get("/api/admin/popups")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(admin(get("/api/admin/popups/{id}", invisible.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVisible").value(false))
                .andExpect(jsonPath("$.data.title").value("비노출 팝업"));
    }

    @Test
    void adminListDefaultsToCreatedAtDescAndAcceptsSortParam() throws Exception {
        Popup alpha = popupRepository.saveAndFlush(Popup.builder()
                .title("Alpha")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .isVisible(true)
                .build());
        Thread.sleep(1100);
        Popup zulu = popupRepository.saveAndFlush(Popup.builder()
                .title("Zulu")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .isVisible(true)
                .build());

        mockMvc.perform(admin(get("/api/admin/popups")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(zulu.getId()))
                .andExpect(jsonPath("$.data[1].id").value(alpha.getId()));

        mockMvc.perform(admin(get("/api/admin/popups")).param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.data[1].id").value(zulu.getId()));
    }

    @Test
    void putUpdatesPopupAndRequiresIsVisible() throws Exception {
        Popup popup = popupRepository.saveAndFlush(Popup.builder()
                .title("원래 제목")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .isVisible(false)
                .build());

        String validBody = "{\"title\":\"수정된 제목\",\"startDate\":\"2026-01-01T00:00:00\","
                + "\"endDate\":\"2026-01-31T23:59:59\",\"isVisible\":true}";

        mockMvc.perform(admin(put("/api/admin/popups/{id}", popup.getId())).content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.isVisible").value(true));

        String missingIsVisibleBody = "{\"title\":\"수정된 제목\",\"startDate\":\"2026-01-01T00:00:00\","
                + "\"endDate\":\"2026-01-31T23:59:59\"}";

        mockMvc.perform(admin(put("/api/admin/popups/{id}", popup.getId())).content(missingIsVisibleBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patchVisibilityUpdatesSingleField() throws Exception {
        Popup popup = popupRepository.saveAndFlush(Popup.builder()
                .title("상태 변경 대상")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .isVisible(false)
                .build());

        mockMvc.perform(admin(patch("/api/admin/popups/{id}/visibility", popup.getId()))
                        .content("{\"isVisible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVisible").value(true));
    }

    @Test
    void deleteRemovesPopupAndSubsequentGetReturns404() throws Exception {
        Popup popup = popupRepository.saveAndFlush(Popup.builder()
                .title("삭제 대상")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .isVisible(false)
                .build());

        mockMvc.perform(admin(delete("/api/admin/popups/{id}", popup.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(admin(get("/api/admin/popups/{id}", popup.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POPUP_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
