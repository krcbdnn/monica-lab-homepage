package com.monicalab.menu.dto;

import java.util.List;

/**
 * P13-T30B: 공개 헤더 전용 View 모델. Entity/MenuTargetType을 Thymeleaf에 노출하지 않기 위해
 * targetType은 포함하지 않는다 - href가 null인 항목만 GROUP(submenu trigger)라는 사실만으로
 * 템플릿이 렌더링을 분기할 수 있다. href/target 속성 계산은 전부 MenuService가 완료해서 넘긴다.
 */
public record HeaderMenuItem(
        Long id,
        String label,
        String href,
        boolean openInNewTab,
        List<HeaderMenuItem> children) {
}
