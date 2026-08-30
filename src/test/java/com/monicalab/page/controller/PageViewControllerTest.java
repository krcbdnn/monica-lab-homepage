package com.monicalab.page.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.page.entity.CmsPage;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.repository.PageRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PageViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageRepository pageRepository;

    @ParameterizedTest
    @EnumSource(PageType.class)
    void everyPageTypeReturns200AndResolvesToTheSharedDetailView(PageType pageType) throws Exception {
        mockMvc.perform(get("/pages/{type}", pageType))
                .andExpect(status().isOk())
                .andExpect(view().name("home/page/detail"));
    }

    @Test
    void detailRendersExternalContentLinkWithTargetBlankAndRelNoopener() throws Exception {
        CmsPage page = pageRepository.findByPageType(PageType.INTRODUCTION).orElseThrow();
        page.update(page.getTitle(), "<p>본문 <a href=\"https://example.com\">외부 링크</a></p>");
        pageRepository.saveAndFlush(page);

        String body = mockMvc.perform(get("/pages/{type}", PageType.INTRODUCTION))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements link = document.select("#page-detail-content a[href=https://example.com]");
        assertThat(link).hasSize(1);
        assertThat(link.attr("target")).isEqualTo("_blank");
        assertThat(link.attr("rel")).isEqualTo("noopener noreferrer");
    }
}
