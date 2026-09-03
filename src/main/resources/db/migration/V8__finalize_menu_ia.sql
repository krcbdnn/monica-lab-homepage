-- P13-T30D(Task C): V5가 만든 3-GROUP IA(연구소 소개/프로그램/게시판)를 최종 공개 IA로 전환한다.
--
-- HOME
-- 연구소 소개 (GROUP) - 인사말/연구소 소개/연혁/오시는 길           <- 무변경
-- 공지사항 (top-level LEAF, BOARD_LIST/NOTICE)                    <- 게시판 GROUP에서 승격
-- 갤러리   (top-level LEAF, BOARD_LIST/GALLERY)                   <- 게시판 GROUP에서 승격
-- 자료실   (top-level LEAF, BOARD_LIST/ARCHIVE)                   <- 게시판 GROUP에서 승격, 숨기지 않음
-- 수강 신청 (GROUP, 구 '프로그램')  - 수강 신청/특강 신청           <- label만 변경
-- 강의 후기 (GROUP, 구 '게시판')    - 수강 후기/특강 후기           <- label만 변경 + 재사용
-- 전체메뉴 (Menu row 아님, header.html 정적 마크업 - 이 migration과 무관)
--
-- 원칙(구현 전 합의 그대로):
-- - V1~V5는 수정하지 않는다.
-- - DELETE는 사용하지 않는다. 모든 UPDATE는 label+target_type+target_value(+parent_id) 정밀 일치
--   조건으로만 동작한다 - 이미 관리자가 그 특정 행을 다른 값으로 바꿔 놓았다면(예: 로컬 개발 DB에서
--   '수강 프로그램'->'수강 신청'을 수동으로 이미 바꿔 둔 경우) 그 UPDATE는 조용히 0건 적용되어
--   스킵된다. 목표 라벨과 이미 같다면 결과적으로 무해하지만, 이 사실이 "실제 운영 pre-flight가
--   필요 없다"는 뜻은 아니다 - 배포 전에는 실제 데이터가 V5 baseline과 완전히 일치하는지 별도로
--   확인해야 한다(이 migration 자체는 그 확인을 대신하지 않는다).
-- - 신규 INSERT는 '특강 후기' 1행뿐이며, label만이 아니라 parent_id+target_type+target_value+
--   target_subvalue 조합 전체로 중복 여부를 판단하는 NOT EXISTS 가드를 둔다.

-- 1. '프로그램' GROUP -> '수강 신청' (id를 먼저 확보해 이후 자식 UPDATE에서 label 변경과 무관하게 재사용)
SET @program_group_id = (SELECT id FROM menu WHERE label = '프로그램' AND target_type = 'GROUP' AND parent_id IS NULL);

UPDATE menu SET label = '수강 신청'
WHERE id = @program_group_id AND label = '프로그램' AND target_type = 'GROUP' AND parent_id IS NULL;

-- 2. COURSE 자식 -> '수강 신청' (parent_id는 위에서 확보한 실제 id로 지정, 하드코딩 없음)
UPDATE menu SET label = '수강 신청'
WHERE parent_id = @program_group_id AND label = '수강 프로그램'
  AND target_type = 'PROGRAM_LIST' AND target_value = 'COURSE';

-- 3. SPECIAL 자식 -> '특강 신청'
UPDATE menu SET label = '특강 신청'
WHERE parent_id = @program_group_id AND label = '특강'
  AND target_type = 'PROGRAM_LIST' AND target_value = 'SPECIAL';

-- 4. '게시판' GROUP id 확보(자식들을 top-level로 옮기기 전에 먼저 캡처해 둔다)
SET @board_group_id = (SELECT id FROM menu WHERE label = '게시판' AND target_type = 'GROUP' AND parent_id IS NULL);

-- 5. NOTICE 자식 -> top-level 승격
UPDATE menu SET parent_id = NULL, sort_order = 1
WHERE parent_id = @board_group_id AND label = '공지사항'
  AND target_type = 'BOARD_LIST' AND target_value = 'NOTICE';

-- 6. GALLERY 자식 -> top-level 승격
UPDATE menu SET parent_id = NULL, sort_order = 2
WHERE parent_id = @board_group_id AND label = '갤러리'
  AND target_type = 'BOARD_LIST' AND target_value = 'GALLERY';

-- 7. ARCHIVE 자식 -> top-level 승격(숨기지 않음 - is_visible은 그대로 둔다)
UPDATE menu SET parent_id = NULL, sort_order = 3
WHERE parent_id = @board_group_id AND label = '자료실'
  AND target_type = 'BOARD_LIST' AND target_value = 'ARCHIVE';

-- 8. 기존 REVIEW 자식 -> '수강 후기' + target_subvalue=COURSE (게시판 GROUP이 곧 아래에서 '강의 후기'로
--    재라벨링될 것이므로, 이 UPDATE는 parent_id가 그대로인 상태에서 먼저 실행한다)
UPDATE menu SET label = '수강 후기', target_subvalue = 'COURSE', sort_order = 0
WHERE parent_id = @board_group_id AND label = '강의 후기'
  AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW';

-- 9. '게시판' GROUP -> '강의 후기' GROUP으로 재사용(신규 GROUP 생성 안 함), top-level sort_order 재배치
UPDATE menu SET label = '강의 후기', sort_order = 5
WHERE id = @board_group_id AND target_type = 'GROUP' AND parent_id IS NULL;

-- 10. '연구소 소개' GROUP과 '수강 신청'(구 프로그램) GROUP의 top-level sort_order를 최종 순서로 재배치
--     (최종 순서: 연구소 소개=0, 공지사항=1, 갤러리=2, 자료실=3, 수강 신청=4, 강의 후기=5)
UPDATE menu SET sort_order = 0
WHERE label = '연구소 소개' AND target_type = 'GROUP' AND parent_id IS NULL;

UPDATE menu SET sort_order = 4
WHERE id = @program_group_id AND target_type = 'GROUP' AND parent_id IS NULL;

-- 11. 신규 '특강 후기' 1행만 INSERT. label만이 아니라 parent_id+target_type+target_value+
--     target_subvalue 조합 전체로 중복을 판단하는 idempotent 가드를 둔다.
INSERT INTO menu (label, parent_id, target_type, target_value, target_subvalue, sort_order,
                   is_visible, open_in_new_tab, created_at, updated_at)
SELECT '특강 후기', @board_group_id, 'BOARD_LIST', 'REVIEW', 'SPECIAL', 1, TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM menu
    WHERE parent_id = @board_group_id
      AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW' AND target_subvalue = 'SPECIAL'
);
