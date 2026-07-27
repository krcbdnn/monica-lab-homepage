# AI_WORKFLOW.md

# AI Development Workflow

Version 2.0

---

# 목적

AI(Codex, Claude Code, ChatGPT)를 프로젝트 개발에 일관되게 활용하기 위한 표준 작업 절차를 정의한다.

본 문서는 **프로젝트 전체 개발 절차**(요구사항 확인, 문서 수정, 커밋, 리뷰, 병합 등)를 정의한다.

코드 구현 세부 절차(Entity → Repository → Service → Controller → View → Test)는 docs/CLAUDE.md를 따른다.

---

# AI 역할

본 프로젝트는 TASK.md(Harness Engineering 방식)와 CLAUDE.md 기준으로 **Claude Code가 Entity → Repository → Service → Controller → View → Test 전 구현 단계를 수행하는 것을 기본 워크플로우로 한다.** ChatGPT와 Codex는 아래와 같이 보조적으로 사용할 수 있으나, 필수 구성 요소는 아니다.

| AI | 역할 |
|------|------|
| Claude Code | TASK.md 기반 기능 구현(Entity/Repository/Service/Controller/View/Test), 리팩토링, 대규모 수정, 프로젝트 분석 |
| ChatGPT (선택) | 요구사항 분석, 설계, 코드 리뷰 보조 |
| Codex (선택) | CRUD 구현 보조(사용 시에도 최종 산출물은 CLAUDE.md 절차와 docs/ 문서를 기준으로 검증) |

아래 "Codex 사용 시점" 절은 Codex를 보조적으로 활용하는 경우의 참고 기준이며, 본 프로젝트의 기본 실행 주체는 Claude Code(TASK.md)이다.

---

# 표준 개발 절차

모든 기능은 아래 순서를 따른다.

```
요구사항 확인

↓

관련 문서 수정

↓

Git Commit

↓

AI 구현

(코드 구현 세부 절차는 CLAUDE.md 참고)

↓

개발자 리뷰

↓

테스트

↓

Commit

↓

Merge
```

---

# ChatGPT 사용 시점

다음 작업에서 사용한다.

- 요구사항 분석
- PRD 수정
- 기능 설계
- ERD 수정
- API 설계
- Architecture 검토
- 코드 리뷰
- 버그 원인 분석

---

# Codex 사용 시점

다음 작업에서 사용한다.

- CRUD 구현
- Controller 생성
- Service 생성
- Repository 생성
- DTO 생성
- Validation 적용
- 테스트 코드 작성

---

# Claude Code 사용 시점

다음 작업에서 사용한다.

- 프로젝트 전체 분석
- 리팩토링
- 구조 개선
- 중복 코드 제거
- 성능 개선
- 코드 품질 검토

---

# 기능 개발 순서

기능 하나를 개발할 때의 순서(ChatGPT/Codex를 보조적으로 병행하는 경우의 참고 흐름이며, Claude Code 단독으로 TASK.md를 실행하는 경우 5번(Codex 구현)은 Claude Code가 CLAUDE.md 절차에 따라 대체 수행한다)

```
1. TASK 확인

↓

2. PRD 확인

↓

3. ERD 확인

↓

4. API 확인

↓

5. Codex 구현

↓

6. 개발자 리뷰

↓

7. 테스트

↓

8. Claude Code 리팩토링

↓

9. Commit
```

---

# 새로운 기능 추가

새로운 기능이 필요한 경우

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

Commit

↓

AI 구현
```

문서를 먼저 수정한 후 구현한다.

---

# 버그 수정

```
오류 확인

↓

원인 분석(ChatGPT)

↓

수정(Codex)

↓

리뷰(Claude Code)

↓

테스트

↓

Commit
```

---

# 완료 전 체크

- 컴파일 성공
- 테스트 성공
- 코드 리뷰 완료
- 문서와 구현 내용 일치
- Git Commit 완료

---

# TASK.md(Harness Engineering)와의 관계

TASK.md는 각 태스크(P{phase}-T{n})의 완료 기준(DoD)에 해당 기능의 테스트 통과를 포함한다.
따라서 위 "표준 개발 절차"의 테스트 단계는 TASK.md의 각 태스크를 완료할 때마다 그 자리에서
수행하는 것을 기본으로 하며, TASK.md의 `Phase 10`(P10-T1 기능 테스트 스위트, P10-T2 예외 처리 테스트)은
이를 대체하는 것이 아니라 Phase 1~9 전체에 대한 회귀 테스트와 ErrorCode 전수 테스트를
추가로 보강하는 단계다.