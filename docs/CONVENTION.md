# CONVENTION.md

# Development Convention

Version 2.0

---

# 목적

프로젝트 전반에서 일관된 개발 방식과 협업 규칙을 정의한다.

모든 개발자와 AI(Codex, Claude Code)는 본 규칙을 따른다.

---

# Git Branch Strategy

브랜치 전략

```
main
develop
feature/{feature-name}
fix/{issue-name}
hotfix/{issue-name}
```

---

# Branch Naming

예시

```
feature/admin-login

feature/program-crud

feature/board-crud

feature/page-management

feature/banner

feature/popup

fix/login-error

hotfix/security
```

소문자와 하이픈(-)을 사용한다.

---

# Commit Convention

형식

```
type: subject
```

예시

```
feat: 관리자 로그인 구현

feat: Program CRUD 구현

fix: 프로그램 수정 오류 해결

refactor: Service 구조 개선

docs: API 문서 수정

test: ProgramService 테스트 추가

chore: Gradle 설정 변경
```

---

# Commit Types

```
feat
fix
refactor
docs
style
test
chore
build
ci
```

---

# Package Convention

패키지는 모두 소문자를 사용한다.

예시

```
admin
board
program
page
banner
popup
file
common
config
security
```

---

# Class Naming

Controller

```
ProgramController
```

Admin Controller (API, `@RestController`, `/api/admin/{domain}`)

```
AdminProgramController
```

Admin View Controller (화면, `@Controller`, `/admin/{domain}`, ARCHITECTURE.md "Admin 화면(View) / API 컨트롤러 명명 규칙" 기준)

```
AdminProgramViewController
```

Service

```
ProgramService
```

Repository

```
ProgramRepository
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
findAll
findById
search
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

---

# Variable Naming

camelCase 사용

예시

```
programType

boardType

createdAt

updatedAt
```

Boolean

```
isVisible

isPublic
```

---

# Constant Naming

```
UPPER_SNAKE_CASE
```

예시

```
DEFAULT_PAGE_SIZE

MAX_UPLOAD_SIZE

UPLOAD_PATH
```

---

# Database Naming

Table

```
snake_case
```

Column

```
snake_case
```

Primary Key

```
id
```

Foreign Key

```
admin_id

program_id

board_id
```

---

# URL Convention

RESTful API를 사용한다.

예시

```
GET /api/programs

POST /api/programs

PUT /api/programs/{id}

DELETE /api/programs/{id}
```

URL에는 동사를 사용하지 않는다.

---

# Code Convention

- 메서드는 하나의 책임만 가진다.
- 중복 코드를 작성하지 않는다.
- 의미 있는 변수명을 사용한다.
- 불필요한 주석을 작성하지 않는다.
- 복잡한 로직은 메서드로 분리한다.

---

# JavaDoc

public 메서드에는 JavaDoc 작성을 권장한다.

예시

```java
/**
 * 프로그램을 등록한다.
 *
 * @param request 등록 요청 DTO
 * @return 등록 결과
 */
```

---

# Pull Request Checklist

PR 전에 확인한다.

- 컴파일 성공
- 테스트 성공
- Validation 적용
- Exception 처리
- DTO 사용
- RESTful API 준수
- Naming Convention 준수

---

# AI Convention

AI는 구현 전에 반드시 다음 문서를 확인한다.

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- CODING_RULES.md

문서에 없는 기능은 임의로 구현하지 않는다.

기존 구조를 우선 재사용한다.