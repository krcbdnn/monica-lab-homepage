-- P13-T30D(Task C, 후속 미세조정): V8이 만든 최종 IA에 두 가지를 조정한다. V6/V7/V8은 이미 적용된
-- migration이라 수정하지 않는다(체크섬 불변). 이 migration도 동일 원칙을 유지한다: DELETE 없음,
-- 재INSERT 없음, PK 하드코딩 없음 - 전부 label/target_type/target_value/parent_id 조합으로 기존
-- row를 식별해 UPDATE만 수행한다.
--
-- 1. '인사말' Menu row는 공개 navigation에서만 숨긴다(is_visible=false). CmsPage(PageType.GREETING)
--    자체와 기존 /pages/GREETING 라우트는 이 migration과 무관하게 그대로 유지된다 - 이 migration은
--    Menu 테이블만 다룬다.
-- 2. top-level 6개 row의 sort_order를 "드롭다운 GROUP 먼저(연구소 소개/수강 신청/강의 후기),
--    그 다음 공지사항/갤러리/자료실" 순서로 재배치한다.

-- 1. '인사말' 비노출. 부모(연구소 소개 GROUP)를 label로 먼저 찾아 그 자식 중 정확한 조합만 매칭한다.
UPDATE menu SET is_visible = FALSE
WHERE label = '인사말' AND target_type = 'PAGE' AND target_value = 'GREETING'
  AND parent_id = (SELECT id FROM menu WHERE label = '연구소 소개' AND target_type = 'GROUP' AND parent_id IS NULL);

-- 2. top-level sort_order 재배치: 연구소 소개=0, 수강 신청=1, 강의 후기=2, 공지사항=3, 갤러리=4, 자료실=5.
UPDATE menu SET sort_order = 0
WHERE parent_id IS NULL AND label = '연구소 소개' AND target_type = 'GROUP';

UPDATE menu SET sort_order = 1
WHERE parent_id IS NULL AND label = '수강 신청' AND target_type = 'GROUP';

UPDATE menu SET sort_order = 2
WHERE parent_id IS NULL AND label = '강의 후기' AND target_type = 'GROUP';

UPDATE menu SET sort_order = 3
WHERE parent_id IS NULL AND label = '공지사항' AND target_type = 'BOARD_LIST' AND target_value = 'NOTICE';

UPDATE menu SET sort_order = 4
WHERE parent_id IS NULL AND label = '갤러리' AND target_type = 'BOARD_LIST' AND target_value = 'GALLERY';

UPDATE menu SET sort_order = 5
WHERE parent_id IS NULL AND label = '자료실' AND target_type = 'BOARD_LIST' AND target_value = 'ARCHIVE';
