# ARCHITECTURE.md

# System Architecture

Version 2.0

---

# Architecture

Spring Boot 기반 MVC + Layered Architecture를 사용한다.

```
Client
    │
    ▼
Controller
    │
    ▼
Service
    │
    ▼
Repository
    │
    ▼
MariaDB
```

모든 비즈니스 로직은 Service 계층에서 처리한다.

---

# Package Structure

```
src/main/java
└── com.project.cms
    │
    ├── config
    ├── security
    ├── common
    │   ├── config
    │   ├── dto
    │   ├── entity
    │   ├── exception
    │   ├── response
    │   └── util
    │
    ├── admin
    │
    ├── page
    │
    ├── program
    │
    ├── board
    │
    ├── banner
    │
    ├── popup
    │
    ├── file
    │
    └── home
```

패키지 설명

- `config` : 애플리케이션 전역 설정(WebConfig, SecurityConfig 등 프로젝트 전체에 적용되는 구성)
- `security` : Spring Security 인증/인가 관련 클래스(로그인 처리, 접근 제어 등)
- `common` : 여러 도메인에서 공통으로 사용하는 클래스 모음
  - `common.config` : common 패키지 내부에서 사용하는 설정(QueryDSL 설정, 공통 리소스 설정 등). 최상위 `config`와 달리 common 모듈 범위에 한정된 설정을 담당한다.
  - `dto` : 공통으로 사용하는 Request/Response DTO
  - `entity` : BaseEntity 등 공통 Entity
  - `exception` : CustomException, ErrorCode 등 예외 관련 클래스
  - `response` : ApiResponse 등 공통 응답 포맷
  - `util` : FileUtil, DateUtil 등 공통 유틸리티
- `admin`, `page`, `program`, `board`, `banner`, `popup`, `file` : 도메인별 패키지. 각 패키지는 Controller, Service, Repository로 구성되는 Layered Architecture를 따른다.
- `home` : 공개 메인 화면(`GET /`) 전용 패키지. 자체 Entity/Repository 없이 Page, Program, Board, Banner, Popup Service를 조합하여 메인 화면 데이터를 구성하는 Controller만 포함한다.

---

# Domain

## Admin

관리자 로그인

```
AdminController
AdminService
AdminRepository
```

기능

- 로그인
- 로그아웃
- 대시보드

---

## Page

CMS 페이지 관리

관리 페이지

- 인사말
- 기관소개
- 연혁
- 오시는 길

```
PageController
AdminPageController

PageService

PageRepository
```

pageType

```
GREETING

INTRODUCTION

HISTORY

LOCATION
```

---

## Program

수강 프로그램과 특강을 하나의 도메인으로 관리한다.

```
ProgramController

AdminProgramController

ProgramService

ProgramRepository
```

programType

```
COURSE

SPECIAL
```

관리 항목

- 제목
- 내용
- 썸네일
- 첨부파일
- Google Form URL
- 모집 상태
- 공개 여부

---

## Board

공지사항

갤러리

자료실

모두 하나의 Board 도메인으로 관리한다.

```
BoardController

AdminBoardController

BoardService

BoardRepository
```

boardType

```
NOTICE

GALLERY

ARCHIVE
```

관리 항목

- 제목
- 내용
- 첨부파일
- 썸네일
- 조회수
- 공개 여부

---

## Banner

```
BannerController

AdminBannerController

BannerService

BannerRepository
```

기능

- 등록
- 수정
- 삭제
- 노출 여부
- 정렬 순서

---

## Popup

```
PopupController

AdminPopupController

PopupService

PopupRepository
```

기능

- 등록
- 수정
- 삭제
- 노출 여부
- 시작일
- 종료일

---

## Home

공개 메인 화면(`GET /`)

```
HomeController
```

기능

- 메인 배너 조회
- 팝업 조회
- 최신 공지/갤러리 조회
- 프로그램 바로가기

자체 Entity/Repository 없이 Banner, Popup, Board, Program Service를 조합하여 사용한다.

---

## File

```
FileController

FileService
```

기능

- 이미지 업로드
- 첨부파일 업로드
- 삭제

저장소

- Local Storage
- AWS S3(확장)

---

# CKEditor5

모든 콘텐츠는 CKEditor5를 이용하여 수정한다.

적용 대상

- 기관소개
- 프로그램
- 게시판
- 팝업

기능

- 텍스트
- 이미지
- 표
- 링크
- 파일 첨부

이미지 업로드

```
POST /api/admin/files
```

이미지 URL을 반환하여 CKEditor에 삽입한다.

---

# Common

공통 클래스

```
BaseEntity

ApiResponse

ErrorCode

CustomException

GlobalExceptionHandler

FileUtil

DateUtil
```

BaseEntity

```
createdAt

updatedAt
```

모든 Entity 공통 사용

---

# Security

Spring Security 사용

관리자 인증이 필요한 대상은 화면 접근과 API 호출로 구분된다.

- `/admin/**` : 관리자 화면(Thymeleaf) 접근 경로
- `/api/admin/**` : 관리자 REST API 호출 경로(API.md 기준)

두 경로 모두 동일한 세션 기반 인증(ROLE_ADMIN)을 사용하며, Spring Security가 두 경로를 함께 인증 대상으로 처리한다.

인증 대상

```
/admin/**
/api/admin/**
```

비로그인 접근

```
/

/page/**

/programs/**

/boards/**

/banners

/popups
```

일반 사용자가 이용하는 조회용 API(`/api/pages/**`, `/api/programs/**`, `/api/boards/**`, `/api/banners`, `/api/popups`, `/api/files/{id}` 등)는 인증 없이 접근 가능하며, `/api/admin/**` 하위 API만 인증을 요구한다(API.md 기준).

권한

```
ROLE_ADMIN
```

단일 권한 사용

---

# DTO

Entity는 직접 반환하지 않는다.

```
Controller

↓

Request DTO

↓

Service

↓

Entity

↓

Response DTO
```

---

# Exception

GlobalExceptionHandler

처리

- Validation
- Authentication
- Authorization
- File Upload
- Business Exception

---

# Logging

로그 관리

- 로그인
- 예외
- 파일 업로드
- 관리자 작업

---

# Database

MariaDB

ORM

- Spring Data JPA
- QueryDSL

공통 Entity

```
BaseEntity
```

---

# File Storage

기본

```
/upload/yyyy/MM/dd
```

파일명

```
UUID
```

DB에는 경로만 저장한다.

---

# Frontend

- Thymeleaf
- Bootstrap 5
- JavaScript ES6
- CKEditor5

---

# URL

Public

```
/

/pages/{type}

/programs

/programs/{id}

/boards

/boards/{id}

/banners

/popups
```

Admin

```
/admin/login

/admin/dashboard

/admin/pages

/admin/programs

/admin/boards

/admin/banners

/admin/popups

/admin/files
```

---

# Design Principles

- MVC Architecture
- Layered Architecture
- Repository Pattern
- DTO Pattern
- Builder Pattern
- RESTful API
- DI
- SRP
- BaseEntity 공통 사용

도메인 설계 원칙(Program/Board 통합, Google Form 연동 등)은 ERD.md의 "설계 원칙"을 따른다.