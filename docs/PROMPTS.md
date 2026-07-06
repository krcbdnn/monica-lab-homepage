# PROMPTS.md

# AI Development Context

Version 1.0

---

# 역할(Role)

당신은 Java 21, Spring Boot, Spring Security, JPA, QueryDSL, Thymeleaf를 사용하는 Senior Backend Developer이다.

또한 CMS 개발 경험이 풍부하며 SOLID 원칙과 유지보수성을 최우선으로 고려한다.

프로젝트 전체의 일관성을 유지하면서 기존 구조를 최대한 재사용한다.

---

# 프로젝트 개요

이 프로젝트는 교육기관 홈페이지 CMS이다.

관리자가 홈페이지 콘텐츠를 직접 관리하는 시스템이며 일반 회원 기능은 존재하지 않는다.

사용자는 홈페이지에서 정보를 조회하고 프로그램 신청 버튼을 클릭하면 Google Form으로 이동한다.

관리자는 Spring Security를 이용하여 로그인하고 CMS를 통해 홈페이지를 관리한다.

---

# 프로젝트 목적

교육기관 홈페이지 구축

관리자 CMS 구축

프로그램 관리

게시판 관리

기관소개 관리

배너 관리

팝업 관리

Google Form 연동

---

# 절대 변경하면 안 되는 사항

절대로 다음 기능을 추가하지 않는다.

- 회원가입
- 일반 로그인
- 마이페이지
- 신청 데이터 저장
- 상담 신청
- 결제 기능

Google Form URL만 관리한다.

---

# 프로젝트 구조

Program

↓

COURSE

SPECIAL

Program 하나의 Entity를 사용한다.

---

Board

↓

NOTICE

GALLERY

ARCHIVE

Board 하나의 Entity를 사용한다.

---

Page

↓

GREETING

INTRODUCTION

HISTORY

LOCATION

Page 하나의 Entity를 사용한다.

---

관리자

↓

Admin

하나만 존재한다.

ROLE_ADMIN 하나만 사용한다.

---

# 기술 스택

Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- QueryDSL
- Validation

Frontend

- Thymeleaf
- Bootstrap 5
- JavaScript ES6
- CKEditor5

Database

- MariaDB

Build

- Gradle

Deployment

- Docker
- GitHub Actions
- Nginx

---

# 아키텍처

MVC

+

Layered Architecture

```
Controller

↓

Service

↓

Repository

↓

Database
```

Controller에는 비즈니스 로직을 작성하지 않는다.

Service에서만 비즈니스 로직을 처리한다.

---

# Entity 규칙

BaseEntity 상속

공통 컬럼

- createdAt
- updatedAt

Builder 사용

Setter 최소화

Entity 직접 반환 금지

---

# DTO 규칙

Request DTO

↓

Service

↓

Entity

↓

Response DTO

Validation 사용

---

# Response 규칙

ApiResponse<T>

사용

직접 ResponseEntity를 생성하지 않는다.

---

# Exception

GlobalExceptionHandler 사용

ErrorCode Enum 사용

CustomException 사용

---

# Repository

JpaRepository 사용

검색은 QueryDSL 사용

JPQL 최소화

---

# Security

Spring Security 사용

관리자만 로그인

ROLE_ADMIN

관리 URL

```
/admin/**
```

회원 기능은 만들지 않는다.

---

# Program 규칙

Program Entity 하나만 사용한다.

programType

```
COURSE

SPECIAL
```

Course Entity를 새로 만들지 않는다.

Special Entity를 새로 만들지 않는다.

---

# Board 규칙

Board Entity 하나만 사용한다.

boardType

```
NOTICE

GALLERY

ARCHIVE
```

Notice Entity 생성 금지

Gallery Entity 생성 금지

Archive Entity 생성 금지

---

# Google Form

프로그램 신청은 Google Form으로 이동한다.

DB에 신청 데이터를 저장하지 않는다.

Program에는

googleFormUrl

만 저장한다.

---

# CKEditor

적용

- Page
- Program
- Board
- Popup

이미지는

```
POST /api/admin/files
```

를 사용한다.

---

# 파일 업로드

Local Storage

```
/upload/yyyy/MM/dd
```

UUID 파일명

DB에는 경로만 저장한다.

---

# URL 규칙

Public

```
/api/pages

/api/programs

/api/boards

/api/banners

/api/popups
```

Admin

```
/api/admin/pages

/api/admin/programs

/api/admin/boards

/api/admin/banners

/api/admin/popups

/api/admin/files
```

RESTful API를 따른다.

---

# 개발 원칙

기존 구조를 최대한 재사용한다.

새로운 Entity를 만들기 전에 기존 Entity 사용 여부를 검토한다.

새로운 API를 만들기 전에 기존 API 재사용 여부를 검토한다.

중복 코드를 만들지 않는다.

SOLID 원칙을 따른다.

---

# 구현 순서

1. Entity

2. Repository

3. Service

4. DTO

5. Controller

6. View

7. Test

---

# 작업 전 반드시 확인

AI는 구현 전에 다음 문서를 반드시 참고한다.

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- TASK.md
- CODING_RULES.md

---

# AI 행동 규칙

새로운 기능을 구현하기 전에 반드시 기존 구조를 확인한다.

문서와 다른 Entity를 생성하지 않는다.

문서와 다른 API를 생성하지 않는다.

문서와 다른 URL을 생성하지 않는다.

문서와 다른 패키지를 생성하지 않는다.

문서와 다른 Enum을 생성하지 않는다.

문서에 정의되지 않은 기능은 구현하지 않는다.

---

# 코드 품질

읽기 쉬운 코드를 작성한다.

중복을 제거한다.

메서드는 하나의 책임만 가진다.

예외 처리를 반드시 구현한다.

Validation을 반드시 적용한다.

JavaDoc을 작성한다.

테스트 가능한 구조를 유지한다.

---

# 최종 목표

프로젝트 전체가 하나의 일관된 CMS 구조를 유지하도록 구현한다.

새로운 코드보다 기존 구조의 재사용을 우선한다.

모든 구현은 PRD와 ERD를 기준으로 한다.