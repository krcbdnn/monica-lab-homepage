# AI_WORKFLOW.md

# AI Development Workflow

Version 1.0

---

# 목적

본 문서는 AI(Codex, Claude Code, ChatGPT)를 이용하여 프로젝트를 개발하는 표준 절차를 정의한다.

AI는 개발 도구이며, 최종 설계와 검토는 개발자가 수행한다.

---

# 사용 AI

| AI | 역할 |
|------|------|
| ChatGPT | 기획, 설계, 문서 작성, 코드 리뷰 |
| Codex | 기능 구현 및 리팩토링 |
| Claude Code | 대규모 코드 수정 및 프로젝트 분석 |

---

# 개발 순서

모든 기능은 반드시 아래 순서를 따른다.

```
기획

↓

문서 수정(PRD 등)

↓

Git Commit

↓

Codex 구현

↓

코드 리뷰

↓

테스트

↓

리팩토링

↓

Commit

↓

Pull Request

↓

Merge
```

---

# AI 사용 원칙

AI는 반드시 프로젝트 문서를 기준으로 개발한다.

우선순위

```
PRD.md

↓

FEATURES.md

↓

ERD.md

↓

API.md

↓

ARCHITECTURE.md

↓

CODING_RULES.md

↓

CONVENTION.md

↓

PROMPTS.md
```

문서보다 AI의 판단을 우선하지 않는다.

---

# 기능 개발 Workflow

예)

Program CRUD

순서

```
TASK.md 확인

↓

ERD 확인

↓

API 확인

↓

Codex 구현

↓

컴파일

↓

테스트

↓

Commit
```

---

# 새로운 기능 추가

새로운 기능은 반드시 아래 순서를 따른다.

```
PRD 수정

↓

FEATURES 수정

↓

ERD 수정

↓

API 수정

↓

ARCHITECTURE 수정

↓

TASK 수정

↓

Git Commit

↓

Codex 구현
```

문서를 수정하지 않고 코드를 먼저 수정하지 않는다.

---

# Codex Workflow

## 1단계

Git 최신화

```
git pull
```

---

## 2단계

새 브랜치 생성

```
feature/program-crud
```

---

## 3단계

Codex 실행

---

## 4단계

작업 요청

예시

```
ERD.md

API.md

CODING_RULES.md

기준으로

Program CRUD를 구현해줘.
```

---

## 5단계

생성 코드 리뷰

확인

- Entity
- DTO
- Service
- API
- Validation
- Exception

---

## 6단계

테스트

```
./gradlew test
```

---

## 7단계

Commit

---

# Claude Code Workflow

Claude Code는

대규모 수정

리팩토링

프로젝트 분석

에 사용한다.

예시

```
Board 전체 리팩토링

DTO 구조 개선

중복 코드 제거

성능 개선
```

---

# ChatGPT Workflow

ChatGPT는

다음 작업에 사용한다.

- 요구사항 분석
- PRD 작성
- ERD 설계
- API 설계
- Architecture 설계
- 코드 리뷰
- 리팩토링 아이디어
- 버그 분석

ChatGPT는 직접 프로젝트 구조를 변경하지 않는다.

---

# AI Prompt 작성 규칙

항상

```
무엇을

왜

기준 문서

출력 형식
```

을 포함한다.

예시

```
ERD.md

API.md

CODING_RULES.md

기준으로

Program CRUD를 구현해줘.

RESTful API를 사용하고

DTO를 분리해줘.
```

---

# 코드 생성 후 체크리스트

반드시 확인

- 컴파일 성공
- 테스트 성공
- DTO 사용
- Entity 직접 반환 없음
- Validation 적용
- Exception 처리
- JavaDoc 작성
- Naming Convention 준수

---

# AI에게 맡기지 않는 작업

다음 작업은 사람이 직접 수행한다.

- 요구사항 최종 결정
- UI 디자인 확정
- 운영 서버 설정
- 배포 승인
- DB 백업
- 고객 요구사항 변경 결정

---

# Git Workflow

```
develop

↓

feature 브랜치 생성

↓

Codex 구현

↓

테스트

↓

Commit

↓

Pull Request

↓

Code Review

↓

develop Merge

↓

main Merge
```

---

# Commit 규칙

작은 단위로 Commit한다.

좋은 예

```
feat: Program Entity 추가

feat: Program Repository 추가

feat: Program Service 구현

feat: Program Controller 구현
```

나쁜 예

```
feat: 프로젝트 전체 완성
```

---

# Pull Request Checklist

- 기능 정상 동작
- 테스트 완료
- 예외 처리
- Validation
- DTO 사용
- JavaDoc 작성
- PRD와 일치
- API와 일치
- ERD와 일치

---

# 버그 수정 Workflow

```
Issue 생성

↓

원인 분석

↓

fix 브랜치 생성

↓

Codex 수정

↓

테스트

↓

Merge
```

---

# 문서 변경 Workflow

코드보다 문서를 먼저 수정한다.

순서

```
PRD

↓

FEATURES

↓

ERD

↓

API

↓

ARCHITECTURE

↓

TASK

↓

Commit

↓

Codex 구현
```

---

# 프로젝트 완료 기준

다음 항목을 모두 만족해야 한다.

- PRD 구현 완료
- 모든 TASK 완료
- 테스트 성공
- 코드 리뷰 완료
- 예외 처리 완료
- 반응형 적용
- 관리자 기능 완료
- Google Form 정상 연결
- 문서 최신 상태 유지

---

# AI 개발 원칙

AI는 개발을 보조하는 도구이다.

프로젝트의 기준은 항상 문서이다.

새로운 기능을 추가하기 전에 반드시 기존 문서를 확인한다.

문서와 다른 Entity, API, 구조를 임의로 생성하지 않는다.

기존 구조를 최대한 재사용한다.

---

# 최종 목표

문서와 코드가 항상 동일한 상태를 유지한다.

AI를 활용하여 개발 속도를 높이되, 프로젝트의 구조와 품질은 일관되게 유지한다.