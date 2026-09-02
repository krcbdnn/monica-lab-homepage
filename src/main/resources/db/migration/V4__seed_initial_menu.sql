-- P13-T30A: 현재 공개 헤더(#quick-menu)에 이미 존재하는 4개 링크만 동일한 의미로 옮긴다.
-- HOME/ABOUT/OUR PROGRAMS 등 아직 확정되지 않은 신규 메뉴 구성(IA)은 이 seed에 포함하지 않는다.
-- 4개 모두 parent_id가 없는 최상위 항목이라 self-reference가 필요 없다.
INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at) VALUES
('연구소 소개', NULL, 'PAGE', 'INTRODUCTION', 0, TRUE, FALSE, NOW(), NOW()),
('프로그램', NULL, 'PROGRAM_LIST', NULL, 1, TRUE, FALSE, NOW(), NOW()),
('강의 후기', NULL, 'BOARD_LIST', 'REVIEW', 2, TRUE, FALSE, NOW(), NOW()),
('게시판', NULL, 'BOARD_LIST', NULL, 3, TRUE, FALSE, NOW(), NOW());
