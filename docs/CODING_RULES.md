# CODING_RULES.md

# Coding Rules

Version 1.0

---

# 목적

본 문서는 프로젝트 전반에서 일관된 코드 품질과 구조를 유지하기 위한 개발 규칙을 정의한다.

모든 개발자는 본 문서를 준수하며, AI(Codex, Claude Code) 역시 동일한 규칙을 따른다.

---

# 프로젝트 환경

- Java 21
- Spring Boot 3.x
- Gradle
- MariaDB
- Spring Security
- Spring Data JPA
- QueryDSL
- Thymeleaf
- Bootstrap 5
- CKEditor 5

---

# 프로젝트 구조

Layered Architecture를 사용한다.

```
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Controller에서 비즈니스 로직을 작성하지 않는다.

비즈니스 로직은 Service에서만 처리한다.

Repository는 데이터 접근만 담당한다.

---

# 패키지 구조

```
com.project.cms

config

common

security

admin

page

program

board

banner

popup

file
```

각 도메인은 동일한 구조를 가진다.

```
controller

service

repository

entity

dto
```

---

# Entity 규칙

모든 Entity는 BaseEntity를 상속한다.

공통 컬럼

- createdAt
- updatedAt

Entity에는 Setter 사용을 최소화한다.

생성자는 protected로 선언한다.

Lombok 사용

```
@Getter

@NoArgsConstructor(access = AccessLevel.PROTECTED)

@Builder
```

---

# DTO 규칙

Entity를 직접 반환하지 않는다.

모든 API는 DTO를 사용한다.

구조

```
Request DTO

↓

Entity

↓

Response DTO
```

DTO에는 Validation을 적용한다.

예)

```
@NotBlank

@NotNull

@Size

@Email
```

---

# Controller 규칙

Controller는 요청과 응답만 처리한다.

금지

- 비즈니스 로직 작성
- Entity 직접 반환
- Repository 직접 호출

Controller → Service만 호출한다.

---

# Service 규칙

모든 비즈니스 로직은 Service에서 처리한다.

트랜잭션은 Service에서 관리한다.

```
@Transactional
```

조회

```
@Transactional(readOnly = true)
```

---

# Repository 규칙

JpaRepository 사용

검색 기능은 QueryDSL 사용

JPQL 사용을 최소화한다.

---

# Program 규칙

수강 프로그램과 특강은 하나의 Entity를 사용한다.

```
Program
```

구분

```
ProgramType

COURSE

SPECIAL
```

절대로 Course Entity와 Special Entity를 따로 만들지 않는다.

---

# Board 규칙

공지사항

갤러리

자료실

하나의 Entity 사용

```
Board
```

구분

```
BoardType

NOTICE

GALLERY

ARCHIVE
```

절대로 Notice Entity, Gallery Entity, Archive Entity를 각각 만들지 않는다.

---

# Google Form

신청 데이터는 저장하지 않는다.

Program에는

```
googleFormUrl
```

만 저장한다.

사용자는

```
신청하기

↓

Google Form 이동
```

구조를 사용한다.

Application Entity를 생성하지 않는다.

---

# Page 관리

기관소개

인사말

연혁

오시는 길

하나의 Page Entity를 사용한다.

구분

```
PageType

GREETING

INTRODUCTION

HISTORY

LOCATION
```

---

# Security

Spring Security 사용

권한

```
ROLE_ADMIN
```

단일 권한 사용

관리자 URL

```
/admin/**
```

보호

회원 기능은 구현하지 않는다.

---

# Response 규칙

공통 응답 사용

```
ApiResponse<T>
```

직접 ResponseEntity를 생성하지 않는다.

예)

```
ApiResponse.success()

ApiResponse.fail()
```

---

# Exception

GlobalExceptionHandler 사용

Exception을 Controller에서 처리하지 않는다.

CustomException 사용

ErrorCode Enum 사용

---

# Validation

모든 Request DTO에 Validation 적용

필수

```
@NotBlank

@NotNull

@Size

@Pattern

@Email
```

---

# 파일 업로드

기본 저장소

```
Local Storage
```

경로

```
/upload/yyyy/MM/dd
```

파일명

UUID 사용

DB에는 파일 경로만 저장한다.

확장 가능

AWS S3

---

# CKEditor

CKEditor5 사용

적용

- Page
- Program
- Board
- Popup

이미지 업로드 API

```
POST /api/admin/files
```

---

# URL 규칙

Public

```
/api/programs

/api/boards

/api/pages
```

Admin

```
/api/admin/programs

/api/admin/boards

/api/admin/pages

/api/admin/banners

/api/admin/popups

/api/admin/files
```

RESTful 규칙을 따른다.

동사 대신 HTTP Method를 사용한다.

---

# API 규칙

조회

```
GET
```

등록

```
POST
```

수정

```
PUT
```

부분 수정

```
PATCH
```

삭제

```
DELETE
```

---

# Naming Convention

Class

```
PascalCase
```

Method

```
camelCase
```

Variable

```
camelCase
```

Constant

```
UPPER_SNAKE_CASE
```

Package

```
lowercase
```

Table

```
snake_case
```

Column

```
snake_case
```

---

# Logging

로그 기록

- 관리자 로그인
- 예외
- 파일 업로드
- 관리자 CRUD

민감한 정보(비밀번호 등)는 로그에 남기지 않는다.

---

# 금지 사항

다음 기능은 구현하지 않는다.

- 회원가입
- 일반 로그인
- 마이페이지
- 신청 데이터 저장
- 상담 신청
- 결제 기능

---

# 코드 스타일

메서드는 하나의 책임만 가진다.

중복 코드를 작성하지 않는다.

공통 기능은 Util 또는 Service로 분리한다.

SOLID 원칙을 준수한다.

코드보다 가독성을 우선한다.

---

# AI 개발 규칙

Codex 또는 Claude Code는 반드시 다음 문서를 기준으로 구현한다.

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- TASK.md
- CODING_RULES.md

위 문서에 없는 Entity, API, 기능을 임의로 추가하지 않는다.

구현 전 기존 구조를 우선 재사용한다.

새로운 구조가 필요하다면 기존 구조를 변경하기 전에 이유를 제시한다.