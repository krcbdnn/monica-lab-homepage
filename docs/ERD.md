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

본 프로젝트의 현재 DB 스키마에는 Entity 간 FK 관계를 두지 않는다.

- `Admin`은 CMS 접근을 위한 인증/인가 계정이며 Program / Board / Banner / Popup / Page의 작성자·소유자 FK로 연결하지 않는다.
- 따라서 Program / Board / Banner / Popup / Page에 `admin_id` 컬럼을 생성하지 않는다.
- Program / Board의 파일 연결도 `file_id` FK가 아니라 File API가 반환한 URL 문자열을 저장한다.
- 아래 테이블 정의에 명시되지 않은 FK를 Flyway migration이나 JPA Entity에 임의 추가하지 않는다.

### 열거형(Enum) 컬럼 표기 규칙

아래 테이블 정의에서 `program_type`, `recruit_status`, `board_type`, `page_type`, `file_type`처럼 고정된 값 집합을 갖는 컬럼은 **Java/도메인 관점의 논리적 enum**을 의미하며, DB 물리 컬럼 타입은 `VARCHAR(20)`을 사용한다(MariaDB 네이티브 `ENUM(...)` 타입은 사용하지 않는다). `Admin.role`과 동일한 표기 방식이다. JPA Entity에서는 `@Enumerated(EnumType.STRING)`으로 매핑하며, 허용 값 목록은 각 컬럼의 `제약/설명` 열에 기술한다. Flyway `V1__baseline_schema.sql`도 이 규칙을 따른다.

---

# 1. Admin

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| login_id | VARCHAR(50) | NOT NULL | 없음 | UNIQUE, 로그인 ID |
| password | VARCHAR(255) | NOT NULL | 없음 | BCrypt 암호 |
| name | VARCHAR(50) | NOT NULL | 없음 | 관리자명 |
| role | VARCHAR(20) | NOT NULL | 없음 | `ROLE_ADMIN`; 초기 관리자 생성 로직에서 application-level로 설정 |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

---

# 2. Program

수강 프로그램과 특강을 하나의 테이블에서 관리한다.

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| program_type | VARCHAR(20) | NOT NULL | 없음 | COURSE / SPECIAL |
| title | VARCHAR(200) | NOT NULL | 없음 | 제목 |
| content | LONGTEXT | NULL | 없음 | 내용 |
| thumbnail | VARCHAR(255) | NULL | 없음 | File API가 반환한 썸네일 URL 문자열 |
| attachment | VARCHAR(255) | NULL | 없음 | File API가 반환한 첨부파일 URL 문자열 |
| google_form_url | VARCHAR(500) | NULL | 없음 | Google Form URL |
| recruit_status | VARCHAR(20) | NOT NULL | 없음 | OPEN / CLOSED; POST 생략 시 application-level 기본값 `OPEN` |
| is_public | BOOLEAN | NOT NULL | 없음 | POST 생략 시 application-level 기본값 `false` |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

`recruit_status`는 마감일 컬럼이 없으므로 시스템에 의한 자동 전환을 수행하지 않는다. 관리자가 API.md `PATCH /api/admin/programs/{id}/status`를 통해 수동으로 OPEN/CLOSED를 변경한다.

신규 등록(`POST /api/admin/programs`) 시 요청 DTO에 값이 없어도 되도록 기본값을 다음과 같이 둔다: `recruit_status` 기본값 `OPEN`, `is_public` 기본값 `false`. 두 값은 **DB DEFAULT가 아니라 application-level(Service/Entity 생성 로직) 기본값**이며, 요청 DTO에서 명시적으로 지정하면 그 값을 우선한다.

---

# 3. Board

공지사항, 갤러리, 자료실을 하나의 테이블에서 관리한다.

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| board_type | VARCHAR(20) | NOT NULL | 없음 | NOTICE / GALLERY / ARCHIVE |
| title | VARCHAR(200) | NOT NULL | 없음 | 제목 |
| content | LONGTEXT | NULL | 없음 | 내용 |
| thumbnail | VARCHAR(255) | NULL | 없음 | 대표 이미지(갤러리), File API URL 문자열 |
| attachment | VARCHAR(255) | NULL | 없음 | 첨부파일, File API URL 문자열 |
| view_count | INT | NOT NULL | 없음 | 서버 관리 필드; 생성 시 application-level 기본값 `0` |
| is_public | BOOLEAN | NOT NULL | 없음 | POST 생략 시 application-level 기본값 `false` |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

---

# 4. Banner

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| title | VARCHAR(100) | NOT NULL | 없음 | 제목 |
| image | VARCHAR(255) | NOT NULL | 없음 | File API가 반환한 이미지 URL 문자열 |
| link_url | VARCHAR(500) | NULL | 없음 | 링크 |
| sort_order | INT | NOT NULL | 없음 | 정렬 순서. 공개 메인 캐러셀 노출 순서를 의미하며 값이 작을수록 먼저 노출됨 |
| is_visible | BOOLEAN | NOT NULL | 없음 | POST 생략 시 application-level 기본값 `false` |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

---

# 5. Popup

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| title | VARCHAR(100) | NOT NULL | 없음 | 제목 |
| content | LONGTEXT | NULL | 없음 | 내용 |
| start_date | DATETIME | NOT NULL | 없음 | 시작일 |
| end_date | DATETIME | NOT NULL | 없음 | 종료일; `start_date <= end_date`는 API/Service Validation에서 검증 |
| is_visible | BOOLEAN | NOT NULL | 없음 | POST 생략 시 application-level 기본값 `false` |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

---

# 6. Page

기관소개 페이지 관리

> **Entity 클래스명 주의**: 테이블명은 `page`를 사용하지만, JPA Entity 클래스명은 `CmsPage`를 사용한다. `Page`는 `org.springframework.data.domain.Page<T>`(Spring Data 페이지네이션 타입)와 이름이 충돌하여 Repository/Service 계층에서 import 모호성이 발생하기 때문이다(ARCHITECTURE.md, CODING_RULES.md 기준).

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| page_type | VARCHAR(20) | NOT NULL | 없음 | GREETING / INTRODUCTION / HISTORY / LOCATION, UNIQUE |
| title | VARCHAR(200) | NOT NULL | 없음 | 제목 |
| content | LONGTEXT | NULL | 없음 | 내용 |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

---

Page는 4개 타입별 정확히 1개 레코드만 존재하는 고정 리소스다. 초기화 시 누락 타입만 생성하며 CMS에서는 조회/수정만 수행한다.

---

# 7. File

업로드된 파일(이미지/첨부파일)의 메타데이터를 관리한다.

> **Entity 클래스명 주의**: 테이블명은 `file`을 사용하지만, JPA Entity 클래스명은 `UploadFile`을 사용한다. `File`은 `java.io.File`과 이름이 충돌하여 파일 업로드/스트리밍 코드에서 import 모호성이 발생하기 때문이다(ARCHITECTURE.md, CODING_RULES.md 기준).

Program, Board의 썸네일/첨부파일과 Page의 CKEditor 이미지 업로드가 이 테이블을 참조한다.

| 컬럼 | 타입 | NULL | DB DEFAULT | 제약 / 설명 |
|-------|------|------|------------|-------------|
| id | BIGINT | NOT NULL | 없음 | PK, `AUTO_INCREMENT` |
| original_name | VARCHAR(255) | NOT NULL | 없음 | 원본 파일명 |
| stored_name | VARCHAR(255) | NOT NULL | 없음 | 저장 파일명(UUID) |
| path | VARCHAR(500) | NOT NULL | 없음 | `UPLOAD_ROOT` 기준 상대 저장 경로(`yyyy/MM/dd/{uuid}.{ext}`) |
| content_type | VARCHAR(100) | NOT NULL | 없음 | MIME 타입 |
| size | BIGINT | NOT NULL | 없음 | 파일 크기(byte) |
| file_type | VARCHAR(20) | NOT NULL | 없음 | IMAGE / ATTACHMENT |
| created_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |
| updated_at | DATETIME | NOT NULL | 없음 | BaseEntity JPA Auditing에서 application-level로 설정 |

비고

- API.md의 `GET /api/admin/files`, `POST /api/admin/files`, `GET /api/files/{id}`, `DELETE /api/admin/files/{id}`는 본 File 테이블을 기준으로 동작한다. 관리자 목록은 `FileRepository`가 UploadFile 업로드 이력을 페이징/정렬 조회한다.
- `path`에는 `UPLOAD_ROOT`를 포함한 절대경로를 저장하지 않고 `yyyy/MM/dd/{uuid}.{ext}` 상대경로만 저장한다. 실제 파일 접근 시 `FileService`가 `UPLOAD_ROOT`와 결합한다. Docker 운영에서는 `UPLOAD_ROOT=/app/uploads`이므로 실제 경로는 `/app/uploads/yyyy/MM/dd/{uuid}.{ext}`가 된다.
- Program.thumbnail / Program.attachment / Board.thumbnail / Board.attachment 컬럼은 File.id를 참조하는 FK(`file_id`)가 아니라, 응답 시 필요한 URL 문자열만 저장한다(외부 연동 단순화를 위해 정규화하지 않음). File 테이블은 업로드 이력·삭제 관리용으로 별도 운용한다.
- **고아 파일(orphan file) 정책**: Program/Board/Page/Popup을 삭제하거나 썸네일·첨부파일을 교체해도, 연결되어 있던 File 레코드와 실제 파일은 **자동으로 함께 삭제하지 않는다**. File 삭제는 `DELETE /api/admin/files/{id}`를 통한 관리자의 명시적 조작으로만 수행한다(P2-T4 기준). 고아 파일 일괄 정리는 본 프로젝트 범위에 포함하지 않으며, 필요 시 "향후 확장" 과제로 별도 진행한다.

---

# PK 생성 전략

모든 Entity의 `id` 기본키는 동일한 전략을 사용한다.

- DB(Flyway): `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY`
- JPA: `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- 애플리케이션에서 `id`를 직접 할당하지 않는다.
- 별도 sequence/table generator를 사용하지 않는다.
- Flyway `V1__baseline_schema.sql`과 모든 JPA Entity는 위 계약을 동일하게 적용한다.

---

# 공통 설계

모든 Entity는 BaseEntity를 상속한다.

공통 컬럼 계약:

| 컬럼 | 타입 | NULL | DB DEFAULT | 설정 주체 |
|---|---|---|---|---|
| created_at | DATETIME | NOT NULL | 없음 | JPA Auditing application-level |
| updated_at | DATETIME | NOT NULL | 없음 | JPA Auditing application-level |

DB DEFAULT 정책:

- 이 ERD의 `DB DEFAULT` 열에 `없음`으로 표시된 컬럼은 Flyway migration에 `DEFAULT` 절을 추가하지 않는다.
- API.md에서 POST 생략 기본값으로 정의된 Program `recruit_status=OPEN`, Program/Board `is_public=false`, Banner/Popup `is_visible=false`, Board `view_count=0`은 모두 application-level에서 설정하고 DB DEFAULT는 두지 않는다.
- Admin `role=ROLE_ADMIN`과 BaseEntity `created_at`/`updated_at`도 application-level에서 설정하며 DB DEFAULT는 두지 않는다.
- NULL/NOT NULL은 각 테이블의 `NULL` 열을 그대로 Flyway V1과 JPA 컬럼 제약에 반영한다.

FK 계약:

- 현재 스키마에는 Entity 간 FK가 없다.
- Admin과 Program / Board / Banner / Popup / Page 사이에 `admin_id`를 만들지 않는다.
- File 연결은 `file_id` FK가 아니라 URL 문자열 저장 정책을 사용한다.
- 문서에 명시되지 않은 FK를 임의 생성하지 않는다.

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