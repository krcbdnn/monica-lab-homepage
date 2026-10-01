-- P13-T33: 발주처 요구사항 재확인 결과, "강의 후기"는 하나의 게시판(BoardType.REVIEW)이며
-- 공개 top-level navigation에는 "강의 후기" 단 하나만 존재해야 한다. V8이 "게시판" GROUP을
-- "강의 후기" GROUP으로 재사용하며 그 자식으로 "수강 후기"/"특강 후기" 2개 메뉴를 만든 것은
-- Board 도메인(Board.programType)과는 무관한 Menu 레이어만의 설계였고, 이번에 요구사항과
-- 어긋난다는 것이 확인되어 이 migration으로 되돌린다.
--
-- Board 도메인(Entity/DTO/Service/Controller)과 공개 게시판 내부 subtype 필터
-- (home/board/list.html의 #board-type-filter: 전체/수강 후기/특강 후기, ?boardType=REVIEW(&programType=
-- COURSE|SPECIAL))는 이미 발주처 요구와 정확히 일치하므로 이 migration이 전혀 건드리지 않는다 -
-- Menu 테이블 구조만 바꾼다.
--
-- 최종 상태: "강의 후기" 행 1개(기존 GROUP 행을 id/label/sort_order/is_visible/open_in_new_tab
-- 그대로 유지한 채 재사용) - parent_id NULL, target_type BOARD_LIST, target_value REVIEW,
-- target_subvalue NULL. "수강 후기"/"특강 후기" 자식 행 2개는 삭제.
--
-- 원칙(V8/V9과 동일):
-- - V1~V9는 수정하지 않는다.
-- - id를 하드코딩하지 않는다(환경별 auto_increment 값에 의존하지 않음) - label+target_type+
--   parent_id 조합으로 대상 GROUP을 세션 변수로 확보한다.
-- - DELETE는 `parent_id = @review_menu_id` 단독 조건으로 광범위하게 실행하지 않는다.
--   target_type/target_value/target_subvalue까지 전부 일치하는 조합으로만 정밀하게 대상을
--   식별한다(현재 스키마 계약상 target_subvalue가 값을 가지는 경우는 BOARD_LIST+REVIEW+
--   {COURSE,SPECIAL} 조합뿐이므로 - MenuService.validateTarget()이 그 외 모든 조합에서
--   target_subvalue를 항상 NULL로 강제한다 - 이 조합만으로 이미 완전히 명확하다). label을
--   삭제 조건에 추가로 넣지 않는다 - 관리자가 이미 "수강 후기"/"특강 후기" 라벨을 다른 문구로
--   바꿔뒀더라도(예: "코스 후기") target_type/target_value/target_subvalue 조합 자체가 여전히
--   "이 GROUP의 REVIEW subtype 자식"이라는 의미를 그대로 나타내므로, label까지 조건에 넣으면
--   그런 케이스를 놓쳐 orphan 자식 행을 남기게 된다 - 오히려 덜 안전하다.
-- - 순서: (1) GROUP id 확보 → (2) 정밀 조합으로 자식 2행 DELETE → (3) GROUP 행을 LEAF로 UPDATE.
--   자식을 먼저 지우고 부모를 바꾸는 순서를 택한 이유는, 반대로 하면 "target_type=BOARD_LIST인
--   행이 parent_id를 가리키는 자식을 가진" 상태가(트랜잭션 내부라 실제로 노출되지는 않지만) 논리적
--   경로상 먼저 생겨 읽기 어려워지기 때문이다 - DDL이 없는 순수 DML이라 Flyway 기본 트랜잭션
--   경계 안에서 원자적으로 처리된다(중간 상태가 커밋되어 관찰될 위험 없음).
-- - 대상 GROUP이 이미 존재하지 않거나(예: 관리자가 label/target_type을 이미 바꿔둔 경우)
--   구조가 다르면 @review_menu_id가 NULL이 되어 이어지는 UPDATE/DELETE가 전부 0건 적용되고
--   조용히 스킵된다(V8/V9와 동일한 멱등 원칙).

SET @review_menu_id = (
    SELECT id FROM menu WHERE label = '강의 후기' AND target_type = 'GROUP' AND parent_id IS NULL
);

-- 1. REVIEW+COURSE 자식("수강 후기") 삭제 - target_subvalue 조합으로 정밀 식별.
DELETE FROM menu
WHERE parent_id = @review_menu_id
  AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW' AND target_subvalue = 'COURSE';

-- 2. REVIEW+SPECIAL 자식("특강 후기") 삭제 - target_subvalue 조합으로 정밀 식별.
DELETE FROM menu
WHERE parent_id = @review_menu_id
  AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW' AND target_subvalue = 'SPECIAL';

-- 3. "강의 후기" GROUP 행을 BOARD_LIST/REVIEW top-level LEAF로 전환한다(같은 id 재사용,
--    label/parent_id(이미 NULL)/sort_order/is_visible/open_in_new_tab은 건드리지 않는다).
UPDATE menu
SET target_type = 'BOARD_LIST', target_value = 'REVIEW', target_subvalue = NULL
WHERE id = @review_menu_id AND label = '강의 후기' AND target_type = 'GROUP' AND parent_id IS NULL;
