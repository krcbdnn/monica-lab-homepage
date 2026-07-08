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

---

# Security

- Spring Security 사용
- BCryptPasswordEncoder 사용
- 인증 및 인가는 Security에서 처리

---

# File Upload

규칙

- UUID 파일명 사용
- 날짜별 디렉터리 저장
- DB에는 파일 경로만 저장

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