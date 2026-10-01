package com.monicalab.page.service;

import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.common.util.HtmlSanitizer;
import com.monicalab.page.dto.PageRequest;
import com.monicalab.page.dto.PageResponse;
import com.monicalab.page.entity.CmsPage;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.repository.PageRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageService {

    private static final Map<PageType, String> DEFAULT_TITLES = Map.of(
            PageType.GREETING, "인사말",
            PageType.INTRODUCTION, "연구소 소개",
            PageType.HISTORY, "연혁",
            PageType.LOCATION, "오시는 길");

    private final PageRepository pageRepository;

    @Transactional
    public void initializeFixedPages() {
        for (PageType pageType : PageType.values()) {
            if (pageRepository.existsByPageType(pageType)) {
                continue;
            }

            CmsPage page = CmsPage.builder()
                    .pageType(pageType)
                    .title(DEFAULT_TITLES.get(pageType))
                    .build();

            pageRepository.save(page);
            log.info("고정 페이지를 생성했습니다. pageType={}", pageType);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse getByType(PageType pageType) {
        return PageResponse.from(getEntity(pageType));
    }

    @Transactional
    public PageResponse update(PageType pageType, PageRequest request) {
        CmsPage page = getEntity(pageType);
        page.update(request.title(), HtmlSanitizer.sanitize(request.content()));
        return PageResponse.from(page);
    }

    private CmsPage getEntity(PageType pageType) {
        return pageRepository.findByPageType(pageType)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
    }
}
