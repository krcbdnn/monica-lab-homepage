# CLAUDE.md

# AI Project Instructions

Version 3.0

본 문서는 **코드 구현 절차**를 정의합니다.

문서 수정, 커밋, 리뷰, 병합 등 프로젝트 전체 개발 절차는 docs/AI_WORKFLOW.md를 따릅니다.

---

## Project

Monika Research Institute CMS

Spring Boot 기반 교육기관 홈페이지 및 관리자 CMS 프로젝트입니다.

- 사용자는 홈페이지 콘텐츠를 조회만 합니다.
- 프로그램 신청은 Google Form URL로 이동하여 진행합니다 (신청 데이터 저장 안 함).
- 관리자만 로그인하여 홈페이지를 관리합니다.

---

## Tech Stack

- Language: Java 21
- Framework: Spring Boot 3.x
- Security: Spring Security
- ORM/Query: Spring Data JPA + QueryDSL
- Validation: Spring Validation
- Template: Thymeleaf
- Frontend: Bootstrap 5, JavaScript (ES6), CKEditor 5
- DB: MariaDB
- Build Tool: Gradle
- Deploy: Docker, Nginx, GitHub Actions

---

## Commands

```bash
# Build
./gradlew build

# Test
./gradlew test

# Run (local)
./gradlew bootRun
```

<!-- Maven 사용 시 위 명령어를 mvn 기준으로 교체 -->

---

## Directory Structure

```
src/main/java/com/project/cms/
├── admin/      (관리자 로그인)
├── page/       (기관소개: 인사말, 연혁, 오시는 길 등)
├── program/    (Program Entity, ProgramType: COURSE / SPECIAL)
├── board/      (Board Entity, BoardType: NOTICE / GALLERY / ARCHIVE)
├── banner/     (메인 배너)
├── popup/      (팝업 관리)
├── file/       (파일 관리)
├── common/     (BaseEntity, ApiResponse, GlobalExceptionHandler 등)
├── config/
└── security/

src/main/resources/
├── templates/  (Thymeleaf)
├── static/
└── application.yml
```

MVC + Layered Architecture: `Controller → Service → Repository → MariaDB`

---

## Before You Code

문서 전체를 항상 다 읽지 말고, 작업 범위에 맞춰 조건부로 확인합니다.

**항상 확인**
- docs/CODING_RULES.md
- docs/CONVENTION.md

**작업 유형별 추가 확인**
| 작업 내용 | 확인할 문서 |
|---|---|
| 신규 기능 여부 판단 | docs/PRD.md, docs/FEATURES.md |
| Entity/DB 컬럼 관련 작업 | docs/ERD.md |
| API 스펙(요청/응답) 관련 작업 | docs/API.md |
| 패키지 구조/레이어 관련 작업 | docs/ARCHITECTURE.md |

문서와 구현 내용이 다르면 항상 문서를 우선합니다.

**문서가 없거나 애매한 경우**: 임의로 추측해서 구현하지 않고, 먼저 사용자에게 질문합니다.

---

## Constraints (필수 준수)

- 관리자(Admin)만 로그인합니다. 회원가입/일반 회원 기능은 구현하지 않습니다.
- 프로그램 신청 데이터는 저장하지 않습니다 (Google Form으로 이동만 처리).
- Program, Board, Page는 각각 단일 Entity만 사용합니다.
- 새로운 Entity, API, 패키지를 임의로 생성하지 않습니다.
- 문서에 정의되지 않은 기능은 구현하지 않습니다.
- 기존 구조를 우선 재사용하고, 새 구조 생성은 최후의 수단으로만 고려합니다.

---

## Coding Rules

세부 규칙은 docs/CODING_RULES.md를 따르되, 다음은 매 구현 시 기본 적용합니다.

- DTO 사용 (Entity 직접 노출 금지)
- Validation 적용 (`@Valid` 등)
- Builder 패턴 사용
- GlobalExceptionHandler로 예외 처리 일원화
- ApiResponse로 응답 포맷 통일
- QueryDSL 사용 (복잡 조회)
- SOLID 원칙 준수

---

## Implementation Workflow

기능 하나를 구현할 때의 순서:

```
문서 확인 → Entity → Repository → Service → Controller → View → Test
```

구현 완료 후 반드시 수행:
1. `./gradlew build` (컴파일 확인)
2. `./gradlew test` (테스트 확인)
3. 리팩토링 (중복 제거, 네이밍 정리)

---

## Goal

항상 프로젝트 문서와 일치하는 코드를 작성합니다.

새로운 구조를 만드는 것보다 기존 구조를 재사용하는 것을 우선합니다.