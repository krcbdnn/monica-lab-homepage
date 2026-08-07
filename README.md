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
- 메인 배너
- 팝업

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
    │       ├── home
    │       ├── common
    │       ├── config
    │       └── security
    │
    └── resources
        ├── templates
        ├── static
        └── application.yml
```

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
git clone https://github.com/your-repository.git
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

# application.yml

다음 설정이 필요하다.

```
spring.datasource.url

spring.datasource.username

spring.datasource.password
```

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

CLAUDE.md

AI_WORKFLOW.md
```

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

- CI: GitHub Actions에서 Gradle test/build를 자동 검증한다.
- 배포: 운영 서버에서는 Docker Compose 기반 수동 배포를 기본으로 한다. 자동 CD workflow는 현재 범위에 포함하지 않는다.
- 업로드 파일: `${UPLOAD_ROOT:/app/uploads}`를 사용하며 Docker에서는 `./data/uploads:/app/uploads` bind mount로 영속화한다.
- DB: MariaDB `/var/lib/mysql`은 `db_data` named volume으로 영속화한다. Schema 변경은 `db/migration/**` Flyway migration만 사용하고 prod `ddl-auto=validate`로 검증한다.
- 초기 관리자: `ApplicationRunner`가 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME`을 읽어 미존재 시에만 BCrypt로 생성하며 운영 비밀번호를 `data.sql`에 두지 않는다.
- 헬스체크: Spring Boot Actuator `/actuator/health`를 사용한다.
