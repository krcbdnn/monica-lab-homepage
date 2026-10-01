# Monika Research Institute CMS

Version 2.0

교육기관 홈페이지 및 관리자 CMS 프로젝트

Spring Boot 기반으로 구축되는 CMS이며,
관리자가 홈페이지 콘텐츠를 직접 관리할 수 있도록 설계되었다.

---

# 프로젝트 소개

본 프로젝트는 교육기관 홈페이지를 위한 CMS(Content Management System)이다.

홈페이지 방문자는 교육 프로그램과 기관 정보를 조회할 수 있으며,
프로그램 신청은 Google Form으로 이동하여 진행한다.

관리자는 CMS에서 다음 기능을 관리할 수 있다.

- 기관소개
- 프로그램
- 공지사항
- 갤러리
- 자료실
- 강의 후기
- 메인 배너
- 팝업
- 메인 고정 콘텐츠
- 공개 헤더 메뉴
- 사이트 테마(포인트 컬러 / 메인 섹션 노출)

---

# 주요 기능

## 홈페이지

- 메인 페이지
- 기관소개
- 프로그램 목록
- 프로그램 상세
- 공지사항
- 갤러리
- 자료실
- Google Form 신청

---

## 관리자 CMS

- 관리자 로그인
- Dashboard
- 기관소개 관리
- 프로그램 관리
- 게시판 관리
- 배너 관리
- 팝업 관리
- 파일 관리(업로드 이력 목록/업로드/다운로드/삭제)
- 메인 고정 콘텐츠 관리
- 메뉴 관리
- 사이트 테마 설정
- 관리자 비밀번호 변경(Phase 15, P15-T6)

기능 범위의 기준 문서는 `docs/PRD.md`, 기능 상세는 `docs/FEATURES.md`다.

---

# 기술 스택

## Backend

- Java 21
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- QueryDSL
- Validation

## Frontend

- Thymeleaf
- Bootstrap 5
- JavaScript (ES6)
- CKEditor 5

## Database

- MariaDB

## Build Tool

- Gradle

## Deploy

- Docker
- Nginx
- GitHub Actions

---

# 프로젝트 구조

```
src
└── main
    ├── java
    │   └── com.monicalab
    │       ├── admin
    │       ├── page
    │       ├── program
    │       ├── board
    │       ├── banner
    │       ├── popup
    │       ├── file
    │       ├── menu
    │       ├── pinned
    │       ├── theme
    │       ├── home
    │       ├── common
    │       └── config
    │
    └── resources
        ├── templates
        ├── static
        ├── db/migration
        ├── application.yml
        └── application-prod.yml
```

패키지/레이어 구조의 기준 문서는 `docs/ARCHITECTURE.md`다. Spring Security 설정(`SecurityConfig`)은 `config` 패키지에 있으며 별도 `security` 패키지는 없다.

---

# Domain

## Admin

관리자 로그인

---

## Page

기관소개

- 인사말
- 기관소개
- 연혁
- 오시는 길

---

## Program

Program 하나의 Entity 사용

ProgramType

- COURSE
- SPECIAL

---

## Board

Board 하나의 Entity 사용

BoardType

- NOTICE
- GALLERY
- ARCHIVE
- REVIEW(강의 후기)

---

## Banner

메인 배너

---

## Popup

팝업 관리

---

# 시스템 구조

```
Browser

↓

Controller

↓

Service

↓

Repository

↓

MariaDB
```

MVC + Layered Architecture 사용

---

# 데이터베이스

핵심 Entity

- Admin
- Program
- Board
- Page
- Banner
- Popup
- UploadFile

공통 Entity

- BaseEntity

---

# Google Form 연동

프로그램 신청은 DB에 저장하지 않는다.

관리자가 Google Form URL을 등록하면

사용자는

```
신청하기

↓

Google Form
```

으로 이동한다.

---

# CKEditor

CMS 콘텐츠는 CKEditor5를 이용하여 수정한다.

적용

- 기관소개
- 프로그램
- 게시판
- 팝업

---

# 실행 방법

## 사전 요구사항

로컬 Docker 데몬이 실행 중이어야 한다.

- 로컬 MariaDB 구동(`docker-compose.local.yml`, TASK.md P1-T4)
- 통합 테스트(`./gradlew test`)의 Testcontainers MariaDB 모듈 기동(TASK.md P1-T6)

두 용도 모두 Docker에 의존하므로, Phase 1~9 개발 및 테스트 진행 전 Docker Desktop(또는 Docker Engine)이 설치·실행되어 있는지 먼저 확인한다. CI(GitHub Actions)는 러너에 내장된 Docker를 사용하므로 별도 설정이 필요 없다(TASK.md P12-T3).

## 프로젝트 Clone

```bash
git clone https://github.com/krcbdnn/monica-lab-homepage.git
```

---

## Build

```bash
./gradlew build
```

---

## Run

```bash
./gradlew bootRun
```

---

# 설정 / Profile

- `application.yml`: 공통 설정. 기본 profile은 `local`이다.
- `application-local.yml`: 로컬 개발용(gitignore 대상, 저장소에 없음). `docker-compose.local.yml`로 띄운 로컬 MariaDB(host 3307)의 datasource 등 개발자 개인 설정을 둔다.
- `application-prod.yml`: 운영 profile. datasource(`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`), 업로드 루트, Actuator 노출 범위를 환경변수 기반으로 정의하며 `docker-compose.yml`이 `SPRING_PROFILES_ACTIVE=prod`로 실행한다.
- 운영 환경변수 목록은 `.env.example`(값 없이 변수명 위주, 실제 `.env`는 gitignore)을 따른다. 운영용 비밀번호/secret을 저장소에 기록하지 않는다.
- 테스트는 `src/test/resources/application-test.yml` + Testcontainers MariaDB를 사용한다.

---

# 문서

프로젝트 설계 문서는 docs 디렉터리에 있다.

```
docs/

PRD.md

FEATURES.md

ERD.md

API.md

ARCHITECTURE.md

TASK.md

CODING_RULES.md

PROMPTS.md

CONVENTION.md

GIT_WORKFLOW.md

AI_WORKFLOW.md

OPERATIONS.md   (운영 runbook - P15-T8)
```

`CLAUDE.md`는 `docs/`가 아니라 저장소 루트에 있다.

문서별 기준(canonical) 역할:

| 문서 | 기준 역할 |
|---|---|
| PRD.md | 기능 범위 |
| FEATURES.md | 기능 상세 / 편집기 지원 서식 / 오류 화면 동작 |
| ERD.md | DB 테이블/Entity 구조 |
| API.md | endpoint / JSON 오류 계약 |
| ARCHITECTURE.md | 구조 / 보안 / sanitizer / 운영 배포 계약 |
| CODING_RULES.md | ErrorCode / 비밀번호 / 로그 코딩 규칙 |
| TASK.md | Task / 의존성 / 완료 기준 |
| GIT_WORKFLOW.md | 브랜치 / release / tag / hotfix |
| OPERATIONS.md | 운영 명령 / runbook(서버 준비, 배포, TLS, 백업·복원, 비밀번호 recovery, rollback, smoke, Launch TBD) |

---

# 개발 규칙

본 프로젝트는 다음 문서를 기준으로 개발한다.

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- TASK.md
- CODING_RULES.md
- PROMPTS.md
- CONVENTION.md
- GIT_WORKFLOW.md
- CLAUDE.md
- AI_WORKFLOW.md

---

# Git Branch

```
main

develop

feature/*

fix/*

hotfix/*

docs/*
```

---

# Commit Message

```
feat:

fix:

refactor:

docs:

style:

test:

chore:
```

---

# 향후 확장

- 관리자 권한 분리
- SMS 알림
- 이메일 알림
- AWS S3
- AWS CloudFront
- Redis Cache
- Elasticsearch 검색

---

# License

Private Project

Copyright © Monika Research Institute

---

# 배포 운영 기준

- CI: GitHub Actions에서 Gradle test/build를 자동 검증한다(pull_request trigger).
- 배포: 운영 서버에서는 Docker Compose 기반 수동 배포를 기본으로 한다. 자동 CD workflow는 현재 범위에 포함하지 않는다. 운영 서버는 main의 release tag(예: `v1.0.0`)를 checkout하고 "tag checkout → `docker compose up -d --build`"를 하나의 배포 단위로 수행한다(GIT_WORKFLOW.md §6-1).
- 업로드 파일: Docker에서는 `/app/uploads`를 `./data/uploads:/app/uploads` bind mount로 영속화한다.
- DB: MariaDB `/var/lib/mysql`은 `db_data` named volume으로 영속화한다. Schema 변경은 `db/migration/**` Flyway migration만 사용하고 prod `ddl-auto=validate`로 검증한다.
- 초기 관리자: `ApplicationRunner`가 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME`을 읽어 미존재 시에만 BCrypt로 생성하며 운영 비밀번호를 `data.sql`에 두지 않는다. 생성 이후 `.env`의 비밀번호를 바꿔도 DB에는 반영되지 않는다.
- 헬스체크: Spring Boot Actuator `/actuator/health`를 사용한다.
- 운영 배포 준비(Phase 15): HTTPS/TLS, 로그인 시도 제한, 로그/volume/환경변수 정비 등 운영 배포 계약은 `docs/ARCHITECTURE.md` "운영 배포 계약(Phase 15)", Task는 `docs/TASK.md` "Phase 15"를 따른다. 서버 운영 명령(서버 준비, 배포, 인증서, 백업·복원, 비밀번호 recovery, rollback, smoke)은 `docs/OPERATIONS.md`를 따른다. Nginx는 `:80`(ACME + 301)/`:443`(TLS, HSTS, 로그인 rate limit)을 노출하며 인증서는 host `./data/certs/`(`fullchain.pem`/`privkey.pem`)에 두어야 기동한다(P15-T3). **실제 공개 launch 전에 OPERATIONS "Launch TBD"(최종 도메인/서버/인증서 발급/백업 외부 보관/발주처 asset 등)를 확정한다.**
