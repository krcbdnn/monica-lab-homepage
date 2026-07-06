# CONVENTION.md

# Development Convention

Version 1.0

---

# 목적

본 문서는 프로젝트 전체에서 일관된 개발 방식과 코드 품질을 유지하기 위한 규칙을 정의한다.

모든 개발자와 AI(Codex, Claude Code)는 본 규칙을 따른다.

---

# Git Branch Strategy

Git Flow를 단순화하여 사용한다.

## Branch

main

운영 배포 브랜치

develop

개발 브랜치

feature/{feature-name}

기능 개발

fix/{issue-name}

버그 수정

hotfix/{issue-name}

운영 긴급 수정

---

# Branch Naming

예시

feature/admin-login

feature/program-crud

feature/board-crud

feature/page-management

feature/banner

feature/popup

fix/login-error

hotfix/security

---

# Commit Message

형식

```
type: subject
```

예시

```
feat: 관리자 로그인 구현

feat: Program CRUD 구현

feat: Board CRUD 구현

fix: 프로그램 수정 오류 해결

refactor: Service 구조 개선

docs: PRD 수정

style: 코드 포맷 수정

test: ProgramService 테스트 추가

chore: Gradle 의존성 추가
```

---

# Commit Type

feat

새 기능

fix

버그 수정

refactor

리팩토링

style

코드 스타일 변경

docs

문서 수정

test

테스트 코드

chore

설정 변경

build

빌드 설정

ci

CI/CD

---

# Package Convention

패키지명은 모두 소문자를 사용한다.

```
admin

page

program

board

banner

popup

file

common

security
```

---

# Class Naming

Controller

```
ProgramController
```

Service

```
ProgramService
```

Repository

```
ProgramRepository
```

Entity

```
Program
```

DTO

```
ProgramRequest

ProgramResponse
```

Enum

```
ProgramType

BoardType

PageType
```

---

# Method Naming

조회

```
find

findById

findAll
```

등록

```
create
```

수정

```
update
```

삭제

```
delete
```

검색

```
search
```

---

# Variable Naming

camelCase 사용

예시

```
programType

boardType

googleFormUrl

createdAt

updatedAt
```

Boolean

```
isPublic

isVisible

isDeleted
```

---

# Constant Naming

대문자 + 언더바

```
MAX_FILE_SIZE

DEFAULT_PAGE_SIZE

UPLOAD_PATH
```

---

# Database Convention

Table

snake_case

예시

```
admin

program

board

banner

popup

page
```

Column

snake_case

예시

```
created_at

updated_at

google_form_url

board_type

program_type
```

Primary Key

```
id
```

Foreign Key

```
admin_id
```

---

# URL Convention

Public

```
/api/programs

/api/programs/{id}

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
```

RESTful URL만 사용한다.

URL에 동사를 사용하지 않는다.

---

# API Convention

조회

GET

등록

POST

수정

PUT

부분 수정

PATCH

삭제

DELETE

---

# Response Convention

모든 API는 ApiResponse를 사용한다.

성공

```
ApiResponse.success(data)
```

실패

```
ApiResponse.fail(errorCode)
```

Entity 직접 반환 금지

---

# Exception Convention

GlobalExceptionHandler 사용

ErrorCode Enum 사용

CustomException 사용

Controller에서 try-catch 작성 금지

---

# Validation Convention

모든 Request DTO는 Validation 적용

예시

```
@NotBlank

@NotNull

@Size

@Pattern
```

---

# Entity Convention

모든 Entity는

```
BaseEntity
```

상속

Setter 최소화

Builder 사용

Protected 생성자 사용

---

# Service Convention

비즈니스 로직은 Service에서만 작성

Repository 직접 호출은 Service만 가능

Service 간 순환 참조 금지

---

# Repository Convention

JpaRepository 사용

검색은 QueryDSL 사용

JPQL 최소화

Native Query는 꼭 필요한 경우만 사용

---

# File Convention

기본 저장소

```
/upload/yyyy/MM/dd
```

파일명

UUID

DB에는 파일 경로만 저장

---

# CKEditor Convention

적용 대상

- Page
- Program
- Board
- Popup

이미지는 File API를 이용하여 업로드

---

# Program Convention

Program 하나만 사용

programType

```
COURSE

SPECIAL
```

Course Entity 생성 금지

Special Entity 생성 금지

---

# Board Convention

Board 하나만 사용

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

# Google Form Convention

신청 데이터를 저장하지 않는다.

Program에는

```
googleFormUrl
```

만 저장한다.

사용자는 Google Form으로 이동한다.

---

# Logging Convention

로그 기록

- 관리자 로그인
- 관리자 CRUD
- 파일 업로드
- 예외 발생

비밀번호 등 민감 정보는 로그에 남기지 않는다.

---

# Code Review Checklist

PR 생성 전 확인

- 기능 정상 동작
- 중복 코드 제거
- Validation 적용
- 예외 처리 완료
- DTO 사용
- Entity 직접 반환 없음
- RESTful API 준수
- JavaDoc 작성
- 테스트 완료

---

# AI Convention

AI(Codex, Claude Code)는 반드시 다음 문서를 기준으로 개발한다.

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- TASK.md
- CODING_RULES.md
- PROMPTS.md
- CONVENTION.md

문서에 정의되지 않은 기능은 임의로 추가하지 않는다.

기존 구조를 최대한 재사용한다.

Program, Board, Page 구조를 유지한다.

새로운 Entity를 생성하기 전에 기존 Entity 사용 여부를 먼저 검토한다.