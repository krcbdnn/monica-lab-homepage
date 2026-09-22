# ARCHITECTURE.md

# System Architecture

Version 2.0

---

# Architecture

Spring Boot 기반 MVC + Layered Architecture를 사용한다.

```
Client
    │
    ▼
Controller
    │
    ▼
Service
    │
    ▼
Repository
    │
    ▼
MariaDB
```

모든 비즈니스 로직은 Service 계층에서 처리한다.

---

# Package Structure

```
src/main/java
└── com.monicalab
    │
    ├── config
    ├── security
    ├── common
    │   ├── config
    │   ├── dto
    │   ├── entity
    │   ├── exception
    │   ├── response
    │   └── util
    │
    ├── admin
    │
    ├── page
    │
    ├── program
    │
    ├── board
    │
    ├── banner
    │
    ├── popup
    │
    ├── file
    │
    ├── menu
    │
    ├── pinned
    │
    └── home
```

패키지 설명

- `config` : 애플리케이션 전역 설정(WebConfig, SecurityConfig 등 프로젝트 전체에 적용되는 구성)
- `security` : Spring Security 인증/인가 관련 클래스(로그인 처리, 접근 제어 등)
- `common` : 여러 도메인에서 공통으로 사용하는 클래스 모음
  - `common.config` : common 패키지 내부에서 사용하는 설정(QueryDSL 설정, 공통 리소스 설정 등). 최상위 `config`와 달리 common 모듈 범위에 한정된 설정을 담당한다.
  - `dto` : 공통으로 사용하는 Request/Response DTO
  - `entity` : BaseEntity 등 공통 Entity
  - `exception` : CustomException, ErrorCode 등 예외 관련 클래스
  - `response` : ApiResponse 등 공통 응답 포맷
  - `util` : FileUtil, DateUtil 등 공통 유틸리티
- `admin`, `page`, `program`, `board`, `banner`, `popup`, `file`, `menu`, `pinned` : 도메인별 패키지. 각 패키지는 Controller, Service, Repository로 구성되는 Layered Architecture를 따른다.
- `home` : 공개 메인 화면(`GET /`) 전용 패키지. 자체 Entity/Repository 없이 Page, Program, Board, Banner, Popup Service를 조합하여 메인 화면 데이터를 구성하는 Controller만 포함한다. `pinned`가 별도 최상위 패키지인 이유가 바로 이 제약이다 - HomePinnedContent는 Entity/Repository를 가지므로 `home` 아래에 둘 수 없다.

---

# Domain

## Admin 화면(View) / API 컨트롤러 명명 규칙

`/admin/**`(Thymeleaf 화면)과 `/api/admin/**`(REST API)는 인증 대상은 같지만 응답 형식이 다르므로(HTML vs JSON), 도메인마다 **두 개의 컨트롤러로 분리**한다. 하나의 컨트롤러가 화면 렌더링과 JSON 응답을 함께 처리하지 않는다.

| 구분 | 네이밍 | 책임 | 예시 |
|---|---|---|---|
| API | `Admin{Domain}Controller` | `@RestController`, `/api/admin/{domain}` 하위 CRUD/상태변경 API, `ApiResponse` 반환 | `AdminProgramController` |
| View | `Admin{Domain}ViewController` | `@Controller`, `/admin/{domain}` 하위 화면(목록/등록/수정 폼) 렌더링, Thymeleaf View 이름 반환. 데이터는 자체 조회하지 않고 화면 진입만 담당하며, 실제 데이터는 화면의 JS가 P3-T5 공통 fetch 유틸로 `Admin{Domain}Controller`의 API를 호출해 채운다 | `AdminProgramViewController` |

이 규칙은 Program, Board, Page, Banner, Popup, File, Menu, Admin(Dashboard 포함) 전 도메인에 동일하게 적용한다. 공개 영역의 `{Domain}Controller`(API) / `{Domain}ViewController`(View) 분리(Page/Program/Board)와 동일한 패턴이다.

## Admin

관리자 로그인 및 대시보드

```
AdminController             (GET /api/admin/me — 로그인한 관리자 본인 정보 조회 전용. 타 관리자 계정 조회/등록/수정 API는 없음, API.md 기준)
AdminAuthController         (POST /api/admin/login, POST /api/admin/logout)
AdminViewController         (GET /admin/login, GET /admin/dashboard 등 관리자 공통/대시보드 화면 렌더링, Thymeleaf)
DashboardController         (GET /api/admin/dashboard, 위 명명 규칙의 API 컨트롤러 역할)

AdminService

AdminRepository
```

기능

- 로그인
- 로그아웃
- 대시보드

비고: Admin 도메인은 로그인 화면과 대시보드 화면을 함께 다루므로 `AdminViewController` 하나가 두 화면(`/admin/login`, `/admin/dashboard`)을 모두 렌더링한다(로그인은 `AdminAuthController`가, 대시보드 데이터는 `DashboardController`가 API로 제공).

---

## Page

CMS 페이지 관리

관리 페이지

- 인사말
- 연구소 소개
- 연혁
- 오시는 길

```
PageController
AdminPageController
AdminPageViewController     (GET /admin/pages 목록/수정 화면 렌더링, Thymeleaf)

PageService

PageRepository

CmsPage                     (Entity. 클래스명은 Page가 아닌 CmsPage 사용 — ERD.md "Entity 클래스명 주의" 참고)
```

pageType

```
GREETING

INTRODUCTION

HISTORY

LOCATION
```

---

## Program

수강 프로그램과 특강을 하나의 도메인으로 관리한다.

```
ProgramController

AdminProgramController
AdminProgramViewController  (GET /admin/programs 목록/등록/수정 화면 렌더링, Thymeleaf)

ProgramService

ProgramRepository
```

programType

```
COURSE

SPECIAL
```

관리 항목

- 제목
- 내용
- 썸네일
- 첨부파일
- Google Form URL
- 모집 상태
- 공개 여부

검색: `GET /api/programs`의 `keyword` 파라미터(API.md 기준)는 제목/내용을 대상으로 QueryDSL 동적 조건으로 검색하며, `programType`과 동시 조합 가능하다(Board의 검색 방식과 동일한 패턴).

---

## Board

공지사항

갤러리

자료실

모두 하나의 Board 도메인으로 관리한다.

```
BoardController

AdminBoardController
AdminBoardViewController    (GET /admin/boards 목록/등록/수정 화면 렌더링, Thymeleaf)

BoardService

BoardRepository
```

boardType

```
NOTICE

GALLERY

ARCHIVE
```

관리 항목

- 제목
- 내용
- 첨부파일
- 썸네일
- 공개 여부

---

## Banner

```
BannerController

AdminBannerController
AdminBannerViewController   (GET /admin/banners 목록/등록/수정 화면 렌더링, Thymeleaf)

BannerService

BannerRepository
```

기능

- 등록
- 수정
- 삭제
- 노출 여부
- 정렬 순서

---

## Popup

```
PopupController

AdminPopupController
AdminPopupViewController    (GET /admin/popups 목록/등록/수정 화면 렌더링, Thymeleaf)

PopupService

PopupRepository
```

기능

- 등록
- 수정
- 삭제
- 노출 여부
- 시작일
- 종료일

---

## Home

공개 메인 화면(`GET /`)

```
HomeController
```

기능

- 메인 배너 조회: `HomeController`는 `BannerService.getPublicList()`가 반환하는 `isVisible=true` 배너 전체(`sortOrder ASC, createdAt DESC`)를 자르지 않고 `banners` 모델 속성으로 그대로 전달한다. 슬라이드 표시 상태(현재 슬라이드, 자동 전환, 일시정지/재생, 키보드 조작, ARIA 등 접근성 처리)는 서버가 관여하지 않고 프론트 `static/js/home/hero-carousel.js`가 전담한다. 캐러셀 동작의 상세 계약은 `docs/TASK.md` P13-T8/P13-T9를 따른다.
- 팝업 조회: `HomeController`는 `PopupService.getPublicList()`가 반환한 공개 노출 대상(`isVisible=true`이며 노출기간 내) Popup 전체를 `popups` 모델 속성으로 그대로 전달한다. 서버는 1건으로 자르거나 순차 표시 상태를 관리하지 않는다. 실제 표시 순서, 닫기, 오늘 하루 보지 않기 등 UI 상태는 서버가 관여하지 않고 프론트 `static/js/home/popup-modal.js`가 전담한다. 상세 계약은 `docs/TASK.md` P13-T10/P13-T11을 따른다.
- 최신 프로그램 카드 조회: `ProgramService.getPublicList(null, null, createdAt desc pageable)` 결과 중 최신 3건을 모델에 제공한다. `recruitStatus` 기준 서버 측 필터링은 하지 않으며, 응답에 포함된 `recruitStatus` 값으로 화면에서 상태 배지만 표시한다. 0건이어도 화면은 완성된 레이아웃의 empty state로 렌더링한다.
- 최신 강의 후기 카드 조회(P13-T16): `#latest-programs` 바로 다음에 위치한다. `BoardService.getPublicList(BoardType.REVIEW, null, createdAt desc pageable)` 결과 중 최신 `LATEST_REVIEW_LIMIT`(3)건을 `latestReviews` 모델 속성으로 제공한다. `LATEST_BOARD_LIMIT`(공지/갤러리, 5)과는 별도 상수를 쓴다. 별도 Entity/API를 만들지 않고 기존 `Board`(`boardType=REVIEW`)를 재사용하며, 마크업은 `#latest-gallery`의 `.gallery-grid`/`.gallery-card__*`를 그대로 재사용한다.
- 최신 공지/갤러리 조회
- 메인 고정 콘텐츠 조회(P13-T38B): `HomePinnedContentService.getPublicList()`가 반환하는 목록을 `pinnedContents` 모델 속성으로 그대로 전달한다. Hero(`#banners`)/Popup(`#popups`) 바로 다음, `#latest-programs` 이전 위치에 `#home-pinned`(제목 "주요 소식")로 렌더링하며, 목록이 비어 있으면(`pinnedContents == null` 방어 포함) 섹션 자체를 렌더링하지 않는다(다른 섹션과 달리 empty state 문구 없음). 상세 계약은 `## HomePinnedContent` 섹션을 참고.
- 바로가기 메뉴(P13-T17, P13-T30B에서 동적화): 기존 공개 화면으로 이동하는 링크(`연구소 소개` → `/pages/INTRODUCTION`, `프로그램` → `/programs`, `강의 후기` → `/boards?boardType=REVIEW`, `게시판` → `/boards`)로 구성되며, P13-T17 시점에는 4개 고정 하드코딩이었으나 P13-T30B부터 `Menu` 도메인(P13-T30A) 기반 동적 렌더링으로 전환됐다. 실제 렌더링 경로는 `## Menu` 섹션의 "공개 헤더 렌더링"을 참고. 홈 상단 인사말(GREETING) 요약 섹션과 하단 "프로그램 바로가기" CTA 섹션은 P13-T17에서 제거됐다. `/pages/GREETING` 상세 페이지 자체(`PageController`/`PageViewController`)는 유지된다.

자체 Entity/Repository 없이 Banner, Popup, Board, Program, Page, `HomePinnedContent`(P13-T38B부터, `com.monicalab.pinned.service.HomePinnedContentService`) Service를 조합하여 사용한다.

---

## File

```
FileController        (공개: GET /api/files/{id} 다운로드)

AdminFileController    (관리자: GET /api/admin/files, POST /api/admin/files, DELETE /api/admin/files/{id})
AdminFileViewController (GET /admin/files 업로드 이력 목록 화면 렌더링, Thymeleaf)

FileService

FileRepository

UploadFile              (Entity. 클래스명은 File이 아닌 UploadFile 사용 — ERD.md "Entity 클래스명 주의" 참고)
```

기능

- 업로드 이력 목록 조회(페이징/정렬, 비공개 개념 없이 전체 UploadFile)
- 이미지 업로드
- 첨부파일 업로드
- 삭제

저장소

- Local Storage
- AWS S3(확장)

---

## Menu

공개 헤더 내비게이션을 관리자가 CRUD할 수 있게 하는 도메인이다(P13-T30A). 다른 Entity와 동일하게 `parentId`는 FK 없는 plain 컬럼이며(ERD.md no-FK 원칙), `targetType=GROUP`인 메뉴만 부모가 될 수 있어 최대 2-depth를 보장한다.

```
AdminMenuController
AdminMenuViewController     (GET /admin/menus 목록/등록/수정 화면 렌더링, Thymeleaf)
HeaderMenuControllerAdvice  (P13-T30B, 공개 View Controller에 공통 header menu model을 공급)

MenuService

MenuRepository

Menu

MenuTargetType

HeaderMenuItem              (P13-T30B, 공개 헤더 전용 View 모델)
```

targetType

```
GROUP
HOME
PAGE
PROGRAM_LIST
BOARD_LIST
INTERNAL_URL
EXTERNAL_URL
```

기능

- 등록
- 수정
- 삭제(자식이 있으면 `MENU_HAS_CHILDREN` 409로 금지)
- 노출 여부
- 정렬 순서(Banner와 동일한 숫자 입력 + "순서 변경" 버튼 방식, 드래그앤드롭 없음)

비고: P13-T30A는 Menu 도메인과 관리자 CRUD, 초기 시드 데이터만 구축했다(공개 헤더 동적 렌더링은 그 시점에 존재하지 않았음).

### 공개 헤더 렌더링(P13-T30B)

공개 `GET /api/menus` REST API는 두지 않는다. 대신 `HeaderMenuControllerAdvice`(`@ControllerAdvice(assignableTypes = {HomeController.class, PageViewController.class, ProgramViewController.class, BoardViewController.class})`)가 `home/layout/default`를 사용하는 공개 View Controller에만 `headerMenuItems`(`List<HeaderMenuItem>`) model attribute를 공급하고, `home/layout/header.html`이 이를 렌더링한다. 관리자/API Controller에는 이 model attribute가 주입되지 않고 Menu 조회도 발생하지 않는다.

- `MenuService.getPublicMenuTree()`가 요청당 `MenuRepository.findAll()` 1회로 전체 Menu를 조회한 뒤 애플리케이션에서 트리를 구성한다(N+1 없음, 캐시 없음 - 관리자 변경이 다음 공개 요청에 즉시 반영됨).
- `visible=true`인 최상위 항목만 후보이며, `visible=false`인 GROUP의 자식은 자식의 visible 여부와 무관하게 전부 제외된다. GROUP의 자식 중 `visible=false`인 것도 제외되고, 남은 visible 자식이 하나도 없는 GROUP은 GROUP 자체도 숨긴다.
- fail-closed: orphan(부모가 없거나 GROUP이 아닌 parentId를 가리키는) 행, GROUP의 자식인데 그 자신도 `targetType=GROUP`인 비정상 행(정상 CRUD로는 생성 불가)은 트리 구성 알고리즘상 자연히 순회되지 않아 공개 화면에 노출되지 않는다. 별도 복구/자동수정 로직은 두지 않는다.
- `HeaderMenuItem`(`id`, `label`, `href`, `openInNewTab`, `children`)은 Entity/`MenuTargetType`을 Thymeleaf에 노출하지 않기 위한 공개 전용 View 모델이며, `href`가 `null`인 항목만 GROUP(submenu trigger)이라는 사실만으로 템플릿이 분기한다. `targetType`별 href 계산(`GROUP→null`, `HOME→/`, `PAGE→/pages/{targetValue}`, `PROGRAM_LIST→targetValue 없으면 /programs, 있으면 /programs?programType={value}`, `BOARD_LIST→targetValue 없으면 /boards, 있으면 /boards?boardType={value}`, `INTERNAL_URL`/`EXTERNAL_URL→targetValue 그대로`)는 `MenuService`가 전담하고 Thymeleaf는 `targetType` 분기를 하지 않는다.
- 헤더 마크업: 일반 메뉴는 `<a>`, GROUP은 `<a href="#">`가 아닌 `<button type="button" aria-expanded aria-controls>`로 렌더링한다. `openInNewTab=true`인 링크는 `target="_blank" rel="noopener noreferrer"`를 함께 렌더링한다(GROUP에는 적용되지 않음). `static/js/home/nav-submenu.js`(신규, `nav-toggle.js`와 별도 파일)가 desktop hover/click, mobile accordion, Escape, outside-click을 담당하며 실제 open 상태와 `aria-expanded`가 항상 일치하도록 `is-open` 클래스를 단일 source of truth로 사용한다(순수 함수 `resolveOpenGroupId`만 Node 테스트 대상; 키보드 포커스는 네이티브 Tab 이동 + `<button>`의 기본 Enter/Space 클릭 동작만으로 지원하며, focus 자체가 자동으로 여닫는 별도 로직은 두지 않는다).
- Footer(`home/layout/footer.html`)는 P13-T30B에서도 동적화하지 않고 기존 4개 하드코딩 링크를 유지한다.

### 최종 IA + 전체메뉴(P13-T30C)

Menu 테이블은 `HOME`/`ABOUT`/`OUR PROGRAMS`/`Blog` 같은 미확정 가안 대신, PRD.md/FEATURES.md에 실제 문서화된 콘텐츠만으로 구성한 최종 IA를 담는다: `연구소 소개`(GROUP: 인사말/연구소 소개/연혁/오시는 길), `프로그램`(GROUP: 수강 프로그램/특강), `게시판`(GROUP: 공지사항/갤러리/자료실/강의 후기) — 3 GROUP + 10 child = 13행(`V5__update_menu_ia.sql`). "Blog"는 어느 문서에도 정의된 기능이 아니어서 제외했다.

- `HOME`과 `전체메뉴`(mega menu trigger)는 Menu DB row가 아니라 `home/layout/header.html`의 정적 마크업이다. `HOME`은 항상 `#quick-menu`의 첫 항목으로 `/`로 이동하는 `<a>`이고, `전체메뉴`는 `headerMenuItems`가 비어 있지 않을 때만 렌더링되는 mega menu trigger다.
- 전체메뉴는 개별 GROUP dropdown과 **동일한 `headerMenuItems`를 템플릿에서 한 번 더 순회**해 렌더링한다(신규 Java 조회 없음). 최상위 항목이 GROUP이면 헤딩+children 목록, LEAF(관리자가 향후 최상위에 PAGE/PROGRAM_LIST/BOARD_LIST 등을 직접 추가하는 경우)면 헤딩 자체가 클릭 가능한 링크가 되도록 개별 dropdown과 동일한 `item.href == null` 분기를 재사용한다 - 관리자가 GROUP을 LEAF로 바꾸거나 그 반대로 바꿔도, 혹은 visible/openInNewTab을 바꿔도 개별 dropdown과 전체메뉴가 항상 같은 데이터를 보므로 두 UI가 어긋날 수 없다.
- `static/js/home/nav-submenu.js`는 무수정이다. `.site-nav__item.has-submenu`를 문서 전체에서 범위 제한 없이 스캔하므로 전체메뉴 트리거도 기존 GROUP dropdown과 완전히 동일한 단일 상태 머신(하나만 열림, 다른 것을 열면 자동으로 닫힘, Escape/outside-click 공통 처리)에 자동으로 편입된다.
- `home.css`의 `.site-nav__megamenu`는 헤더의 가장 오른쪽 항목이라는 특성상 기존 `.site-nav__submenu`의 `left:0`(오른쪽으로 확장) 대신 `right:0; left:auto`(왼쪽으로 확장)를 사용해 1024/1440px에서 뷰포트 밖으로 나가지 않도록 한다. 모바일에서는 hamburger accordion이 이미 동일한 정보를 전부 보여주므로 `[data-menu-id="all"]`을 `display:none`으로 숨겨 중복 노출을 피한다.
- **향후 원칙**: V5는 Menu 도메인이 아직 어떤 프로덕션 추적 브랜치에도 배포되지 않아 관리자 커스터마이징이 존재할 수 없었던 시점의 1회성 IA 교체다. 이 시점 이후로는 관리자가 CRUD로 수정한 메뉴 데이터를 향후 migration이 DELETE 후 재생성(destructive reset)하는 방식으로 다루지 않는다.

### IA 갱신 이력: P13-T33 → P14-T2A

위 최종 IA는 V8 시점(`연구소 소개`/`수강 신청`/`강의 후기` GROUP 3개, `강의 후기` GROUP은 수강 후기/특강 후기 child 2개) 기준 서술이다. 이후 두 차례 발주처 요구사항 변경을 거쳐 실제 최신 구조는 다음과 같다. **과거 결정은 삭제하지 않고 이력으로 남긴다** - 각 시점의 결정은 그 시점 요구사항 기준으로 올바른 작업이었다.

- **P13-T33(V10)**: 발주처 요구사항 재확인 결과 "강의 후기는 하나의 게시판이며 공개 navigation에는 top-level LEAF 하나만 존재해야 한다"는 것이 확인되어, V8이 만든 "강의 후기" GROUP(수강 후기/특강 후기 child)을 단일 top-level LEAF(`BOARD_LIST`/`REVIEW`, `target_subvalue` 없음)로 되돌렸다.
- **P14-T2A(V12)**: 발주처 요구사항이 다시 최신으로 변경되어, V10의 결정을 다시 GROUP 구조로 전환했다. 최종(V12 이후) 공개 IA는 다음과 같다.

  ```
  HOME (정적)
  연구소 소개 (GROUP) - 인사말(비노출)/연구소 소개/연혁/오시는 길   ← 무변경
  수강 신청 (GROUP)   - 수강 신청/특강 신청                        ← 무변경(label 유지)
  소식·자료 (GROUP)   - 공지사항/갤러리/자료실                     ← 신규 GROUP, 기존 top-level LEAF 3개를 자식으로 이동
  강의 후기 (GROUP)   - 전체/수강 후기/특강 후기                   ← 기존 top-level LEAF를 GROUP으로 재전환(같은 id 재사용) + 자식 3개 신규
  전체메뉴 (정적)
  ```

  - "소식·자료"에는 "전체" 항목을 두지 않는다(공지사항/갤러리/자료실 3개만).
  - "강의 후기 > 전체"의 href는 `/boards?boardType=REVIEW`(programType 없음)로, 기존 REVIEW+programType 없음(미분류) 후기를 포함하는 semantics를 그대로 유지한다. "수강 후기"/"특강 후기"만 모으는 새 query는 만들지 않았다.
  - Menu row 개수는 12 → 16(신규 4행: 소식·자료 GROUP + 강의 후기 자식 3개). `MenuService`/`HeaderActiveResolver`/`HeaderMenuControllerAdvice` 등 Java main code는 무변경 - 기존 범용 GROUP/BOARD_LIST 매칭 로직이 그대로 새 구조를 처리한다.
  - 공개 `home/board/list.html`은 Controller가 이미 넘기는 `boardType`/`programType` model 속성만으로 title/filter-nav를 legacy(`/boards`, 7개 필터)/소식·자료(3개)/강의 후기(3개) 세 context로 Thymeleaf 조건 분기한다(새 route/Controller 없음). 상세는 `docs/TASK.md`의 P14-T2A 항목을 참고한다.
  - V12도 V8/V9와 동일하게 DELETE를 쓰지 않는다(V10은 예외적으로 정밀 DELETE 2건을 사용했으나 V12는 다시 순수 guarded INSERT/UPDATE 원칙으로 돌아간다).

---

## HomePinnedContent

관리자가 기존 Board/Program 콘텐츠 중에서 선택해 공개 메인 화면 상단에 고정 노출하기 위한 참조 전용 도메인이다(P13-T38A, "인스타그램 고정 게시물"과 유사한 개념). `home` 패키지는 자체 Entity를 가질 수 없으므로(위 패키지 설명 참고) Menu와 동일하게 완전히 독립된 최상위 패키지 `com.monicalab.pinned`로 둔다.

```
AdminHomePinnedContentController
AdminHomePinnedContentViewController  (GET /admin/home-pinned-contents 목록 화면 렌더링, Thymeleaf. 별도 등록/수정 폼 View는 없음 - 추가/순서변경/노출변경/해제 전부 목록 화면에서 처리)

HomePinnedContentService

HomePinnedContentRepository

HomePinnedContent

HomeTargetType
```

targetType

```
BOARD
PROGRAM
```

Page/Popup/Banner는 대상에 포함하지 않는다(구조상 썸네일/공개여부/상세URL이 없거나, 이미 메인 자체를 구성하는 콘텐츠라 "고정 대상 게시글"이라는 개념과 맞지 않음). 신규 목적지가 필요해지면 `HomeTargetType`에 값만 추가하는 형태로 확장한다(FK 없는 polymorphic reference라 Board/Program Entity 변경 없이 확장 가능).

기능

- 기존 Board/Program 검색 후 고정 추가(신규 검색 API 없음 - 기존 `GET /api/admin/boards`, `GET /api/admin/programs` 재사용). 존재하지 않거나 비공개인 대상은 거부한다.
- 고정 목록 조회, 정렬 순서 변경(Menu/Banner와 동일한 숫자 입력 + "순서 변경" 버튼 방식, 드래그앤드롭 없음), 노출 여부 변경, 고정 해제
- 개수 제한 없음
- 중복 방지: `(targetType, targetId)` 서비스 사전 검증 + DB `UNIQUE` 제약 2단계

원본 상태 처리: 고정 이후 원본(Board/Program)이 비공개로 전환되거나 삭제되어도 `HomePinnedContent` 행은 자동 삭제하지 않는다. 관리자 목록 조회 시점에 `BoardRepository.findAllById`/`ProgramRepository.findAllById`로 대상을 배치 재조회(핀 개수와 무관하게 타입당 쿼리 1회, N+1 없음)해 `PUBLIC`(원본 존재+공개)/`PRIVATE`(원본 존재+비공개)/`DELETED`(원본 없음) 상태를 애플리케이션 레벨에서 계산한다 - 이 상태는 DB persisted 컬럼이 아니라 `HomePinnedContentResponse` 조립 시점의 계산값이다.

공개 노출(P13-T38B): `HomePinnedContentService.getPublicList()`가 `isVisible=true`인 pin만 후보로 삼아 `HomePinnedContentRepository.findByIsVisibleTrue(Sort)`로 1회 조회하고, `getAdminList()`와 동일하게 `targetType`별로 그룹핑해 `BoardRepository.findAllByIdInAndIsPublicTrue`/`ProgramRepository.findAllByIdInAndIsPublicTrue`(관리자용 `findAllById`와 달리 `isPublic=true` 조건을 predicate에 포함하는 신규 배치 메서드)로 각각 최대 1회 배치 조회한다(핀 개수와 무관하게 최대 3 query, N+1 없음). 배치 조회 결과에 없는(원본이 비공개이거나 물리 삭제된) pin은 예외를 던지지 않고 조용히 제외한다. 응답은 관리자용 `HomePinnedContentResponse`가 아니라 공개 전용 `HomePinnedContentPublicResponse`(`pinnedId`/`targetType`/`targetId`/`title`/`thumbnail`/`href`)를 사용하며, `href`는 서비스가 `/boards/{id}` 또는 `/programs/{id}`로 미리 조립한다. `HomeController`는 이 결과를 `pinnedContents` model attribute로 전달하기만 하는 얇은 책임을 유지하고, 공개 JSON API는 두지 않는다.

비고: P13-T38A는 이 도메인과 관리자 CRUD까지 구축했고, 공개 메인 화면(`HomeController`/`home/index.html`)에서의 실제 렌더링은 P13-T38B에서 구현했다.

---

# CKEditor5

모든 콘텐츠는 CKEditor5를 이용하여 수정한다.

적용 대상

- 기관소개
- 프로그램
- 게시판
- 팝업

기능

- 텍스트
- 이미지
- 표
- 링크
- 파일 첨부

이미지 업로드

```
POST /api/admin/files
```

이미지 URL을 반환하여 CKEditor에 삽입한다.

## CKEditor 구성 및 build 정책

- CDN 사전빌드(`https://cdn.ckeditor.com/ckeditor5/41.4.2/classic/ckeditor.js`, classic build)를 그대로 사용하며, npm 패키지/번들러는 도입하지 않는다. 이 build에는 `FontFamily`/`FontSize`/`FontColor`/`FontBackgroundColor`/문단 `Alignment`/`ImageResize`/`LinkImage` 플러그인이 포함되어 있지 않다(실제 CDN 번들을 헤드리스로 직접 실행해 확인, 문자열 grep이 아닌 `editor.plugins.has()`/`editor.commands` 기준). `ImageStyle`/`ImageToolbar`는 포함되어 있고, `image.styles.options`에 `inline`/`alignLeft`/`alignRight`/`alignCenter`/`alignBlockLeft`/`alignBlockRight`/`block`/`side`가 이미 등록되어 있어, 이 중 toolbar에 노출할 항목은 새 plugin 없이 `image.toolbar` config만으로 선택할 수 있다.
- `static/js/admin/ckeditor-config.js`가 Board/Program/Page/Popup 4개 관리자 폼이 공유하는 `EDITOR_CONFIG`를 제공하며, `ClassicEditor.create(el, AdminCkeditorConfig.EDITOR_CONFIG)`로 전달한다. 업로드 어댑터(`ckeditor-upload-adapter.js`)는 이 config와 독립적으로 `FileRepository.createUploadAdapter`를 교체하는 방식을 그대로 유지한다.

## XSS 방지 정책

CKEditor5로 작성된 콘텐츠는 HTML 형태로 저장되며, Thymeleaf에서 `th:utext`로 그대로 출력되므로 저장형 XSS(Stored XSS)에 노출될 수 있다. 다음 정책을 따른다.

- 서버 저장 시점에 HTML 화이트리스트 정제(sanitize)를 수행한다(예: OWASP Java HTML Sanitizer 또는 jsoup의 `Safelist` 사용).
- 허용 태그: 텍스트 서식(`p`, `br`, `strong`, `em`, `u`, `h1~h6`), 표(`table`, `tr`, `td`, `th`), 링크(`a[href]`), 이미지(`img[src]`), 이미지 wrapper(`figure[class]`) 등 CKEditor5 기본 툴바가 생성하는 태그로 한정한다.
- `figure`의 `class` 속성은 값 전체를 정규식으로 검증하지 않고, whitespace로 분리한 토큰 단위로 화이트리스트(`image`, `image-style-side`, `image-style-align-left`, `image-style-align-right`, `image-style-align-center`, `image_resized`)와 대조해 안전한 토큰만 남긴다. `img`에는 class를 허용하지 않는다. `img`의 `width`/`height`, font 관련 속성·값은 허용하지 않는다.
- `figure`의 `style` 속성은 Safelist 자체에는 추가하지 않는다(`style` 전체를 허용하지 않는 원칙 유지). 대신 이미지 크기 조절(P13-T40) 값만 별도의 "extract(clean 이전 원본에서 검증) → clean → reinject(검증된 값만 재적용)" 패턴으로 좁게 허용한다: `style`이 정확히 `width: 25%;`/`50%;`/`75%;`(공백·세미콜론 변형만 허용) 전체 일치일 때만 그 값을 clean 이후 다시 그려 넣고, 그 외 값이나 다른 property가 하나라도 섞이면 `style` 전체를 폐기한다. 향후 확장 시에도 이 좁은 값 단위 화이트리스트만 확장하고 `style` 속성을 Safelist에 일반 허용하지 않는 것을 원칙으로 한다.
- `script`, `iframe`, `on*` 이벤트 속성, `javascript:` 스킴 링크는 모두 제거한다.
- 정제는 `PageService`, `ProgramService`, `BoardService`, `PopupService`의 등록/수정 로직에서 공통 유틸(`common/util/HtmlSanitizer.java`)을 통해 일괄 적용한다.

## 콘텐츠 링크 처리 정책

CKEditor5 본문에 삽입된 `<a href>` 링크는 외부/내부 여부에 따라 다르게 열려야 한다(외부는 새 탭, 내부는 같은 탭). 다음 정책을 따른다.

- 링크 판별 기준: `href`가 `http://`, `https://`, `//`로 시작하면 외부 링크, 그 외(상대 경로, `mailto:`, `#anchor` 등)는 내부/비외부 링크로 간주한다.
- 판별 및 속성 부여는 저장 시점이 아닌 공개 화면 렌더링 시점에 수행한다(공통 유틸 `common/util/ContentLinkRenderer.java`). 이미 저장된 기존 콘텐츠도 별도 마이그레이션이나 재저장 없이 자동 적용된다.
- 외부 링크에는 `target="_blank" rel="noopener noreferrer"`를 부여하고, 내부 링크는 변경하지 않는다.
- `Board`, `Program`, `Page` 상세 화면과 `Popup` 노출 화면, 총 4곳의 공개 뷰가 대상이다. 각 공개 `*ViewController`(`HomeController` 포함)가 `ContentLinkRenderer`로 미리 변환한 HTML을 view 전용 model attribute(`renderedContent`, Popup은 `popupRenderedContents` Map)로 전달하고, 템플릿은 `th:utext`로 이 값만 출력한다.
- DB에 저장되는 `content`, 관리자 CMS 응답(API Response DTO), CKEditor 재편집 시 로드되는 원본 데이터에는 이 변환을 적용하지 않는다. 즉 `HtmlSanitizer`가 담당하는 저장 시점 XSS 방지 화이트리스트와는 별개의, 표시 전용(read-time) 후처리다.

### 새 탭 링크 공통 규칙

CKEditor 본문 링크뿐 아니라, 첨부파일 다운로드·외부 신청 링크·관리자 파일 목록 등 직접 마크업/JS로 생성하는 새 탭 링크에도 동일하게 적용한다.

- `target="_blank"`를 사용하는 모든 링크는 `rel="noopener noreferrer"`를 항상 함께 부여한다(`window.opener`를 통한 tabnabbing 방지).

---

# Common

공통 클래스

```
BaseEntity

ApiResponse

ErrorCode

CustomException

GlobalExceptionHandler

FileUtil

DateUtil
```

BaseEntity

```
createdAt

updatedAt
```

모든 Entity 공통 사용

---

# ApiResponse 스키마

모든 API 응답(성공/실패)은 아래 포맷을 따른다. `ApiResponse.success(data)` / `ApiResponse.fail(errorCode)` 호출 시 이 구조로 직렬화되어야 한다.

## 성공 응답

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

- `data` : 실제 응답 데이터. 객체, 배열, `null`(204 등) 모두 가능하다.

예)

```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "공지사항 제목"
  },
  "error": null
}
```

## 실패 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD_NOT_FOUND",
    "message": "게시글을 찾을 수 없습니다."
  }
}
```

- `error.code` : CODING_RULES.md "ErrorCode 카탈로그"의 Enum 이름을 그대로 사용한다(예: `PROGRAM_NOT_FOUND`, `INVALID_INPUT_VALUE`).
- `error.message` : 사용자에게 노출 가능한 한글 메시지. `ErrorCode`마다 고정 메시지를 갖는다.
- HTTP 상태 코드는 CODING_RULES.md ErrorCode 카탈로그의 매핑을 따르며, 응답 바디의 `error.code`와 항상 1:1로 대응한다.

Validation 실패(`INVALID_INPUT_VALUE`)처럼 필드 단위 상세가 필요한 경우 `error`에 `fields` 배열을 추가한다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_INPUT_VALUE",
    "message": "입력값이 올바르지 않습니다.",
    "fields": [
      { "field": "title", "reason": "제목은 필수입니다." }
    ]
  }
}
```

---

# Pagination 응답 구조

목록 조회 API(Program, Board 등 `page`, `size` 쿼리 파라미터를 받는 API)는 `ApiResponse.data`에 아래 구조를 담는다.

```json
{
  "success": true,
  "data": {
    "content": [ ],
    "page": 0,
    "size": 10,
    "totalElements": 42,
    "totalPages": 5,
    "last": false
  },
  "error": null
}
```

- `content` : 조회된 항목 배열
- `page` : 현재 페이지(0부터 시작)
- `size` : 페이지당 항목 수
- `totalElements` : 전체 항목 수
- `totalPages` : 전체 페이지 수
- `last` : 마지막 페이지 여부

Spring Data `Page<T>`를 직접 반환하지 않고, 위 필드로 구성된 별도 Response DTO(`PageResponse<T>` 등 공통 DTO, `common/dto`)로 변환하여 반환한다.

---

# Security

Spring Security 사용

관리자 인증이 필요한 대상은 화면 접근과 API 호출로 구분된다.

- `/admin/**` : 관리자 화면(Thymeleaf) 접근 경로
- `/api/admin/**` : 관리자 REST API 호출 경로(API.md 기준)

두 경로 모두 동일한 세션 기반 인증(ROLE_ADMIN)을 사용하며, Spring Security가 두 경로를 함께 인증 대상으로 처리한다.

인증 대상

```
/admin/**
/api/admin/**
```

비로그인 접근

```
/

/pages/**

/programs/**

/boards/**

/api/files/{id}
```

`/admin/login`(GET, 로그인 화면)과 `POST /api/admin/login`은 `/admin/**`, `/api/admin/**` 인증 대상의 예외로 permitAll 처리한다. 이는 로그인 화면 자체를 인증 대상에 포함시키면 미인증 사용자가 로그인 화면에 접근할 수 없어 리다이렉트 루프가 발생하기 때문이다. 그 외 `/admin/**`, `/api/admin/**` 하위 경로는 모두 세션 기반 인증(ROLE_ADMIN)을 요구한다.

일반 사용자가 이용하는 조회용 API(`/api/pages/**`, `/api/programs/**`, `/api/boards/**`, `/api/banners`, `/api/popups`, `/api/files/{id}` 등)는 인증 없이 접근 가능하며, `/api/admin/**` 하위 API만 인증을 요구한다(API.md 기준).

권한

```
ROLE_ADMIN
```

단일 권한 사용

## CSRF 정책

관리자 CMS는 세션 기반 인증을 사용하고, `/admin/**` 화면에서 JS(fetch)로 `/api/admin/**`의 상태 변경 API(POST/PUT/PATCH/DELETE)를 호출하므로 CSRF 공격에 노출될 수 있다(PRD.md 비기능요구사항 "CSRF 보호" 근거).

- `CookieCsrfTokenRepository.withHttpOnlyFalse()`를 사용해 CSRF 토큰을 `XSRF-TOKEN` 쿠키로 발급한다.
- 관리자 화면의 공통 JS(fetch 유틸)는 요청 시 해당 쿠키 값을 읽어 `X-XSRF-TOKEN` 헤더에 담아 전송한다.
- `POST /api/admin/login`은 세션 수립 이전 최초 요청이므로 CSRF 토큰 없이도 호출 가능하도록 예외 처리하며, 로그인 성공 후 발급되는 세션에 새 CSRF 토큰이 결합된다.
- `GET`으로 상태를 변경하는 API는 두지 않는다(RESTful 원칙 준수, CONVENTION.md 기준).

---

# DTO

Entity는 직접 반환하지 않는다.

```
Controller

↓

Request DTO

↓

Service

↓

Entity

↓

Response DTO
```

---

# Exception

GlobalExceptionHandler

처리

- Validation
- Authentication
- Authorization
- File Upload
- Business Exception

---

# Logging

로그 관리

- 로그인
- 예외
- 파일 업로드
- 관리자 작업

---

# Database

MariaDB

ORM

- Spring Data JPA
- QueryDSL

공통 Entity

```
BaseEntity
```

---

# File Storage

저장 루트는 `UPLOAD_ROOT` 환경변수로 주입한다. 파일은 저장 루트 아래 날짜별 디렉토리에 저장한다.

```
${UPLOAD_ROOT}/yyyy/MM/dd/{uuid}.{ext}
```

Docker 운영 기본값:

```
UPLOAD_ROOT=/app/uploads
```

파일명은 UUID를 사용한다.

DB의 `UploadFile.path`에는 `UPLOAD_ROOT`를 제외한 상대경로(`yyyy/MM/dd/{uuid}.{ext}`)만 저장하고, 실제 파일 조회/삭제 시 `FileService`가 `UPLOAD_ROOT`와 결합한다.

---

# Frontend

- Thymeleaf
- Bootstrap 5
- JavaScript ES6
- CKEditor5

## 공개 화면 디자인 시스템 원칙 (Phase 14, P14-T0)

공개 홈페이지 시각 디자인 개선(Phase 14)의 구조 관련 원칙이다. 세부 디자인 계약, Task 구성, 보존 대상 DOM/JS/Test 목록은 `docs/TASK.md`의 "Phase 14"를 따르며 여기에 중복 기재하지 않는다. 이 원칙은 기능/URL/Controller/data flow를 바꾸지 않는다.

- 공개 스타일은 `static/css/home.css` 단일 파일이며 관리자 CSS(`static/css/admin/`)와 분리한다. Phase 14 동안 `home.css`를 분할하지 않는다(필요하면 사유와 영향을 보고하고 별도 승인 후 진행).
- 색상/typography/spacing/radius 등 디자인 값은 `:root`의 CSS custom property(token)로 관리하고, component는 token을 참조한다. 기존 token 이름은 가능한 한 유지하고 값 조정을 우선한다. `!important`는 추가하지 않는다.
- Bootstrap 5.3.3 CDN은 유지한다. 필요한 범위에서만 공개 화면의 Bootstrap 기본 스타일(`list-group`, `badge`, `btn`, `form-control`)을 override하며, 새 CSS framework와 새 npm dependency는 도입하지 않는다.
- 폰트는 단일 sans 계열을 우선 후보로 하되 제공 방식(self-host/CDN/system font)은 P14-T1에서 license 원문 확인 후 결정한다. 시스템 한글 sans stack이 항상 fallback으로 동작해야 한다.
- 사용자에게 보이는 enum 표시명(예: `COURSE`, `OPEN`, `NOTICE`의 한글 표시)은 presentation layer에서만 적용한다. domain enum, DB 값, API/internal value는 변경하지 않는다.
- `header.html`/`nav-*.js`/`hero-carousel.js`/`popup-modal.js`가 의존하는 id/class/data 속성과 Playwright/Java 테스트가 의존하는 selector는 보존한다. 디자인을 위해 안정된 DOM을 불필요하게 바꾸지 않으며, 재구성이 꼭 필요하면 기존 selector를 보존한 wrapper 추가부터 검토한다.
- 공통 layout fragment(`home/layout/default.html`)의 fragment 시그니처와 `#site-main` 구조, 900px navigation breakpoint(P13 결정)는 Phase 14에서 변경하지 않는다(폰트 link, skip link 추가처럼 TASK.md에 명시된 범위의 추가만 허용).

---

# URL

Public

```
/

/pages/{type}

/programs

/programs/{id}

/boards

/boards/{id}
```

Admin

```
/admin/login

/admin/dashboard

/admin/pages
/admin/pages/{pageType}/edit

/admin/programs
/admin/programs/new
/admin/programs/{id}/edit

/admin/boards
/admin/boards/new
/admin/boards/{id}/edit

/admin/banners
/admin/banners/new
/admin/banners/{id}/edit

/admin/popups
/admin/popups/new
/admin/popups/{id}/edit

/admin/files

/admin/menus
/admin/menus/new
/admin/menus/{id}/edit
```

- `/new`, `/{id}/edit`은 각 도메인 `Admin{Domain}ViewController`가 등록/수정 폼 화면을 렌더링하는 경로이며, 실제 저장/수정은 화면의 JS가 `Admin{Domain}Controller`의 `POST`/`PUT` API를 호출한다.
- Page는 4개 타입(GREETING/INTRODUCTION/HISTORY/LOCATION)이 타입별 단일 레코드이므로 `/new` 없이 `/admin/pages/{pageType}/edit`만 사용한다.

---

# Design Principles

- MVC Architecture
- Layered Architecture
- Repository Pattern
- DTO Pattern
- Builder Pattern
- RESTful API
- DI
- SRP
- BaseEntity 공통 사용

도메인 설계 원칙(Program/Board 통합, Google Form 연동 등)은 ERD.md의 "설계 원칙"을 따른다.

---

# 구현 전 확정 운영 계약

## 관리자 조회 API
관리자 Thymeleaf 화면은 공개 API를 데이터 소스로 사용하지 않는다. `API.md`에 정의된 `/api/admin/{domain}` 조회 API를 사용하여 비공개/비노출 리소스도 조회한다. ViewController는 화면 진입만 담당하고 데이터는 공통 fetch 유틸로 조회한다.

## Page 생명주기
`CmsPage`는 `GREETING`, `INTRODUCTION`, `HISTORY`, `LOCATION` 4개 고정 리소스다. 초기화 시 누락 레코드만 생성하며 중복 생성하지 않는다. CMS에서는 조회/수정만 지원하고 생성/삭제 API와 `/new` 화면은 두지 않는다.

## Nginx 정적 리소스 공급
운영 Docker Compose에서 Nginx가 직접 서빙하는 프로젝트 정적 리소스는 배포 checkout의 `./src/main/resources/static`을 Nginx 기본 정적 루트 `/usr/share/nginx/html`에 read-only bind mount(`./src/main/resources/static:/usr/share/nginx/html:ro`)하여 공급한다. `/css/**`, `/js/**`, `/images/**`, `/vendor/**` 등 해당 정적 경로는 Nginx가 직접 처리하고, 그 외 애플리케이션 요청은 Spring Boot 컨테이너로 reverse proxy한다. P12-T2에서는 이 계약을 그대로 사용하며 별도 static copy/shared-volume 방식을 임의 선택하지 않는다.

## DB 스키마 관리
운영 재현성을 위해 Flyway migration을 사용한다. `ddl-auto`는 local/test에서 검증 목적 설정을 명시하고 prod에서는 `validate`를 사용한다. 스키마 변경은 migration 파일로 관리하며 운영에서 `update/create`로 자동 변경하지 않는다.

## 초기 관리자 계정
`ApplicationRunner`가 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME` 환경변수를 읽어 계정이 없을 때만 BCrypt 해시로 초기 관리자 1건을 생성한다. `PasswordEncoder` BCrypt Bean은 Admin 초기화 Task에서 함께 정의하고 SecurityConfig는 이를 주입받아 사용하여 초기화가 미래 Security Task에 의존하지 않게 한다. 운영용 기본 비밀번호를 소스/data.sql에 저장하지 않는다. 필수 환경변수가 없으면 운영 프로파일에서는 초기 계정을 생성하지 않고 명확한 오류 로그를 남긴다.

## 업로드 영속성
업로드 루트는 `UPLOAD_ROOT` 환경변수로 설정하며 기본 Docker 경로는 `/app/uploads`다. `docker-compose.yml`은 `./data/uploads:/app/uploads` bind mount를 사용하여 컨테이너 재생성 후에도 파일을 유지한다. MariaDB는 `db_data:/var/lib/mysql` named volume을 사용하여 컨테이너 재생성 후에도 DB 데이터와 Flyway 적용 이력을 유지한다.

## Health / 배포 자동화 범위
헬스체크는 Spring Boot Actuator `/actuator/health`를 사용한다. GitHub Actions는 CI(test/build)까지만 자동화하고 실제 운영 배포는 Docker Compose 수동 배포를 기본 범위로 한다.

## 타임존
모든 시각 컬럼은 타임존을 저장하지 않는 `DATETIME`이고 애플리케이션이 `LocalDateTime.now()`로 현재 시각을 판단하므로, `docker-compose.yml`의 `app`/`db` 컨테이너는 `TZ=Asia/Seoul`로 시간 기준을 통일한다.
