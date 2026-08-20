package com.monicalab.popup.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class PopupControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PopupRepository popupRepository;

    @BeforeEach
    void setUp() {
        popupRepository.deleteAll();
    }

    @Test
    void publicListReturnsOnlyVisiblePopupsWithinPeriod() throws Exception {
        popupRepository.saveAndFlush(Popup.builder()
                .title("진행중 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());
        popupRepository.saveAndFlush(Popup.builder()
                .title("비노출 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(false)
                .build());
        popupRepository.saveAndFlush(Popup.builder()
                .title("기간이 지난 팝업")
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().minusDays(1))
                .isVisible(true)
                .build());
        popupRepository.saveAndFlush(Popup.builder()
                .title("아직 시작 안한 팝업")
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(10))
                .isVisible(true)
                .build());

        mockMvc.perform(get("/api/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("진행중 팝업"));
    }

    @Test
    void publicListOrdersByCreatedAtDesc() throws Exception {
        Popup first = popupRepository.saveAndFlush(Popup.builder()
                .title("먼저 등록된 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());
        Thread.sleep(1100);
        Popup second = popupRepository.saveAndFlush(Popup.builder()
                .title("나중에 등록된 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());

        mockMvc.perform(get("/api/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data[1].id").value(first.getId()));
    }

    @Test
    void visibilityFalseTransitionExcludesPopupFromPublicList() throws Exception {
        Popup popup = popupRepository.saveAndFlush(Popup.builder()
                .title("노출이었던 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());

        mockMvc.perform(get("/api/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(admin(patch("/api/admin/popups/{id}/visibility", popup.getId()))
                        .content("{\"isVisible\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/popups"))
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
