package com.monicalab.page.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.page.entity.PageType;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

    @Test
    void invalidTypeToPublicViewPathReturnsInvalidInputValue() throws Exception {
        // 경로 변수 변환이 컨트롤러 메서드 호출(및 뷰 이름 반환) 이전에 실패하므로,
        // home/page/detail 템플릿이 아직 없어도(P8-T2 이전) 안전하게 실제 요청으로 검증 가능하다.
        mockMvc.perform(get("/pages/NOT_A_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }
}
