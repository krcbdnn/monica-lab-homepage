package com.monicalab.theme.entity;

// P14-T8C: TERRACOTTA(P14-T3D에서 도입)에 BURGUNDY/FOREST를 추가해 3개 프리셋을 지원한다. 각 프리셋의
// 실제 색상(--color-accent/--color-accent-text/--color-accent-on-dark)과 WCAG 대비 검증은 home.css에서
// 이뤄지며, 이 enum은 순수 식별자 역할만 한다. 표시용 한글 라벨은 기존 BoardType/ProgramType과 동일하게
// enum에 두지 않고 View 계층(admin/theme/form.html)에서 매핑한다.
public enum AccentPreset {
    TERRACOTTA,
    BURGUNDY,
    FOREST
}
