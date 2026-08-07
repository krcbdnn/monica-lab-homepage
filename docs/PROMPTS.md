# PROMPTS.md

# AI Prompt Library

Version 2.0

---

# 목적

이 문서는 Codex, Claude Code, ChatGPT에게 반복적으로 요청하는 프롬프트를 모아놓은 템플릿이다.

모든 구현은 반드시 프로젝트 문서를 기준으로 진행한다.

기준 문서 / Source of Truth 우선순위

1. 요구사항: PRD.md → FEATURES.md
2. Entity/DB: ERD.md
3. HTTP API: API.md
4. Architecture/Security/Deployment: ARCHITECTURE.md
5. Coding/Security Rules: CODING_RULES.md
6. Task/Dependency/DoD: TASK.md
7. Git: GIT_WORKFLOW.md → CONVENTION.md
8. README.md, PROMPTS.md는 보조 문서

충돌이나 누락된 결정이 있으면 구현으로 추측하지 않고 중단하여 보고한다.

---

# 1. 새로운 기능 구현

## CRUD 생성

```
docs/PRD.md
docs/FEATURES.md
docs/ERD.md
docs/API.md
docs/CODING_RULES.md

를 기준으로

{기능명} CRUD를 구현해줘.

요구사항

- RESTful API
- DTO 사용
- Validation 적용
- GlobalExceptionHandler 사용
- ApiResponse 사용
- JavaDoc 작성
- 기존 구조를 최대한 재사용
```

---

## Entity 생성

```
ERD.md를 기준으로

{Entity명} Entity를 생성해줘.

조건

- BaseEntity 상속
- Builder 사용
- Setter 최소화
- Validation 고려
- JPA Mapping 적용
```

---

## Repository 생성

```
ERD.md를 기준으로

{Entity명} Repository를 생성해줘.

조건

- JpaRepository 사용
- QueryDSL 지원
- Custom Repository 분리
```

---

## Service 생성

```
API.md와 CODING_RULES.md를 기준으로

{기능명} Service를 구현해줘.

조건

- Service에만 비즈니스 로직 작성
- @Transactional 적용
- DTO 사용
- Exception 처리
```

---

## Controller 생성

```
API.md를 기준으로

{기능명} Controller를 구현해줘.

조건

- RESTful API
- DTO 사용
- Validation 적용
- ApiResponse 반환
- Entity 직접 반환 금지
```

---

# 2. 관리자 기능

## 관리자 로그인

```
Spring Security를 사용하여

관리자 로그인 기능을 구현해줘.

조건

- ROLE_ADMIN
- Session 기반 인증
- 로그인
- 로그아웃
- 접근 권한 설정
```

---

## CMS 페이지 관리

```
CmsPage Entity를 이용하여

CMS 고정 페이지 조회/수정 기능을 구현해줘.

조건

- CKEditor5 사용
- GREETING / INTRODUCTION / HISTORY / LOCATION 4개 고정 리소스 조회/수정
- POST/DELETE API 생성 금지
- 공통 File API를 통한 이미지 업로드 지원
```

---

## 프로그램 관리

```
Program CRUD를 구현해줘.

조건

- ProgramType 사용
- COURSE
- SPECIAL
- Google Form URL 관리
- CKEditor 적용
- 파일 업로드 지원
```

---

## 게시판 관리

```
Board CRUD를 구현해줘.

조건

- BoardType 사용
- NOTICE
- GALLERY
- ARCHIVE
- 검색
- 페이징
- CKEditor 적용
```

---

# 3. 기능 개선

## QueryDSL 검색 추가

```
기존 CRUD에 QueryDSL 검색 기능을 추가해줘.

조건

- 동적 검색
- Pagination 유지
- 성능 고려
```

---

## 페이징 개선

```
기존 목록 조회를 개선해줘.

조건

- Pageable 사용
- QueryDSL 유지
- 정렬 지원
```

---

## 파일 업로드

```
파일 업로드 기능을 구현해줘.

조건

- UUID 파일명
- 날짜별 디렉토리
- Local Storage
```

---

## CKEditor 적용

```
CKEditor5를 적용해줘.

조건

- 이미지 업로드 API 사용
- HTML 저장
- XSS 고려
```

---

# 4. 리팩토링

## 코드 리팩토링

```
현재 코드를 리뷰하고 리팩토링해줘.

조건

- SOLID 원칙
- 중복 제거
- 가독성 향상
- 성능 개선
- 기존 구조 유지
```

---

## Service 리팩토링

```
Service 계층을 리팩토링해줘.

조건

- 메서드 분리
- 책임 분리
- 중복 제거
```

---

## QueryDSL 적용

```
기존 Repository를 QueryDSL 기반으로 개선해줘.

조건

- 성능 고려
- 동적 검색 지원
```

---

# 5. 코드 리뷰

## 전체 코드 리뷰

```
현재 구현된 코드를 리뷰해줘.

확인 항목

- SOLID 원칙
- DTO 사용 여부
- Validation
- Exception 처리
- RESTful API
- 성능
- 가독성
- 중복 코드
```

---

## 보안 리뷰

```
Spring Security 관점에서

현재 코드를 리뷰해줘.

확인 항목

- 인증
- 인가
- CSRF
- XSS
- SQL Injection
- 파일 업로드 보안
```

---

## 성능 리뷰

```
성능 개선이 가능한 부분을 찾아줘.

확인 항목

- Query
- N+1 문제
- Index
- Cache
- Pagination
```

---

# 6. 테스트

## 테스트 코드 생성

```
현재 Service의 테스트 코드를 작성해줘.

조건

- JUnit5
- Mockito
- 성공 케이스
- 실패 케이스
```

---

## 버그 수정

```
다음 오류를 분석하고 수정해줘.

원인 분석

↓

해결 방법

↓

수정 코드

↓

영향 범위

순서로 설명해줘.
```

---

# 7. 문서 기반 구현

## ERD 기준 구현

```
ERD.md만 기준으로

Entity와 Repository를 구현해줘.
```

---

## API 기준 구현

```
API.md를 기준으로

Controller와 Service를 구현해줘.
```

---

## Architecture 기준 구현

```
ARCHITECTURE.md를 기준으로

프로젝트 구조를 생성해줘.
```

---

# 8. 개발 완료 체크

```
다음 항목을 확인해줘.

- PRD와 일치
- FEATURES와 일치
- ERD와 일치
- API와 일치
- Architecture와 일치
- DTO 사용
- Validation 적용
- Exception 처리
- 테스트 완료
- 리팩토링 완료

문제가 있으면 수정안을 제안해줘.
```

---

# 9. Claude Code Harness 전체 실행

```
docs/TASK.md를 읽고 의존성을 확인한 뒤 P1-T1부터 순서대로 구현해.
각 Task마다 관련 Source of Truth를 확인하고 구현·테스트·build·DoD 검증 후 다음 Task로 이동해.
문서 충돌이나 누락된 결정이 있으면 추측하지 말고 중단해서 보고해.
설계 문서는 임의 수정하지 마.
```

추가 규칙:
- 관리자 화면 데이터는 공개 API가 아니라 API.md의 `/api/admin/**` GET을 사용한다.
- 운영 런타임은 TASK.md P1-T7/P12-T1의 Flyway, prod `ddl-auto=validate`, Actuator health, 환경변수, DB/upload 영속성 계약을 따른다.
- 자동 배포(CD)는 만들지 않는다. GitHub Actions는 test/build CI만 수행한다.
