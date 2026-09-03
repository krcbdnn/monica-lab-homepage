-- P13-T30D(Task C): 강의 후기(REVIEW) 게시글을 COURSE/SPECIAL로 세분화하기 위한 nullable 컬럼.
-- 별도 ReviewType enum/별도 Review entity를 만들지 않고 기존 program.ProgramType을 그대로 재사용한다.
-- REVIEW가 아닌 boardType(NOTICE/GALLERY/ARCHIVE)은 이 컬럼이 항상 NULL이어야 하며, 이는
-- BoardService의 애플리케이션 레벨 검증(validateProgramType)이 강제한다(DB 제약으로는 표현하지 않음).
-- 기존 행은 컬럼이 없던 시절 데이터이므로 DEFAULT 없이 추가하면 전부 자동으로 NULL이 된다
-- (기존 REVIEW 게시글도 별도 backfill 없이 NULL로 남아 "REVIEW+NULL 허용" 계약과 자연히 호환된다).
ALTER TABLE board ADD COLUMN program_type VARCHAR(20) NULL;
