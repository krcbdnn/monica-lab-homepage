# ERD (Entity Relationship Diagram)

Version 1.0

---

# 개요

본 프로젝트는 교육기관 홈페이지 CMS이다.

일반 회원 기능은 제공하지 않으며,
관리자만 로그인하여 홈페이지 콘텐츠를 관리한다.

프로그램 신청은 Google Form으로 연결하므로
신청 데이터를 별도로 저장하지 않는다.

---

# ERD

Admin (1)
│
├── Notice (N)
├── Gallery (N)
├── Archive (N)
├── Program (N)
├── Banner (N)
├── Popup (N)
└── Page (N)

---

# 1. Admin

관리자 계정

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| login_id | VARCHAR(50) | 로그인 아이디 |
| password | VARCHAR(255) | BCrypt 암호 |
| name | VARCHAR(50) | 관리자명 |
| role | VARCHAR(20) | ROLE_ADMIN |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 2. Program

수강 프로그램 및 특강 관리

※ 하나의 테이블에서 관리

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| program_type | ENUM | COURSE / SPECIAL |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| thumbnail | VARCHAR(255) | 썸네일 |
| attachment | VARCHAR(255) | 첨부파일 |
| google_form_url | VARCHAR(500) | Google Form URL |
| recruit_status | ENUM | 모집중 / 마감 |
| is_public | BOOLEAN | 공개 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 3. Notice

공지사항

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| attachment | VARCHAR(255) | 첨부파일 |
| view_count | INT | 조회수 |
| is_public | BOOLEAN | 공개 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 4. Gallery

갤러리

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| thumbnail | VARCHAR(255) | 대표 이미지 |
| view_count | INT | 조회수 |
| is_public | BOOLEAN | 공개 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 5. Archive

자료실

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| attachment | VARCHAR(255) | 첨부파일 |
| view_count | INT | 조회수 |
| is_public | BOOLEAN | 공개 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 6. Banner

메인 배너

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| title | VARCHAR(100) | 제목 |
| image | VARCHAR(255) | 이미지 |
| link_url | VARCHAR(500) | 이동 링크 |
| sort_order | INT | 정렬 순서 |
| is_visible | BOOLEAN | 노출 여부 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 7. Popup

팝업

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

# 8. Page

고정 페이지 관리

CMS에서 수정되는 페이지

- 인사말
- 기관소개
- 연혁
- 오시는 길

| 컬럼 | 타입 | 설명 |
|-------|------|------|
| id | BIGINT | PK |
| page_type | ENUM | GREETING / INTRODUCTION / HISTORY / LOCATION |
| title | VARCHAR(200) | 제목 |
| content | LONGTEXT | 내용 |
| created_at | DATETIME | 생성일 |
| updated_at | DATETIME | 수정일 |

---

# 관계

Admin

├── Program

├── Notice

├── Gallery

├── Archive

├── Banner

├── Popup

└── Page

---

# 저장소

이미지 및 첨부파일

기본

- Local Storage

확장 가능

- AWS S3

파일 경로만 DB에 저장한다.

---

# 제외 기능

다음 기능은 구현하지 않는다.

- 일반 회원
- 회원가입
- 로그인(일반 사용자)
- 마이페이지
- 신청 정보 저장
- 상담 신청
- 결제 기능

---

# 설계 원칙

- 프로그램 신청은 Google Form을 사용한다.
- CMS에서 모든 콘텐츠를 수정할 수 있다.
- 관리자만 Spring Security로 로그인한다.
- 프로그램은 하나의 Program 테이블에서 관리한다.
- 공통 컬럼(created_at, updated_at)은 BaseEntity로 관리한다.