package com.monicalab.program.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * P8-T3에서 templates/home/program/*.html이 생성되기 전이므로, 실제 렌더링을 시도하는
 * MockMvc 통합 테스트 대신 컨트롤러 메서드를 직접 호출하는 단위/정적 테스트로 검증한다
 * (TASK.md P5-T5 DoD 기준, PageViewControllerTest와 동일한 패턴).
 */
class ProgramViewControllerTest {

    private final ProgramViewController controller = new ProgramViewController();

    @Test
    void listResolvesToTheHomeProgramListView() {
        assertThat(controller.list()).isEqualTo("home/program/list");
    }

    @Test
    void detailResolvesToTheHomeProgramDetailView() {
        assertThat(controller.detail(1L)).isEqualTo("home/program/detail");
    }

    @Test
    void listMappingTargetsProgramsPath() throws NoSuchMethodException {
        GetMapping mapping = ProgramViewController.class.getMethod("list").getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/programs");
    }

    @Test
    void detailMappingTargetsProgramsIdPath() throws NoSuchMethodException {
        GetMapping mapping = ProgramViewController.class.getMethod("detail", Long.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/programs/{id}");
    }
}
