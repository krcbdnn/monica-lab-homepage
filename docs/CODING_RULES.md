# CODING_RULES.md

# Coding Rules

Version 2.0

---

# 목적

프로젝트 전반에서 일관된 코드 품질과 구조를 유지하기 위한 코딩 규칙을 정의한다.

모든 개발자와 AI(Codex, Claude Code)는 본 규칙을 따른다.

---

# Java

- Java 21 사용
- Spring Boot 3.x 사용
- Lombok 사용
- Optional 적극 활용
- Stream API 적절히 활용

---

# Architecture

MVC + Layered Architecture 사용

```
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

규칙

- Controller는 HTTP 요청/응답만 처리
- Service는 비즈니스 로직만 담당
- Repository는 데이터 접근만 담당
- 계층 간 역할을 침범하지 않는다.

---

# Entity

모든 Entity는 BaseEntity를 상속한다.

공통 컬럼

- createdAt
- updatedAt

규칙

- Setter 최소화
- Builder 사용
- 생성자는 protected
- Entity 직접 반환 금지

---

# DTO

모든 API는 DTO를 사용한다.

구조

```
Request DTO
    ↓
Service
    ↓
Entity
    ↓
Response DTO
```

Request DTO에는 Validation을 적용한다.

---

# Validation

사용

- @NotBlank
- @NotNull
- @Size
- @Email
- @Pattern

Controller에서 @Valid를 적용한다.

---

# Service

규칙

- 비즈니스 로직은 Service에서만 작성
- 조회는 readOnly Transaction 사용
- 변경 작업은 Transaction 적용

---

# Repository

- JpaRepository 사용
- 검색은 QueryDSL 사용
- JPQL은 필요한 경우만 사용
- Native Query는 최소화

---

# API Response

모든 API는 ApiResponse를 사용한다.

예시

```
ApiResponse.success(data)

ApiResponse.fail(errorCode)
```

Entity 직접 반환 금지

---

# Exception

예외 처리는 GlobalExceptionHandler에서 수행한다.

사용

- CustomException
- ErrorCode Enum

Controller에서 try-catch를 작성하지 않는다.

## ErrorCode 카탈로그

`ErrorCode`는 아래 항목을 최소 기준으로 정의한다. 도메인이 늘어나면 동일한 네이밍 규칙(`{DOMAIN}_{REASON}`)으로 추가한다.

| ErrorCode | HTTP Status | 설명 |
|---|---|---|
| INVALID_INPUT_VALUE | 400 | Validation 실패(공통) |
| AUTHENTICATION_FAILED | 401 | 로그인 아이디/비밀번호 불일치(아이디가 존재하지 않는 경우 포함) |
| UNAUTHORIZED | 401 | 미인증 상태로 인증 필요 리소스 접근 |
| ACCESS_DENIED | 403 | 권한 없는 리소스 접근(ROLE_ADMIN 아님) |
| ADMIN_NOT_FOUND | 404 | (로그인 실패에는 사용하지 않음) 인증된 관리자 컨텍스트에서 특정 관리자 계정 조회 시 대상 없음 |
| PAGE_NOT_FOUND | 404 | Page 리소스 없음 |
| PROGRAM_NOT_FOUND | 404 | Program 리소스 없음 |
| BOARD_NOT_FOUND | 404 | Board 리소스 없음 |
| BANNER_NOT_FOUND | 404 | Banner 리소스 없음 |
| POPUP_NOT_FOUND | 404 | Popup 리소스 없음 |
| FILE_NOT_FOUND | 404 | File 리소스 없음 |
| DUPLICATE_LOGIN_ID | 409 | login_id 중복 (본 프로젝트 범위에는 관리자 계정 등록 API가 없어 seed 데이터 검증 등 내부 용도로만 예약됨. 향후 관리자 계정 관리 API가 추가되기 전까지는 API 응답으로 노출되지 않는다) |
| INVALID_FILE_TYPE | 400 | 허용되지 않은 확장자 업로드 |
| FILE_SIZE_EXCEEDED | 400 | 파일 업로드 용량 초과(File Upload 섹션 기준값 참고) |
| FILE_UPLOAD_FAILED | 500 | 파일 저장 중 I/O 오류 |
| INTERNAL_SERVER_ERROR | 500 | 정의되지 않은 서버 오류(공통 fallback) |

로그인 실패 시에는 아이디 존재 여부와 무관하게 항상 `AUTHENTICATION_FAILED`(401)를 반환한다. 계정 존재 여부가 응답 코드로 노출되면 아이디 추측 공격에 악용될 수 있기 때문이다. `ADMIN_NOT_FOUND`는 로그인 흐름이 아닌, 인증된 관리자 컨텍스트에서의 조회 실패에만 사용한다.

---

# Security

- Spring Security 사용
- BCryptPasswordEncoder 사용
- 인증 및 인가는 Security에서 처리

## 비밀번호 정책

- 최소 8자 이상, 영문/숫자/특수문자 중 2종 이상 조합
- Request DTO에 `@Pattern` 또는 커스텀 Validator로 적용
- 관리자 계정은 초기 데이터(seed) 또는 최초 로그인 시 등록으로 생성하며, 본 프로젝트 범위에서 별도 회원가입 화면은 제공하지 않는다(PRD.md 원칙 준수)

---

# File Upload

규칙

- UUID 파일명 사용
- 날짜별 디렉터리 저장
- DB에는 파일 경로만 저장

## 업로드 제한 값

| 상수 | 값 | 비고 |
|---|---|---|
| MAX_UPLOAD_SIZE | 10MB | 파일 1건당 최대 크기 |
| MAX_IMAGE_SIZE | 5MB | 이미지(썸네일, CKEditor 삽입 이미지) 최대 크기 |
| ALLOWED_IMAGE_EXTENSIONS | jpg, jpeg, png, gif | FEATURES.md 지원 형식 중 이미지 |
| ALLOWED_ATTACHMENT_EXTENSIONS | jpg, jpeg, png, gif, pdf, hwp, hwpx, docx, xlsx, pptx, zip | FEATURES.md 지원 형식 전체 |

초과 시 `FILE_SIZE_EXCEEDED`, 허용되지 않은 확장자는 `INVALID_FILE_TYPE`을 반환한다(위 ErrorCode 카탈로그 기준).

---

# Logging

로그 대상

- 로그인
- 관리자 CRUD
- 파일 업로드
- 예외 발생

비밀번호 등 민감한 정보는 로그에 남기지 않는다.

---

# Naming

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

Table / Column

```
snake_case
```

---

# Code Style

- 하나의 메서드는 하나의 책임만 가진다.
- 중복 코드를 작성하지 않는다.
- 공통 기능은 분리한다.
- SOLID 원칙을 따른다.
- 가독성을 우선한다.

---

# AI Rules

AI는 반드시 다음 문서를 기준으로 구현한다.

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- CONVENTION.md

구현 전 기존 구조를 먼저 확인하고 재사용한다.