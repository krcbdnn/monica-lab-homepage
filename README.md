# Monika Research Institute CMS

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
- 파일 관리

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
    │   └── com.project.cms
    │       ├── admin
    │       ├── page
    │       ├── program
    │       ├── board
    │       ├── banner
    │       ├── popup
    │       ├── file
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