package com.monicalab.page.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.page.entity.PageType;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PageViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @EnumSource(PageType.class)
    void everyPageTypeReturns200AndResolvesToTheSharedDetailView(PageType pageType) throws Exception {
        mockMvc.perform(get("/pages/{type}", pageType))
                .andExpect(status().isOk())
                .andExpect(view().name("home/page/detail"));
    }
}
