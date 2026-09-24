package com.monicalab.theme.entity;

// P14-T8A: 실제 색상값/WCAG 대비 검증 없이 "지원 가능한 프리셋"을 미리 선언하지 않는다.
// TERRACOTTA는 P14-T3D에서 이미 도입된 --color-accent 계열과 이름을 맞춘 자리표시자 값이며,
// 다른 프리셋(예: BURGUNDY/FOREST)은 실제 색상과 대비 검증이 끝난 뒤 해당 값을 도입하는
// 작업(P14-T8C)에서 함께 추가한다. 표시용 한글 라벨은 기존 BoardType/ProgramType과 동일하게
// enum에 두지 않고 View 계층에서 매핑한다.
public enum AccentPreset {
    TERRACOTTA
}
