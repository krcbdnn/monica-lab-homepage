# PROMPTS.md

# AI Prompt Library

Version 2.0

---

# 목적

이 문서는 Codex, Claude Code, ChatGPT에게 반복적으로 요청하는 프롬프트를 모아놓은 템플릿이다.

모든 구현은 반드시 프로젝트 문서를 기준으로 진행한다.

기준 문서

- PRD.md
- FEATURES.md
- ERD.md
- API.md
- ARCHITECTURE.md
- CODING_RULES.md
- CONVENTION.md

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
Page Entity를 이용하여

CMS 페이지 관리 기능을 구현해줘.

조건

- CKEditor5 사용
- CRUD 구현
- 이미지 업로드 지원
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
- 이미지 미리보기 지원
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