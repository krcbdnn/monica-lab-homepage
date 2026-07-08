# CLAUDE.md

# AI Project Instructions

## Project

Monika Research Institute CMS

Spring Boot 기반 교육기관 홈페이지 및 관리자 CMS 프로젝트입니다.

사용자는 홈페이지 콘텐츠를 조회하며, 프로그램 신청은 Google Form으로 이동하여 진행합니다.

관리자만 로그인하여 홈페이지를 관리합니다.

---

## Before You Code

반드시 아래 문서를 먼저 확인합니다.

1. docs/PRD.md
2. docs/FEATURES.md
3. docs/ERD.md
4. docs/API.md
5. docs/ARCHITECTURE.md
6. docs/CODING_RULES.md
7. docs/CONVENTION.md

문서와 구현 내용이 다르면 항상 문서를 우선합니다.

---

## Project Rules

이 프로젝트에서는 다음 규칙을 반드시 지킵니다.

- 관리자(Admin)만 로그인합니다.
- 회원가입 및 일반 회원 기능은 구현하지 않습니다.
- 프로그램 신청 데이터는 저장하지 않습니다.
- Program은 하나의 Entity만 사용합니다.
- Board는 하나의 Entity만 사용합니다.
- Page는 하나의 Entity만 사용합니다.
- 프로그램 신청은 Google Form URL로 이동합니다.

---

## Development Principles

- 기존 구조를 우선 재사용합니다.
- 새로운 Entity를 임의로 생성하지 않습니다.
- 새로운 API를 임의로 생성하지 않습니다.
- 새로운 패키지를 임의로 생성하지 않습니다.
- 문서에 정의되지 않은 기능은 구현하지 않습니다.

---

## Coding

다음 규칙은 docs/CODING_RULES.md를 따릅니다.

- DTO 사용
- Validation 적용
- Builder 사용
- GlobalExceptionHandler 사용
- ApiResponse 사용
- QueryDSL 사용
- SOLID 원칙 준수

---

## Workflow

기능 개발 순서

```
문서 확인

↓

Entity

↓

Repository

↓

Service

↓

Controller

↓

View

↓

Test
```

구현이 끝나면

- 컴파일
- 테스트
- 리팩토링

을 수행합니다.

---

## Goal

항상 프로젝트 문서와 일치하는 코드를 작성합니다.

새로운 구조를 만드는 것보다 기존 구조를 재사용하는 것을 우선합니다.