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

| AI | 역할 |
|------|------|
| ChatGPT | 요구사항 분석, 설계, 코드 리뷰 |
| Codex | 기능 구현 |
| Claude Code | 리팩토링, 대규모 수정, 프로젝트 분석 |

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

기능 하나를 개발할 때의 순서

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