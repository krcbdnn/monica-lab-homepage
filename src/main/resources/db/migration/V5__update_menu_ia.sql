-- P13-T30C: V4가 심은 4개 flat 메뉴를 최종 IA(GROUP 3개 + 하위 10개, 총 13행)로 교체한다.
--
-- 전제(구현 전 실측 확인 완료): Menu 도메인은 지금까지 main 브랜치에 배포된 적이 없다
-- (git merge-base origin/main origin/develop 기준 main의 최신 공통 조상은 P12-T3 시점이며,
-- 그 시점 이후에 Menu 도메인이 develop에 추가됨). 즉 실제 운영 환경에서 관리자가 이 메뉴
-- 기능을 사용해 커스터마이징했을 가능성은 존재하지 않는다.
--
-- 이 전제 하에, 아래 DELETE는 "전체 삭제(DELETE FROM menu)"가 아니라 V4가 실제로 생성한
-- 4개 행만 내용 전체(label + parent_id IS NULL + target_type + target_value) 일치로 정밀
-- 식별해서 제거한다 - 예상과 다른 행이 존재하는 환경에서 이 migration이 실행되더라도 그
-- 행은 건드리지 않는다.
--
-- 중요한 원칙: 이번 V5는 "아직 관리자가 실제로 메뉴를 커스터마이징한 적이 없는" 시점의
-- 1회성 IA 교체다. 이 시점 이후로는 관리자가 CRUD로 수정한 메뉴 데이터를 향후 migration이
-- DELETE 후 재생성하는 방식(destructive reset)으로 다루지 않는다. 이후 메뉴 구조 변경이
-- 필요하면 기존 행을 UPDATE하거나 새 migration에서 신중하게 선택적으로 추가/조정한다.

DELETE FROM menu
WHERE parent_id IS NULL
  AND ((label = '연구소 소개' AND target_type = 'PAGE' AND target_value = 'INTRODUCTION')
    OR (label = '프로그램' AND target_type = 'PROGRAM_LIST' AND target_value IS NULL)
    OR (label = '강의 후기' AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW')
    OR (label = '게시판' AND target_type = 'BOARD_LIST' AND target_value IS NULL));

-- 연구소 소개 GROUP + 4개 하위 페이지
INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at)
VALUES ('연구소 소개', NULL, 'GROUP', NULL, 0, TRUE, FALSE, NOW(), NOW());
SET @about_group_id = LAST_INSERT_ID();

INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at) VALUES
('인사말', @about_group_id, 'PAGE', 'GREETING', 0, TRUE, FALSE, NOW(), NOW()),
('연구소 소개', @about_group_id, 'PAGE', 'INTRODUCTION', 1, TRUE, FALSE, NOW(), NOW()),
('연혁', @about_group_id, 'PAGE', 'HISTORY', 2, TRUE, FALSE, NOW(), NOW()),
('오시는 길', @about_group_id, 'PAGE', 'LOCATION', 3, TRUE, FALSE, NOW(), NOW());

-- 프로그램 GROUP + 2개 하위 타입
INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at)
VALUES ('프로그램', NULL, 'GROUP', NULL, 1, TRUE, FALSE, NOW(), NOW());
SET @program_group_id = LAST_INSERT_ID();

INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at) VALUES
('수강 프로그램', @program_group_id, 'PROGRAM_LIST', 'COURSE', 0, TRUE, FALSE, NOW(), NOW()),
('특강', @program_group_id, 'PROGRAM_LIST', 'SPECIAL', 1, TRUE, FALSE, NOW(), NOW());

-- 게시판 GROUP + 4개 하위 타입
INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at)
VALUES ('게시판', NULL, 'GROUP', NULL, 2, TRUE, FALSE, NOW(), NOW());
SET @board_group_id = LAST_INSERT_ID();

INSERT INTO menu (label, parent_id, target_type, target_value, sort_order, is_visible, open_in_new_tab, created_at, updated_at) VALUES
('공지사항', @board_group_id, 'BOARD_LIST', 'NOTICE', 0, TRUE, FALSE, NOW(), NOW()),
('갤러리', @board_group_id, 'BOARD_LIST', 'GALLERY', 1, TRUE, FALSE, NOW(), NOW()),
('자료실', @board_group_id, 'BOARD_LIST', 'ARCHIVE', 2, TRUE, FALSE, NOW(), NOW()),
('강의 후기', @board_group_id, 'BOARD_LIST', 'REVIEW', 3, TRUE, FALSE, NOW(), NOW());
