# ERD (Entity Relationship Diagram)

Version 2.0

---

# 프로젝트 개요

본 프로젝트는 교육기관 홈페이지 CMS이다.

- 일반 회원 기능은 제공하지 않는다.
- 관리자만 로그인하여 CMS를 관리한다.
- 프로그램 신청은 Google Form으로 연결한다.
- 모든 콘텐츠는 CMS에서 관리한다.

---

# ERD

Admin (1)
│
├── Program (N)
├── Board (N)
├── Banner (N)
├── Popup (N)
└── Page (N)

---

# 1. Admin

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| login_id | VARCHAR(50) | 로그인 ID |
| password | VARCHAR(255) | BCrypt 암호 |
| name | VARCHAR(50) | 관리자명 |
| role | VARCHAR(20) | ROLE_ADMIN |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 2. Program

수강 프로그램과 특강을 하나의 테이블에서 관리한다.

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| program_type | ENUM | COURSE / SPECIAL |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| thumbnail | VARCHAR(255) | 썸네일 |
| attachment | VARCHAR(255) | 첨부파일 |
| google_form_url | VARCHAR(500) | Google Form URL |
| recruit_status | ENUM | OPEN / CLOSED |
| is_public | BOOLEAN | 공개 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

`recruit_status`는 마감일 컬럼이 없으므로 시스템에 의한 자동 전환을 수행하지 않는다. 관리자가 API.md `PATCH /api/admin/programs/{id}/status`를 통해 수동으로 OPEN/CLOSED를 변경한다.

신규 등록(`POST /api/admin/programs`) 시 요청 DTO에 값이 없어도 되도록 기본값을 다음과 같이 둔다: `recruit_status` 기본값 `OPEN`(등록 직후 바로 모집 시작이 일반적인 운영 흐름이므로), `is_public` 기본값 `false`(콘텐츠 작성이 끝나기 전 공개되는 것을 방지하기 위해 관리자가 명시적으로 공개 처리하도록 함). 두 값 모두 요청 DTO에서 명시적으로 지정하면 그 값을 우선한다.

---

# 3. Board

공지사항, 갤러리, 자료실을 하나의 테이블에서 관리한다.

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| board_type | ENUM | NOTICE / GALLERY / ARCHIVE |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| thumbnail | VARCHAR(255) | 대표 이미지(갤러리) |
| attachment | VARCHAR(255) | 첨부파일 |
| view_count | INT | 조회수 |
| is_public | BOOLEAN | 공개 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 4. Banner

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| title | VARCHAR(100) | 제목 |
| image | VARCHAR(255) | 이미지 |
| link_url | VARCHAR(500) | 링크 |
| sort_order | INT | 정렬 순서 |
| is_visible | BOOLEAN | 노출 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 5. Popup

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| title | VARCHAR(100) | 제목 |
| content | LONGTEXT | 내용 |
| start_date | DATETIME | 시작일 |
| end_date | DATETIME | 종료일 |
| is_visible | BOOLEAN | 노출 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 6. Page

기관소개 페이지 관리

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| page_type | ENUM | GREETING / INTRODUCTION / HISTORY / LOCATION |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 7. File

업로드된 파일(이미지/첨부파일)의 메타데이터를 관리한다.

Program, Board의 썸네일/첨부파일과 Page의 CKEditor 이미지 업로드가 이 테이블을 참조한다.

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| original_name | VARCHAR(255) | 원본 파일명 |
| stored_name | VARCHAR(255) | 저장 파일명(UUID) |
| path | VARCHAR(500) | 저장 경로(`/upload/yyyy/MM/dd/{uuid}.{ext}`) |
| content_type | VARCHAR(100) | MIME 타입 |
| size | BIGINT | 파일 크기(byte) |
| file_type | ENUM | IMAGE / ATTACHMENT |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

비고

- API.md의 `POST /api/admin/files`, `GET /api/files/{id}`, `DELETE /api/admin/files/{id}`는 본 File 테이블을 기준으로 동작한다.
- Program.thumbnail / Program.attachment / Board.thumbnail / Board.attachment 컬럼은 File.id를 참조하는 FK(`file_id`)가 아니라, 응답 시 필요한 URL 문자열만 저장한다(외부 연동 단순화를 위해 정규화하지 않음). File 테이블은 업로드 이력·삭제 관리용으로 별도 운용한다.
- **고아 파일(orphan file) 정책**: Program/Board/Page/Popup을 삭제하거나 썸네일·첨부파일을 교체해도, 연결되어 있던 File 레코드와 실제 파일은 **자동으로 함께 삭제하지 않는다**. File 삭제는 `DELETE /api/admin/files/{id}`를 통한 관리자의 명시적 조작으로만 수행한다(P2-T4 기준). 고아 파일 일괄 정리는 본 프로젝트 범위에 포함하지 않으며, 필요 시 "향후 확장" 과제로 별도 진행한다.

---

# 공통 설계

모든 Entity는 BaseEntity를 상속한다.

공통 컬럼

- created_at
- updated_at

---

# 저장소

기본

- Local Storage

확장

- AWS S3

DB에는 파일 경로만 저장한다.

---

# 제외 기능

- 회원가입
- 일반 로그인
- 마이페이지
- 신청 데이터 저장
- 상담 신청

---

# 설계 원칙

- Program(program_type)으로 수강/특강 통합
- Board(board_type)으로 공지사항/갤러리/자료실 통합
- Google Form URL을 이용한 신청
- CMS에서 모든 콘텐츠 수정
- 관리자만 Spring Security 인증