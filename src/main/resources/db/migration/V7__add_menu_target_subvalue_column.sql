-- P13-T30D(Task C): BOARD_LIST/REVIEW 메뉴를 COURSE/SPECIAL로 세분화하기 위한 nullable 보조 컬럼.
-- "REVIEW:COURSE" 같은 복합 문자열을 target_value에 욱여넣지 않고 별도 컬럼으로 분리한다.
-- target_value는 계속 BoardType/ProgramType/PageType 등 단일 enum 이름만 담당하고, 이 컬럼은
-- targetType=BOARD_LIST && target_value='REVIEW'일 때만 의미를 가진다(그 외에는 항상 NULL이어야
-- 하며, 이는 MenuService.validateTarget()이 강제한다 - DB 제약으로는 표현하지 않음).
ALTER TABLE menu ADD COLUMN target_subvalue VARCHAR(50) NULL;
