package com.monicalab.page.controller;

import static com.monicalab.support.PublicHeadAssertions.assertPublicHead;
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

    // P14-T5: 페이지 제목이 유일한 h1이고, renderedContent는 기존대로 .ckeditor-content에 렌더링된다.
    @Test
    void detailRendersTitleAsTheOnlyH1AndKeepsRenderedContent() throws Exception {
        CmsPage page = pageRepository.findByPageType(PageType.GREETING).orElseThrow();
        page.update("인사말 h1 제목", "<h2>본문 소제목</h2><p>인사말 본문</p>");
        pageRepository.saveAndFlush(page);

        Document document = Jsoup.parse(mockMvc.perform(get("/pages/{type}", PageType.GREETING))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(document.select("h1")).hasSize(1);
        assertThat(document.select("#page-detail-content > h1.detail-title").text()).isEqualTo("인사말 h1 제목");
        assertThat(document.select("#page-detail-content .ckeditor-content h2").text()).isEqualTo("본문 소제목");
        assertThat(document.select("#page-detail-content .ckeditor-content p").text()).isEqualTo("인사말 본문");
    }

    // P15-T7A: 기관소개 페이지는 기존 "페이지 제목 - 사이트명" title 형식을 유지하고 공통 SEO meta를 갖는다.
    @Test
    void detailHeadKeepsTitleFormatAndHasCommonSeoMeta() throws Exception {
        String pageTitle = pageRepository.findByPageType(PageType.INTRODUCTION).orElseThrow().getTitle();

        Document document = Jsoup.parse(mockMvc.perform(get("/pages/{type}", PageType.INTRODUCTION))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertPublicHead(document, pageTitle + " - 모니카영어교육연구소", "/pages/INTRODUCTION");
    }
}
