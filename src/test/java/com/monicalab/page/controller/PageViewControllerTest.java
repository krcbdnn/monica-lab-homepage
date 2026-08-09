package com.monicalab.page.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.page.entity.PageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * P8-T2에서 templates/home/page/*.html이 생성되기 전이므로, 실제 렌더링을 시도하는
 * MockMvc 통합 테스트 대신 컨트롤러 메서드를 직접 호출하는 단위/정적 테스트로 검증한다
 * (TASK.md P4-T3 DoD 기준).
 */
class PageViewControllerTest {

    private final PageViewController controller = new PageViewController();

    @ParameterizedTest
    @EnumSource(PageType.class)
    void everyPageTypeResolvesToTheSharedDetailView(PageType pageType) {
        assertThat(controller.view(pageType)).isEqualTo("home/page/detail");
    }

    @Test
    void mappingTargetsPagesTypePath() throws NoSuchMethodException {
        GetMapping mapping = PageViewController.class.getMethod("view", PageType.class).getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/pages/{type}");
    }
}
