# GIT_WORKFLOW.md

작업 종류별 브랜치 생성 → 커밋 → 업로드 가이드

CONVENTION.md의 브랜치 전략(`main / develop / feature/* / fix/* / hotfix/*`)과
커밋 컨벤션(`type: subject`)을 실제로 어떻게 실행하는지 정리한 문서입니다.

---

## 0. 작업 시작 전 항상 먼저 할 것

```bash
git checkout develop
git pull origin develop
```

새 작업은 **항상 최신 develop에서 브랜치를 딴다.** 오래된 develop에서 작업하면 나중에 merge 충돌이 커집니다.

---

## 1. 작업 종류 판단표

| 하려는 작업 | 브랜치 종류 | 예시 브랜치명 | 커밋 type |
|---|---|---|---|
| TASK.md의 새 기능 구현 (Entity/Controller 등) | `feature/*` | `feature/admin-login`, `feature/p5-t1-program-domain` | `feat:` |
| 기존 코드 버그 수정 | `fix/*` | `fix/login-error` | `fix:` |
| 운영 중 긴급 장애 수정 | `hotfix/*` | `hotfix/security-patch` | `fix:` |
| docs/*.md 문서 수정 (오늘 한 작업) | `feature/*` 또는 `docs/*`(팀 합의 시) | `docs/task-review-fixes` | `docs:` |
| 동작 변경 없는 코드 구조 개선 | `feature/*` | `feature/service-refactor` | `refactor:` |
| 테스트 코드만 추가 | `feature/*` | `feature/program-service-test` | `test:` |
| 빌드/설정 파일 변경 | `feature/*` | `feature/gradle-config` | `chore:` |

> `docs/*`는 CONVENTION.md 브랜치 전략에 정식으로 포함되어 있습니다(코드 변경 없이 문서만 수정하는 작업 전용). 문서 전용 작업도 `feature/*`로 통일하고 싶다면 `feature/docs-task-review`처럼 써도 무방하지만, 기본값은 `docs/*`입니다.

---

## 2. 공통 작업 흐름 (모든 타입 동일)

```bash
# 1. develop 최신화
git checkout develop
git pull origin develop

# 2. 새 브랜치 생성
git checkout -b {type}/{작업명}

# 3. 작업 수행
# (코드/문서 수정)

# 4. 변경 확인 — 반드시 커밋 전에 확인하는 습관
git status
git diff

# 5. 원하는 파일만 정확히 add (git add . 은 실수 유발 주의)
git add {수정한 파일 경로}

# 6. 커밋 (아래 3번 표의 type 사용)
git commit -m "{type}: {한 줄 요약}"

# 7. 원격에 push
git push -u origin {type}/{작업명}

# 8. GitHub에서 PR 생성: base = develop, compare = 방금 브랜치
# 9. diff 확인 후 Merge
```

---

## 3. 커밋 메시지 type 참고 (CONVENTION.md 기준)

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (동작 변화 없음) |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅 (로직 변화 없음) |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 설정, 패키지 매니저 등 |

---

## 4. `git add .`을 피해야 하는 이유

오늘 실수처럼, `git checkout develop` 전에 남아있던 다른 작업 파일이나
IDE 임시 파일이 같이 커밋될 수 있습니다. 항상 이 순서를 권장합니다.

```bash
git status              # 뭐가 바뀌었는지 먼저 확인
git diff {파일명}        # 내용까지 확인하고 싶으면
git add {파일1} {파일2}  # 의도한 파일만 지정
git status               # add 후 다시 한번 확인
```

`git add .`이 안전한 경우는 **새 브랜치를 딴 직후, 아무 사전 작업 없이 바로 시작한 게 확실할 때**뿐입니다.

---

## 5. 실수했을 때 복구 명령 모음

### push 하기 전, 브랜치명/커밋 메시지를 잘못 지었을 때
```bash
git branch -m {틀린이름} {올바른이름}       # 브랜치명 수정
git commit --amend -m "{올바른 메시지}"     # 마지막 커밋 메시지 수정
```

### push 하기 전, 엉뚱한 파일이 커밋에 섞였을 때
```bash
git reset --soft HEAD~1     # 커밋만 취소, 변경 내용은 유지
git status                  # 다시 파일 선택해서 커밋
```

### 이미 push까지 했는데 브랜치를 잘못 땄을 때
```bash
git checkout develop
git checkout -b {올바른브랜치명}
git cherry-pick {잘못된 브랜치의 커밋 해시}   # 커밋만 옮겨오기
git push -u origin {올바른브랜치명}
# 잘못된 원격 브랜치는 GitHub에서 삭제
```

### 작업 중이던 파일을 다른 브랜치로 옮기고 싶을 때 (커밋 전)
```bash
git stash                       # 현재 변경사항 임시 보관
git checkout {옮길 브랜치}
git stash pop                   # 변경사항 복원
```

---

## 6. develop → main 승격 (기능 여러 개 쌓였을 때)

```bash
git checkout develop
git pull origin develop
```
GitHub에서 PR 생성: `base: main`, `compare: develop` → 리뷰 후 Merge.

배포 직전에만 올리는 걸 원칙으로 합니다(README.md/CONVENTION.md 원칙과 동일).

### 6-1. Release / tag 기반 운영 배포 (Phase 15 계약, P15-T0 확정)

운영 서버는 mutable 브랜치(`develop`/`main`)를 계속 pull하지 않고, **main에 붙인 release tag**만 checkout해서 배포합니다. 최초 운영 배포(launch) 흐름:

1. Phase 15 완료(TASK.md "Phase 15") → 전체 QA(JUnit / Node / Playwright / 로컬 HTTPS smoke)
2. release PR: `base: main`, `compare: develop` → 리뷰 후 **merge commit**으로 Merge(squash/rebase 금지 - develop 이력 보존)
3. main의 merge commit에 annotated tag 생성 후 push

   ```bash
   git checkout main
   git pull origin main
   git tag -a v1.0.0 -m "release: v1.0.0"
   git push origin v1.0.0
   ```

4. 운영 서버에서 해당 tag checkout(`git checkout v1.0.0`) → `.env`/TLS 구성 → `docker compose up -d --build`(Flyway는 app 기동 시 자동 적용) → smoke test

- "tag checkout → `docker compose up -d --build`"가 하나의 배포 단위입니다. checkout만 하고 rebuild하지 않거나, app image만 따로 rollback하지 않습니다(ARCHITECTURE.md "Nginx 정적 리소스 공급" 결정). rollback은 이전 tag checkout → 전체 `up -d --build`입니다.
- 버전 규칙: 기능 묶음 release는 `v1.1.0`처럼 minor, 긴급 수정은 `v1.0.1`처럼 patch를 올립니다.
- 서버 측 명령/순서의 상세 runbook은 `docs/OPERATIONS.md`(§6 최초 배포, §15 배포/rollback)를 따릅니다.

### 6-2. hotfix 기본 흐름

1. `main`(현재 운영 tag가 있는 브랜치)에서 `hotfix/{작업명}` 브랜치 생성
2. 수정 커밋(`fix:`) → PR `base: main` → merge commit으로 Merge
3. main에 patch tag(예: `v1.0.1`) 생성/push → 운영 서버에서 해당 tag로 배포
4. 같은 수정을 develop에도 반영: PR `base: develop`, `compare: main`(또는 hotfix 브랜치) → Merge. develop과 main이 어긋난 채로 두지 않습니다.

---

## 7. 빠른 체크리스트 (매 작업마다)

- [ ] `develop`에서 최신 pull 후 브랜치 땄는가
- [ ] 브랜치명이 작업 성격과 맞는가 (`feature/`, `fix/`, `hotfix/`, `docs/`)
- [ ] `git status`로 add 전에 변경 파일 확인했는가
- [ ] 커밋 메시지 type이 CONVENTION.md 표와 맞는가
- [ ] PR의 base가 `develop`인가 (`main`으로 바로 올리지 않았는가 - release PR/hotfix는 §6 예외)
- [ ] 병합 후 로컬 develop을 다시 pull 받았는가