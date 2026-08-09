package com.monicalab.board.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * P8-T4에서 templates/home/board/*.html이 생성되기 전이므로, 실제 렌더링을 시도하는
 * MockMvc 통합 테스트 대신 컨트롤러 메서드를 직접 호출하는 단위/정적 테스트로 검증한다
 * (TASK.md P6-T2 DoD 기준, ProgramViewControllerTest와 동일한 패턴).
 */
class BoardViewControllerTest {

    private final BoardViewController controller = new BoardViewController();

    @Test
    void listResolvesToTheHomeBoardListView() {
        assertThat(controller.list()).isEqualTo("home/board/list");
    }

    @Test
    void detailResolvesToTheHomeBoardDetailView() {
        assertThat(controller.detail(1L)).isEqualTo("home/board/detail");
    }

    @Test
    void listMappingTargetsBoardsPath() throws NoSuchMethodException {
        GetMapping mapping = BoardViewController.class.getMethod("list").getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/boards");
    }

    @Test
    void detailMappingTargetsBoardsIdPath() throws NoSuchMethodException {
        GetMapping mapping = BoardViewController.class.getMethod("detail", Long.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/boards/{id}");
    }
}
