package com.monicalab.page.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.page.entity.PageType;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PageControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @EnumSource(PageType.class)
    void publicApiReturnsPageWithoutAuthenticationForEveryType(PageType pageType) throws Exception {
        mockMvc.perform(get("/api/pages/{pageType}", pageType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageType").value(pageType.name()));
    }

    @Test
    void invalidPageTypeToPublicApiReturnsInvalidInputValue() throws Exception {
        mockMvc.perform(get("/api/pages/NOT_A_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // P15-T5: 공개 화면 경로 변수 변환 실패는 존재할 수 없는 주소이므로 JSON 400이 아니라 HTML 404 오류 페이지다
    // (위 /api/pages/NOT_A_TYPE의 JSON 400 계약은 그대로 유지).
    @Test
    void invalidTypeToPublicViewPathReturnsHtmlNotFoundPage() throws Exception {
        mockMvc.perform(get("/pages/NOT_A_TYPE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(view().name("error/4xx"));
    }
}
