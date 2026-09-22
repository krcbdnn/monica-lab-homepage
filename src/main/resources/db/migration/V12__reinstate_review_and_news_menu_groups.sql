-- P14-T2A: 발주처 요구사항이 최신으로 다시 변경되어, P13-T33(V10)이 "강의 후기는 하나의 게시판이며
-- 공개 navigation에는 top-level LEAF 하나만 존재해야 한다"며 되돌렸던 결정을 이번에 다시 뒤집는다.
-- P13-T33 자체는 당시 요구사항 기준으로 올바른 작업이었으므로 V10을 수정하거나 되돌리지 않고, 그
-- 위에 새 forward migration으로 최신 IA를 쌓는다(이력 보존 - docs/TASK.md P13-T33 항목도 무수정).
--
-- 최종 목표 공개 IA(HOME/전체메뉴는 Menu row가 아니라 header.html 정적 마크업, 이 migration과 무관):
--
-- HOME
-- 연구소 소개 (GROUP) - 연구소 소개/연혁/오시는 길                      <- 무변경
-- 수강 신청 (GROUP)   - 수강 신청/특강 신청                            <- 무변경(label도 유지)
-- 소식·자료 (GROUP)   - 공지사항/갤러리/자료실                         <- 신규 GROUP, 기존 top-level LEAF 3개를 자식으로 이동
-- 강의 후기 (GROUP)   - 전체/수강 후기/특강 후기                       <- 기존 top-level LEAF(V10)를 GROUP으로 재전환 + 자식 3개 신규
-- 전체메뉴
--
-- 원칙(V8/V9/V10과 동일, 이번 migration에서도 그대로 유지):
-- - DELETE는 사용하지 않는다(이번 migration에 DELETE 문 자체가 없다).
-- - id를 하드코딩하지 않는다 - label+target_type+target_value(+target_subvalue/parent_id) 조합으로
--   기존 row를 세션 변수(@...)에 확보한 뒤에만 그 id를 참조한다.
-- - 모든 UPDATE는 "현재 baseline과 정확히 일치하는" row만 대상으로 한다. 관리자가 이미 해당 row의
--   label/target_type/target_value/parent_id를 다른 값으로 바꿔 놓았다면 WHERE 조건이 매치되지 않아
--   조용히 0건 적용되어 스킵된다(V8/V9/V10과 동일한 멱등 원칙) - 그 경우 이 migration은 목표 IA를
--   완성하지 못하므로, 실제 운영 배포 전에는 대상 DB가 이 baseline과 일치하는지 별도로 확인해야
--   한다(이 migration 자체가 그 확인을 대신하지 않는다 - V8 주석과 동일한 전제).
-- - "예상과 다른 기존 구조를 복구한다"는 명목으로 넓은 조건의 UPDATE를 사용하지 않는다.
--
-- 예상 결과: 12행 -> 16행(신규 4행: 소식·자료 GROUP, 강의 후기의 자식 3개 - 전체/수강 후기/특강 후기).
-- 기존 "강의 후기" row(V10이 만든 top-level LEAF)와 공지사항/갤러리/자료실 3행은 id를 그대로 재사용한다.

-- ============================================================
-- A. 소식·자료 GROUP
-- ============================================================

-- A-1. "소식·자료" top-level GROUP guarded INSERT. sort_order는 목표 순서(연구소 소개=0, 수강 신청=1,
--      소식·자료=2, 강의 후기=3)에 맞춰 2로 둔다.
INSERT INTO menu (label, parent_id, target_type, target_value, target_subvalue, sort_order,
                   is_visible, open_in_new_tab, created_at, updated_at)
SELECT '소식·자료', NULL, 'GROUP', NULL, NULL, 2, TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM menu WHERE label = '소식·자료' AND target_type = 'GROUP' AND parent_id IS NULL
);

SET @news_group_id = (
    SELECT id FROM menu WHERE label = '소식·자료' AND target_type = 'GROUP' AND parent_id IS NULL
);

-- A-2. 기존 top-level LEAF 3개(공지사항/갤러리/자료실)를 baseline과 정확히 일치하는 조건으로만
--      소식·자료 자식으로 이동한다. id/label/is_visible/open_in_new_tab은 건드리지 않고
--      parent_id와 child 기준 sort_order만 갱신한다.
UPDATE menu SET parent_id = @news_group_id, sort_order = 0
WHERE parent_id IS NULL AND label = '공지사항'
  AND target_type = 'BOARD_LIST' AND target_value = 'NOTICE' AND target_subvalue IS NULL;

UPDATE menu SET parent_id = @news_group_id, sort_order = 1
WHERE parent_id IS NULL AND label = '갤러리'
  AND target_type = 'BOARD_LIST' AND target_value = 'GALLERY' AND target_subvalue IS NULL;

UPDATE menu SET parent_id = @news_group_id, sort_order = 2
WHERE parent_id IS NULL AND label = '자료실'
  AND target_type = 'BOARD_LIST' AND target_value = 'ARCHIVE' AND target_subvalue IS NULL;

-- ============================================================
-- B. 강의 후기: 기존 top-level LEAF(V10) -> GROUP 재전환
-- ============================================================

-- V10이 만든 정확한 모양(label/target_type/target_value/target_subvalue IS NULL/parent_id IS NULL)과
-- 일치하는 row만 확보한다. 관리자가 이미 이 row를 바꿔뒀다면(label 변경, 다른 target_type 등) 아래
-- 조건에 매치되지 않아 @review_group_id가 NULL이 되고, 이어지는 UPDATE(B)와 INSERT(C)가 전부
-- 자연스럽게 스킵된다 - 다른 REVIEW row를 추측해서 대신 전환하지 않는다.
SET @review_group_id = (
    SELECT id FROM menu WHERE label = '강의 후기' AND target_type = 'BOARD_LIST'
      AND target_value = 'REVIEW' AND target_subvalue IS NULL AND parent_id IS NULL
);

-- 같은 id를 재사용해 GROUP으로 전환한다(label/parent_id(이미 NULL)/open_in_new_tab은 무변경).
-- sort_order는 목표 top-level 순서(3)로 재배치한다(V9이 배치한 기존 값 2에서 변경 - 소식·자료가
-- 그 자리를 대신 차지했으므로).
UPDATE menu SET target_type = 'GROUP', target_value = NULL, target_subvalue = NULL, sort_order = 3
WHERE id = @review_group_id AND label = '강의 후기' AND target_type = 'BOARD_LIST'
  AND target_value = 'REVIEW' AND target_subvalue IS NULL AND parent_id IS NULL;

-- ============================================================
-- C. 강의 후기 children: 전체 / 수강 후기 / 특강 후기 (guarded INSERT)
-- ============================================================
-- @review_group_id가 NULL이면(B에서 대상을 찾지 못한 경우) 아래 INSERT는 WHERE 절의
-- "@review_group_id IS NOT NULL" 조건에 의해 전부 스킵된다.

-- "전체": 기존 top-level LEAF가 갖던 것과 동일한 href(/boards?boardType=REVIEW)를 그대로 재현한다
-- (programType 없음 - REVIEW+NULL 기존 후기도 포함하는 현재 semantics를 그대로 유지, Board 도메인/
-- BoardService는 이 migration과 무관하게 무수정).
INSERT INTO menu (label, parent_id, target_type, target_value, target_subvalue, sort_order,
                   is_visible, open_in_new_tab, created_at, updated_at)
SELECT '전체', @review_group_id, 'BOARD_LIST', 'REVIEW', NULL, 0, TRUE, FALSE, NOW(), NOW()
WHERE @review_group_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM menu WHERE parent_id = @review_group_id
      AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW' AND target_subvalue IS NULL
);

INSERT INTO menu (label, parent_id, target_type, target_value, target_subvalue, sort_order,
                   is_visible, open_in_new_tab, created_at, updated_at)
SELECT '수강 후기', @review_group_id, 'BOARD_LIST', 'REVIEW', 'COURSE', 1, TRUE, FALSE, NOW(), NOW()
WHERE @review_group_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM menu WHERE parent_id = @review_group_id
      AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW' AND target_subvalue = 'COURSE'
);

INSERT INTO menu (label, parent_id, target_type, target_value, target_subvalue, sort_order,
                   is_visible, open_in_new_tab, created_at, updated_at)
SELECT '특강 후기', @review_group_id, 'BOARD_LIST', 'REVIEW', 'SPECIAL', 2, TRUE, FALSE, NOW(), NOW()
WHERE @review_group_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM menu WHERE parent_id = @review_group_id
      AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW' AND target_subvalue = 'SPECIAL'
);
