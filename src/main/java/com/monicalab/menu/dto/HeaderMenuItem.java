package com.monicalab.menu.dto;

import com.monicalab.menu.entity.MenuTargetType;
import java.util.List;

/**
 * P13-T30B: 공개 헤더 전용 View 모델. href가 null인 항목만 GROUP(submenu trigger)라는 사실만으로
 * 템플릿이 렌더링을 분기할 수 있다. href/target 속성 계산은 전부 MenuService가 완료해서 넘긴다.
 *
 * <p>P13-T37: {@code targetType}을 추가한다 - href 문자열의 생김새만으로 BOARD_LIST/PROGRAM_LIST/
 * PAGE/INTERNAL_URL을 추론하면, INTERNAL_URL로 "/boards"나 "/pages/INTRODUCTION" 같은 값을 그대로
 * 입력한 메뉴가 BOARD_LIST/PAGE로 오판정될 수 있다(관리자 Menu CMS가 INTERNAL_URL을 이미 지원). 이
 * 필드는 여전히 {@code header.html}이 직접 분기하는 데 쓰지 않는다 - {@code HeaderActiveResolver}
 * (view-support)만 소비하며, header.html은 그 결과(boolean)만 받는다(Thymeleaf에 targetType 분기
 * 로직을 작성하지 않는다는 기존 원칙은 유지).
 */
public record HeaderMenuItem(
        Long id,
        String label,
        String href,
        boolean openInNewTab,
        MenuTargetType targetType,
        List<HeaderMenuItem> children) {
}
