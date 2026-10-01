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
    ├── theme
    │
    └── home
```

패키지 설명

- `config` : 애플리케이션 전역 설정. 현재 `SecurityConfig`(Spring Security 인증/인가, CSRF, 인증 실패 EntryPoint 연결)가 위치한다. 별도 `security` 패키지는 두지 않는다 - 로그인 처리는 `admin`(`AdminAuthController`/`AdminService`), BCrypt `PasswordEncoder` Bean은 `admin.config.AdminPasswordConfig`, 인증/인가 실패 응답은 `common.exception`(`CustomAuthenticationEntryPoint`/`CustomAccessDeniedHandler`)에 있다.
- `common` : 여러 도메인에서 공통으로 사용하는 클래스 모음
  - `common.config` : common 패키지 내부에서 사용하는 설정(QueryDSL 설정, 공통 리소스 설정 등). 최상위 `config`와 달리 common 모듈 범위에 한정된 설정을 담당한다.
  - `dto` : 공통으로 사용하는 Request/Response DTO
  - `entity` : BaseEntity 등 공통 Entity
  - `exception` : CustomException, ErrorCode 등 예외 관련 클래스
  - `response` : ApiResponse 등 공통 응답 포맷
  - `util` : 공통 유틸리티(`HtmlSanitizer`, `ContentLinkRenderer`, `PaginationSupport`)
- `admin`, `page`, `program`, `board`, `banner`, `popup`, `file`, `menu`, `pinned`, `theme` : 도메인별 패키지. 각 패키지는 Controller, Service, Repository로 구성되는 Layered Architecture를 따른다.
- `home` : 공개 메인 화면(`GET /`) 전용 패키지. 자체 Entity/Repository 없이 Page, Program, Board, Banner, Popup Service를 조합하여 메인 화면 데이터를 구성하는 Controller만 포함한다. `pinned`가 별도 최상위 패키지인 이유가 바로 이 제약이다 - HomePinnedContent는 Entity/Repository를 가지므로 `home` 아래에 둘 수 없다.

---

# Domain

## Admin 화면(View) / API 컨트롤러 명명 규칙

`/admin/**`(Thymeleaf 화면)과 `/api/admin/**`(REST API)는 인증 대상은 같지만 응답 형식이 다르므로(HTML vs JSON), 도메인마다 **두 개의 컨트롤러로 분리**한다. 하나의 컨트롤러가 화면 렌더링과 JSON 응답을 함께 처리하지 않는다.

| 구분 | 네이밍 | 책임 | 예시 |
|---|---|---|---|
| API | `Admin{Domain}Controller` | `@RestController`, `/api/admin/{domain}` 하위 CRUD/상태변경 API, `ApiResponse` 반환 | `AdminProgramController` |
| View | `Admin{Domain}ViewController` | `@Controller`, `/admin/{domain}` 하위 화면(목록/등록/수정 폼) 렌더링, Thymeleaf View 이름 반환. 데이터는 자체 조회하지 않고 화면 진입만 담당하며, 실제 데이터는 화면의 JS가 P3-T5 공통 fetch 유틸로 `Admin{Domain}Controller`의 API를 호출해 채운다 | `AdminProgramViewController` |

이 규칙은 Program, Board, Page, Banner, Popup, File, Menu, Theme, Admin(Dashboard 포함) 전 도메인에 동일하게 적용한다. 공개 영역의 `{Domain}Controller`(API) / `{Domain}ViewController`(View) 분리(Page/Program/Board)와 동일한 패턴이다.

**예외 — CKEditor 본문을 표시하는 읽기 전용 관리자 상세 View(Board/Program)**: 관리자 목록/등록/수정 화면은 위 원칙을 그대로 따른다. 단, Board/Program의 읽기 전용 관리자 상세 View(`GET /admin/boards/{id}`, `GET /admin/programs/{id}`)는 CKEditor HTML 본문을 공개 상세와 동일한 안전한 렌더링 경로로 출력해야 하므로, 해당 `Admin{Domain}ViewController`가 기존 관리자 조회 Service(`getAdminById`, 공개 여부 무관)를 호출해 결과 DTO를 model로 전달한다. 본문은 저장 시 `HtmlSanitizer`로 정제된 값을 대상으로 조회 시 `ContentLinkRenderer.externalLinksOpenInNewTab`을 적용한 `renderedContent`로만 전달하고, 템플릿은 `th:utext`로 이 값만 출력한다(아래 "XSS 방지 정책", "콘텐츠 링크 처리 정책"과 동일). JS fetch + `innerHTML` 방식으로 HTML/링크 처리 정책을 별도로 중복 구현하지 않기 위한 예외이며, 다른 관리자 화면에는 일반화하지 않는다(P14-T10에서 도입).

## Admin

관리자 로그인 및 대시보드

```
AdminController             (GET /api/admin/me — 로그인한 관리자 본인 정보 조회 전용. 타 관리자 계정 조회/등록/수정 API는 없음, API.md 기준)
                            (P15-T6: 같은 Controller의 PUT /api/admin/me/password — 본인 비밀번호 변경, API.md 기준)
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
- 본인 비밀번호 변경(CURRENT — P15-T6, 화면 `GET /admin/password`는 `AdminViewController`, 진입 링크는 header 로그아웃 옆)

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
AdminProgramViewController  (GET /admin/programs 목록/등록/수정 화면 + GET /admin/programs/{id} 읽기 전용 상세 렌더링, Thymeleaf)

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
AdminBoardViewController    (GET /admin/boards 목록/등록/수정 화면 + GET /admin/boards/{id} 읽기 전용 상세 렌더링, Thymeleaf)

BoardService

BoardRepository
```

boardType

```
NOTICE

GALLERY

ARCHIVE

REVIEW      (강의 후기, P13-T16)
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

## Theme

공개 홈페이지 디자인 설정(포인트 컬러 프리셋 + 메인 섹션 노출 여부) 도메인이다(P14-T8A~T8D). Entity를 가지므로 `home`이 아닌 독립 최상위 패키지 `com.monicalab.theme`에 둔다. 테이블은 ERD.md `# 10. SiteThemeSetting`, API는 API.md `# Theme`을 따른다.

```
AdminThemeController        (GET/PUT /api/admin/theme)
AdminThemeViewController    (GET /admin/theme 설정 화면 렌더링, Thymeleaf)
ThemeControllerAdvice       (공개 View Controller 4개에 siteTheme model attribute 공급)

SiteThemeSettingService

SiteThemeSettingRepository

SiteThemeSetting

AccentPreset                (TERRACOTTA / BURGUNDY / FOREST)
```

- `ThemeControllerAdvice`는 `HeaderMenuControllerAdvice`와 같은 4개 공개 View Controller(`HomeController`/`PageViewController`/`ProgramViewController`/`BoardViewController`)에만 `@ControllerAdvice(assignableTypes=...)`로 적용된다. `home/layout/default.html`의 `<html data-theme>`가 accent 프리셋을, `home/index.html`의 5개 섹션 `th:if`가 노출 여부를 반영한다.
- 조회(`getSetting()`)는 행이 없으면 DB write 없이 기본값으로 fallback하고, 저장(`updateSetting()`)은 행이 없으면 404로 처리한다. 행 생성은 V13 migration 시드만 담당한다.
- 실제 색상 값과 WCAG 대비 검증은 `home.css`에 있고, enum은 식별자 역할만 한다.

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
- **CURRENT(P15-T4) — 툴바 ↔ sanitizer 계약**: "툴바/Autoformat/PasteFromOffice로 만들 수 있고 FEATURES.md가 지원한다고 표시한 서식 = 저장 후 보존되는 서식"을 원칙으로 한다.
  - `EDITOR_CONFIG.toolbar.items`를 명시 선언한다: `undo, redo, |, heading, |, bold, italic, |, link, uploadImage, insertTable, blockQuote, |, bulletedList, numberedList, outdent, indent`. build 기본 툴바(`ClassicEditor.defaultConfig`, 헤드리스 실측)에서 `mediaEmbed`만 뺀 것으로 구분선 위치/순서는 동일하다. 표 툴바는 재선언하지 않아 build 기본값(`tableColumn, tableRow, mergeTableCells`)을 그대로 쓴다.
  - `removePlugins: ['MediaEmbed']`로 플러그인 자체도 제거한다. 툴바에서만 빼면 `mediaEmbed` command가 남아 YouTube URL 붙여넣기/`setData`/HTML 붙여넣기로 `<figure class="media"><oembed>`가 계속 생성되고, 반대로 `removePlugins`만 쓰면 기본 툴바가 없는 항목을 찾아 `toolbarview-item-unavailable {item: mediaEmbed}` 경고가 나므로 둘을 함께 적용한다(헤드리스 실측: 두 설정 동시 적용 시 초기화 console 로그 0, oembed/iframe은 에디터 단계에서 제거, URL은 평문으로 남음).
  - build에는 `Autoformat`(`* `/`- ` → 글머리 목록, `1. ` → 번호 목록, `> ` → 인용, `_x_`/`*x*` → 기울임)과 `PasteFromOffice`가 포함되어 있어 툴바와 무관하게 목록/인용/기울임/표 머리글/병합 셀이 생성될 수 있으며, 아래 XSS 정책의 허용 태그가 이 출력을 보존한다. `IndentBlock`은 없으므로 indent/outdent는 목록 중첩에만 동작한다.
  - 인용 표시 스타일은 공개 상세(`.ckeditor-content`)/공개 팝업 본문(`.popup-modal__body`, 이상 `home.css`)과 관리자 읽기 전용 상세(`admin.css`)에 최소 규칙(왼쪽 구분선 + padding + 보조 텍스트 색)만 둔다. 목록(중첩 포함)은 Bootstrap reboot 기본값(마커 + 들여쓰기)으로 충분히 읽혀 별도 규칙을 두지 않는다. 새 CKEditor plugin/npm 패키지/번들러/self-host는 도입하지 않는다.

## XSS 방지 정책

CKEditor5로 작성된 콘텐츠는 HTML 형태로 저장되며, Thymeleaf에서 `th:utext`로 그대로 출력되므로 저장형 XSS(Stored XSS)에 노출될 수 있다. 다음 정책을 따른다.

- 서버 저장 시점에 HTML 화이트리스트 정제(sanitize)를 수행한다(예: OWASP Java HTML Sanitizer 또는 jsoup의 `Safelist` 사용).
- 허용 태그(**CURRENT**, `HtmlSanitizer` 실제 구현): 텍스트 서식(`p`, `br`, `strong`, `em`, `i`, `u`, `h1~h6`), 목록(`ul`, `ol`, `li` - 중첩은 `li` 안의 하위 목록), 인용(`blockquote`), 표(`table`, `thead`, `tbody`, `tr`, `td`, `th`), 링크(`a[href]`), 이미지(`img[src|alt]`), 이미지 wrapper(`figure[class]`, `figcaption`). P15-T4에서 추가한 `ul`/`ol`/`li`/`blockquote`/`i`/`thead`/`tbody`는 **attribute를 하나도 허용하지 않는다**(`style`/`class`/`id`/`on*`/`start`/`type`/`value`/`cite` 등 전부 제거). CKEditor 41.4.2의 기울임 출력은 `<i>`이며 기존 `<em>`도 계속 허용한다(에디터가 재편집 시 `<em>`을 `<i>`로 upcast).
- 표 셀 병합(**CURRENT — P15-T4**): `td`/`th`에만 `colspan`/`rowspan`을 허용하고, clean 이후 후처리 pass(`restrictTableCellSpans`, `img src` 후처리와 같은 패턴)에서 값이 정규식 `^(?:[1-9]|[1-4][0-9]|50)$`(정확히 ASCII 10진 정수 1~50)과 일치할 때만 유지한다. 그 외(0, 51 이상, 음수, 소수, 문자, 빈 값, 앞뒤 공백, 선행 0, `+` 부호, 지수, 전각 숫자, 단위 등)는 **해당 attribute만** 제거하고 셀과 셀 내용은 유지한다. 다른 태그의 `colspan`/`rowspan`은 Safelist 단계에서 제거된다.
- `iframe`/`oembed`/`style` 일반 허용은 추가하지 않는다 - 미디어/지도 embed는 지원하지 않는다(FEATURES.md "편집 서식 보존 계약"). 과거 저장된 `<figure class="media"><oembed>`는 기존과 같이 빈 `<figure>`로 정제된다. CKEditor 표 wrapper `<figure class="table">`의 `table` class 토큰은 아래 figure class 화이트리스트 대상이 아니어서 `<figure>`로 저장되는 기존 동작을 유지한다(표 구조·재편집에는 영향 없음).
- `figure`의 `class` 속성은 값 전체를 정규식으로 검증하지 않고, whitespace로 분리한 토큰 단위로 화이트리스트(`image`, `image-style-side`, `image-style-align-left`, `image-style-align-right`, `image-style-align-center`, `image_resized`)와 대조해 안전한 토큰만 남긴다. `img`에는 class를 허용하지 않는다. `img`의 `width`/`height`, font 관련 속성·값은 허용하지 않는다.
- `figure`의 `style` 속성은 Safelist 자체에는 추가하지 않는다(`style` 전체를 허용하지 않는 원칙 유지). 대신 이미지 크기 조절(P13-T40) 값만 별도의 "extract(clean 이전 원본에서 검증) → clean → reinject(검증된 값만 재적용)" 패턴으로 좁게 허용한다: `style`이 정확히 `width: 25%;`/`50%;`/`75%;`(공백·세미콜론 변형만 허용) 전체 일치일 때만 그 값을 clean 이후 다시 그려 넣고, 그 외 값이나 다른 property가 하나라도 섞이면 `style` 전체를 폐기한다. 향후 확장 시에도 이 좁은 값 단위 화이트리스트만 확장하고 `style` 속성을 Safelist에 일반 허용하지 않는 것을 원칙으로 한다.
- `script`, `iframe`, `on*` 이벤트 속성, `javascript:` 스킴 링크는 모두 제거한다.
- 정제는 `PageService`, `ProgramService`, `BoardService`, `PopupService`의 등록/수정 로직에서 공통 유틸(`common/util/HtmlSanitizer.java`)을 통해 일괄 적용한다.

## 콘텐츠 링크 처리 정책

CKEditor5 본문에 삽입된 `<a href>` 링크는 외부/내부 여부에 따라 다르게 열려야 한다(외부는 새 탭, 내부는 같은 탭). 다음 정책을 따른다.

- 링크 판별 기준: `href`가 `http://`, `https://`, `//`로 시작하면 외부 링크, 그 외(상대 경로, `mailto:`, `#anchor` 등)는 내부/비외부 링크로 간주한다.
- 판별 및 속성 부여는 저장 시점이 아닌 공개 화면 렌더링 시점에 수행한다(공통 유틸 `common/util/ContentLinkRenderer.java`). 이미 저장된 기존 콘텐츠도 별도 마이그레이션이나 재저장 없이 자동 적용된다.
- 외부 링크에는 `target="_blank" rel="noopener noreferrer"`를 부여하고, 내부 링크는 변경하지 않는다.
- `Board`, `Program`, `Page` 상세 화면과 `Popup` 노출 화면, 총 4곳의 공개 뷰가 대상이다. 각 공개 `*ViewController`(`HomeController` 포함)가 `ContentLinkRenderer`로 미리 변환한 HTML을 view 전용 model attribute(`renderedContent`, Popup은 `popupRenderedContents` Map)로 전달하고, 템플릿은 `th:utext`로 이 값만 출력한다. 관리자 읽기 전용 상세 View(`/admin/boards/{id}`, `/admin/programs/{id}`)도 같은 방식(`renderedContent` + `th:utext`)으로 이 변환을 적용한다(위 "Admin 화면(View) / API 컨트롤러 명명 규칙"의 예외 참고).
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

HtmlSanitizer

ContentLinkRenderer

PaginationSupport
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

## 쿠키 / HTTPS 인식 / HSTS

- **CURRENT(P15-T1 구현)**: prod profile(`application-prod.yml`)에 `server.forward-headers-strategy: native`(Tomcat RemoteIpValve, docker 내부망 proxy 신뢰), `server.tomcat.use-relative-redirects: true`, `server.servlet.session.cookie.same-site: lax`가 적용되어 있다. `Secure`는 별도 강제 설정 없이 Nginx가 전달한 `X-Forwarded-Proto: https`로 요청을 HTTPS로 인식할 때 `JSESSIONID`/`XSRF-TOKEN`에 자동 적용된다(forward header가 없는 평문 HTTP 요청에는 붙지 않음). `JSESSIONID`는 `HttpOnly` + `SameSite=Lax`다.
- **CURRENT(P15-T1) — 관리자 login redirect**: `/admin/**` 미인증 요청의 redirect `Location`은 상대 URI `/admin/login`이다(모든 profile). Spring Security `LoginUrlAuthenticationEntryPoint`는 기본적으로 요청 scheme/host/port로 절대 URL을 직접 조립하므로 Tomcat `use-relative-redirects`만으로는 상대 URI가 되지 않는다 - `SecurityConfig`의 기본 EntryPoint에 `setFavorRelativeUris(true)`를 적용해 구현했다. `server.tomcat.use-relative-redirects: true`는 운영 runtime 계약을 명시하는 설정으로 유지한다. `/api/admin/**` 미인증은 기존대로 redirect 없이 401 JSON(`UNAUTHORIZED`)이다.
- **CURRENT(P15-T1) — HSTS**: HSTS는 Nginx가 단독으로 담당하므로 `SecurityConfig`에서 Spring Security HSTS header만 비활성화했다(Spring 기본값은 `includeSubDomains` 포함). `X-Content-Type-Options`, `X-Frame-Options` 등 나머지 Spring Security 기본 보안 헤더는 유지한다. 상세는 "운영 배포 계약(Phase 15)".
- 관리자 로그인 시도 제한은 애플리케이션이 아니라 Nginx가 담당한다(CURRENT, P15-T3).
- 관리자 본인 비밀번호 변경(CURRENT, P15-T6)은 기존 세션 인증 + CSRF + BCrypt를 그대로 사용하며(`SecurityConfig` 무변경 - 기존 `/api/admin/**` matcher와 CSRF 설정이 적용됨), 성공 시 `AdminController`가 세션 ID를 교체한다(`changeSessionId`, 로그인과 동일한 세션 고정 방어). 세션 속성(SecurityContext)은 유지되므로 로그인 상태가 이어진다. 다른 세션은 강제 만료하지 않는다.

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

## 오류 응답 경로 분리

- **CURRENT(P15-T5) — 공개 HTML 오류 페이지**:
  1. `common/exception/PublicViewExceptionHandler`(`@ControllerAdvice(assignableTypes = {HomeController, PageViewController, ProgramViewController, BoardViewController})`, `@Order(Ordered.HIGHEST_PRECEDENCE)`)가 공개 View Controller 4개의 예외만 HTML 오류 페이지로 응답한다. Spring은 적용 가능한 advice를 `@Order` 순서로 보고 매핑이 있는 첫 advice를 쓰므로, `@Order`가 없는(최저 우선순위) `GlobalExceptionHandler`보다 항상 먼저 선택된다. 매핑: `CustomException`은 `ErrorCode.httpStatus` 그대로(`*_NOT_FOUND` 404, `INVALID_INPUT_VALUE` 400, 5xx는 5xx 페이지), `MethodArgumentTypeMismatchException`은 `@PathVariable`이면 타입(enum/Long)과 무관하게 404(예: `/pages/NOPE`, `/boards/abc`) / query 값이면 400(예: `?boardType=NOPE`), `BindException`/`ConstraintViolationException`/`MissingServletRequestParameterException`은 400, 그 외 예외는 500. 응답은 `ModelAndView`(`error/4xx` 또는 `error/5xx`, 실제 HTTP status 명시, model은 `status`만)다. 로그는 `GlobalExceptionHandler`(P15-T1)와 같은 형식/레벨이다(4xx `CustomException`은 stacktrace 없는 WARN, 5xx `CustomException`과 미처리 예외는 ERROR + stacktrace, 잘못된 입력은 stacktrace 없는 WARN).
  2. `GlobalExceptionHandler`는 not-found handler(`NoHandlerFoundException`/`NoResourceFoundException`) 한 곳만 **요청 경로**로 분기한다(Accept header 무관): `/api/`로 시작하면 기존 JSON(`RESOURCE_NOT_FOUND`), 그 외 미매핑 경로(공개 주소, 로그인 상태의 `/admin/` 미매핑 주소 포함)는 HTML 404(`error/4xx`). `@RestControllerAdvice`여도 실제 반환값이 `ModelAndView`면 Spring의 `ModelAndView` return value handler가 `@ResponseBody` 처리보다 먼저 선택되어 view로 렌더링된다. 나머지 handler 메서드와 JSON 계약은 변경하지 않았다.
  3. 오류 템플릿은 Spring Boot 관례 이름 `templates/error/4xx.html`(404와 그 외 4xx 문구 분기), `templates/error/5xx.html`의 **독립 템플릿**이다. `home/layout/default`, header/footer fragment, header 메뉴(`headerMenuItems`), `siteTheme` 등 DB 조회에 의존하는 model/fragment를 사용하지 않는다. 예외 처리 경로에서는 Controller advice의 `@ModelAttribute`가 실행되지 않으므로 메뉴/테마(DB) 장애로 인한 500도 렌더링된다. 예외 메시지/stacktrace/경로/SQL/ErrorCode를 출력하지 않고(Boot `server.error.include-*` 기본값 유지, `path` 속성은 템플릿에서 참조하지 않음), JS와 Bootstrap/Pretendard CDN 없이 `/css/home.css`의 `.error-page` 규칙(`:root` 기본 토큰)만 쓴다. Filter/렌더링 단계 오류와 `/error`(Boot `BasicErrorController` + `DefaultErrorViewResolver`)도 같은 템플릿을 사용하며, Whitelabel 페이지는 더 이상 쓰이지 않는다. custom ErrorController/ErrorViewResolver는 두지 않는다.
  4. 변경하지 않은 것: `/api/**` JSON 계약, 관리자 API, 관리자 View Controller 안에서 발생한 상세 404(`/admin/boards/{id}` 등은 `PublicViewExceptionHandler` 대상이 아니므로 JSON 유지), Spring Security 단계의 401/403(`CustomAuthenticationEntryPoint`/`CustomAccessDeniedHandler` JSON)과 `/admin/**` 미인증 `/admin/login` redirect. 공개 오류 페이지(`error/4xx`·`5xx`)는 공통 layout을 쓰지 않는 독립 템플릿이라 P15-T7A의 SEO meta/OG가 붙지 않으며 `noindex`도 두지 않는다(실제 HTTP status로 충분). SEO 메타 계약(CURRENT — P15-T7A): `static/robots.txt`는 `User-agent: *` + `Disallow: /admin/`·`/api/admin/`만 두는 도메인 비의존 crawler 지침(접근 통제 아님 - 인가는 `SecurityConfig`, nginx/Security 무변경으로 Spring static이 permitAll 범위에서 제공)이고, 공개 공통 layout(`home/layout/default.html`)이 공개 6개 화면에 공통 meta description과 기본 OG 5종을 렌더링한다. 최종 title은 layout에서 한 번만 계산해 `<title>`과 `og:title`에 같은 값을 쓴다(pageTitle이 없으면 사이트명만 - 홈은 `null`을 넘긴다). favicon/`og:image`/`og:url`/canonical/sitemap/페이지별 description은 두지 않는다(앞 셋은 P15-T7B).

---

# Logging

로그 관리

- **CURRENT**: 초기 관리자/고정 페이지 생성은 INFO, 초기 관리자 환경변수 누락은 ERROR, 예외는 `GlobalExceptionHandler`가 기록한다. 로그인 실패는 `AUTHENTICATION_FAILED` `CustomException` WARN으로만 남고, 로그인 성공/파일 업로드/관리자 작업 감사 로그는 별도로 남기지 않는다. 요청 단위 기록(IP/경로/상태)은 Nginx access log(컨테이너 stdout)가 담당한다. 비밀번호 등 민감 정보는 로그에 남기지 않는다. 별도 logback 설정 파일/file appender는 없고 stdout만 사용한다.
- **CURRENT(P15-T1) — 예외 로그 레벨**: `CustomException`은 `ErrorCode.getHttpStatus()` 기준으로 분기한다. 5xx(예: `FILE_UPLOAD_FAILED`)는 `CustomException: code={}, status={}` ERROR + stacktrace, 그 외(4xx 등)는 같은 형식의 stacktrace 없는 WARN 한 줄이다(요청 URL/본문/메시지는 넣지 않음). 미처리 예외는 기존대로 ERROR + stacktrace, Validation/Bind/ConstraintViolation/NoResourceFound 등 나머지 handler는 기존대로 stacktrace 없는 WARN이며 응답 JSON 계약은 변경하지 않았다. 참고: `FileService`가 `FILE_UPLOAD_FAILED`를 던질 때 원인 `IOException`을 cause로 전달하지 않아 5xx stacktrace에 root cause가 남지 않는다(Phase 15 범위 밖 후속 후보).
- **CURRENT(P15-T1) — SQL 로그**: base `application.yml`의 `spring.jpa.show-sql: true`/`format_sql: true`는 local/test용으로 유지하고, prod profile(`application-prod.yml`)에서 둘 다 `false`로 override한다. `show-sql`은 logger가 아니라 Hibernate가 stdout에 직접 출력하므로 logging level이 아닌 이 설정으로 끈다.
- **CURRENT(P15-T2) — 컨테이너 로그 보관량**: Docker `json-file` 로그 rotation(`app`/`db`/`nginx` 모두 `max-size: 10m`, `max-file: 3`)이 적용되어 있다("운영 배포 계약(Phase 15)" 참고). 이는 컨테이너 stdout/stderr 보관량 제한일 뿐 애플리케이션 로그 레벨(P15-T1)과는 별개다. 감사 로그/중앙 로그 수집은 Phase 15 범위 밖이다.

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

/admin/home-pinned-contents

/admin/theme

/admin/password        (P15-T6 — 본인 비밀번호 변경, sidebar 항목 아님)
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

**정적 리소스 캐시 정책**: Nginx가 직접 제공하는 `/css/`, `/js/`, `/images/`, `/vendor/` 응답에는 `Cache-Control: no-cache`를 명시한다(`nginx/nginx.conf`의 각 정적 location, P14-T11). 브라우저는 응답을 저장할 수 있지만 사용 전에 ETag/Last-Modified로 재검증해야 하며, 변경이 없으면 304로 응답한다. 명시적 정책이 없으면 브라우저가 heuristic freshness로 이전 CSS/JS를 재검증 없이 재사용해 "최신 HTML(Spring이 `no-store` 계열로 응답) + 이전 CSS/JS" 조합이 생길 수 있기 때문이다. 이는 versioned asset URL이 없는 현재 구조의 안전한 기본값이며, versioned asset URL을 도입하면 long-lived cache + `immutable`로 재검토한다. 새 정책은 이 헤더를 받은 응답부터 적용되므로, 정책 도입 이전에 이미 heuristic freshness 상태로 저장된 브라우저 캐시는 만료되거나 새로고침으로 새 응답을 받기 전까지 남을 수 있다.

**운영 배포 전 결정 필요**: 현재 정적 리소스는 host checkout bind mount, application/template은 app image로 서로 다른 release lifecycle을 가진다. 그래서 `git pull` 시점에 정적 리소스만 먼저 바뀌거나, app image를 rollback해도 정적 리소스는 새 버전으로 남을 수 있어 atomic deployment/rollback이 보장되지 않는다. 운영 배포 전에 application/template/static asset을 동일 release version으로 묶는 방식, Nginx 정적 리소스의 image 기반 제공 여부, release directory/symlink 방식, versioned asset URL, long-lived cache + `immutable`을 함께 결정한다(현재 계약에서는 특정 방식을 확정하지 않는다).

**결정(Phase 15, P15-T0 — 위 "운영 배포 전 결정 필요" 항목을 닫는다)**: 현재 구조(Nginx가 host checkout의 static을 직접 제공, app image는 같은 checkout에서 build)를 유지하고 재설계하지 않는다. 대신 **"release tag checkout → `docker compose up -d --build`"를 하나의 배포 단위**로 취급해 app/template과 static의 버전 일치를 보장한다. `git pull`/`git checkout`만 하고 rebuild하지 않는 것, app image만 별도로 rollback하는 것은 금지한다. rollback도 "이전 release tag checkout → 전체 `up -d --build`"로 수행한다. Nginx image bake, release directory/symlink, versioned asset URL, long-lived cache는 도입하지 않으며 `Cache-Control: no-cache` 재검증 정책을 유지한다. 상세 절차는 `docs/OPERATIONS.md` §15(배포/rollback)와 GIT_WORKFLOW.md "develop → main 승격"을 따른다.

## DB 스키마 관리
운영 재현성을 위해 Flyway migration을 사용한다. `ddl-auto`는 local/test에서 검증 목적 설정을 명시하고 prod에서는 `validate`를 사용한다. 스키마 변경은 migration 파일로 관리하며 운영에서 `update/create`로 자동 변경하지 않는다.

## 초기 관리자 계정
`ApplicationRunner`가 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME` 환경변수를 읽어 계정이 없을 때만 BCrypt 해시로 초기 관리자 1건을 생성한다. `PasswordEncoder` BCrypt Bean은 Admin 초기화 Task에서 함께 정의하고 SecurityConfig는 이를 주입받아 사용하여 초기화가 미래 Security Task에 의존하지 않게 한다. 운영용 기본 비밀번호를 소스/data.sql에 저장하지 않는다. 필수 환경변수가 없으면 운영 프로파일에서는 초기 계정을 생성하지 않고 명확한 오류 로그를 남긴다.

## 업로드 영속성
업로드 루트는 `UPLOAD_ROOT` 환경변수로 설정하며 기본 Docker 경로는 `/app/uploads`다. `docker-compose.yml`은 `./data/uploads:/app/uploads` bind mount를 사용하여 컨테이너 재생성 후에도 파일을 유지한다. MariaDB는 `db_data:/var/lib/mysql` named volume을 사용하여 컨테이너 재생성 후에도 DB 데이터와 Flyway 적용 이력을 유지한다. Phase 15에서 적용한 volume 이름 고정과 compose `UPLOAD_ROOT` 고정(P15-T2)은 아래 "운영 배포 계약(Phase 15)"을 따른다.

## Health / 배포 자동화 범위
헬스체크는 Spring Boot Actuator `/actuator/health`를 사용한다. GitHub Actions는 CI(test/build)까지만 자동화하고 실제 운영 배포는 Docker Compose 수동 배포를 기본 범위로 한다.

## 타임존
모든 시각 컬럼은 타임존을 저장하지 않는 `DATETIME`이고 애플리케이션이 `LocalDateTime.now()`로 현재 시각을 판단하므로, `docker-compose.yml`의 `app`/`db` 컨테이너는 `TZ=Asia/Seoul`로 시간 기준을 통일한다.

---

# 운영 배포 계약(Phase 15)

Phase 15 Production Readiness(P15-T0)에서 확정한 운영 배포 구조 계약이다. 각 항목의 상태는 **CURRENT**(저장소에 구현됨 - P15-T1~T8 구현 항목은 모두 CURRENT이며 PLANNED로 남은 항목은 없다), **CLIENT-DEPENDENT**(favicon/`og:image`/`og:url` - P15-T7B, 발주처 asset·최종 도메인 대기, 미구현), **LAUNCH**(실제 도메인/인증서 발급/release/운영 smoke - 개발 Task가 아닌 배포 단계, OPERATIONS "Launch TBD")로 구분한다. Task 상세는 `docs/TASK.md` "Phase 15", 실제 운영 명령/절차는 `docs/OPERATIONS.md`(P15-T8, 운영 절차의 canonical 문서)를 따른다. 이 절은 구조/계약만 정의하고 명령어 runbook을 중복 기술하지 않는다.

## 전체 구조

```
Internet
  → nginx :80  (/.well-known/acme-challenge/ 만 응답, 그 외 → 301 https)
  → nginx :443 (TLS termination, HSTS, 관리자 로그인 rate limit, 정적 리소스, reverse proxy)
      → Spring Boot :8080 (docker 내부 HTTP, host 비노출)
          → MariaDB :3306 (docker 내부, host 비노출)
```

- **CURRENT(P15-T3)**: 위 구조. host에는 Nginx `:80`/`:443`만 노출되고, Spring Boot와 MariaDB는 계속 host 포트를 노출하지 않는다.

## TLS / 인증서 / HSTS (CURRENT — P15-T3, 실제 발급은 배포 단계)

- 인증서/개인키는 저장소에 저장하지 않는다. host `./data/certs/`(`.gitignore`의 `data/` 대상)에 고정 파일명 `fullchain.pem`/`privkey.pem`으로 두고, 컨테이너 `/etc/nginx/certs/`에 read-only mount한다. Nginx 설정은 도메인과 무관한 이 고정 경로만 참조한다.
- ACME: host에 설치한 certbot의 **webroot** 방식을 사용한다(certbot 컨테이너는 추가하지 않는다). Nginx `:80`은 `/.well-known/acme-challenge/`를 host webroot 디렉토리(`./data/certbot/`)에서 제공하고, 그 외 요청은 HTTPS로 301 redirect한다. certbot deploy-hook이 발급/갱신된 인증서를 `./data/certs/`에 실제 파일로 복사한 뒤 Nginx를 reload한다.
- 최초 기동: 인증서가 없으면 Nginx가 기동하지 않으므로, 자체서명 placeholder 인증서로 먼저 기동한 뒤 webroot 발급으로 교체한다(절차는 OPERATIONS §7, 갱신/deploy-hook은 §8).
- HSTS는 **Nginx가 단독으로 담당**한다: `Strict-Transport-Security: max-age=31536000`, `includeSubDomains` 없음, `preload` 없음. Nginx는 location에 `add_header`가 있으면 상위 `add_header`를 상속하지 않으므로, `Cache-Control`을 설정하는 정적 location에도 HSTS를 함께 선언한다. Spring Security HSTS는 비활성화되어 있다(P15-T1). Nginx는 `:443` server 블록과 4개 정적 location(`/css/`, `/js/`, `/images/`, `/vendor/`)에 `add_header ... always`로 선언해 오류 응답(404/429 등)에도 붙는다. `:80`(301/ACME) 응답에는 HSTS를 두지 않는다.
- 도메인 없이 Phase 15에서 검증하는 범위: 443/redirect/ACME location/HSTS/rate limit/forward header 구조와 로컬 자체서명 인증서 smoke. 실제 도메인 DNS, Let's Encrypt 발급, 최종 host 확인, 운영 smoke는 배포(Launch) 단계에서 수행한다.
- `server_tokens off`로 Nginx 버전 노출을 끈다(P15-T3). `Server: nginx` header와 Nginx 기본 오류 본문에 버전이 없다.
- TLS protocol/cipher는 Nginx 기본값을 사용한다. `:80`의 301은 `https://$host$request_uri`로 보내므로 표준 포트(443)를 전제한다(로컬 override처럼 host 포트를 바꾼 환경에서는 redirect URL에 원래 포트가 붙지 않는다).
- 로컬 검증(개발 전용, 운영 절차 아님): `./data/certs/`에 `localhost`/`127.0.0.1` SAN을 가진 자체서명 인증서를 고정 파일명으로 생성한다 - 예: `openssl req -x509 -newkey rsa:2048 -nodes -days 365 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" -keyout data/certs/privkey.pem -out data/certs/fullchain.pem`(`./data/certbot/`은 빈 디렉토리로 둔다). 사용자 소유 local override(`docker-compose.local-test.yml`)는 host `8088:80`/`8443:443`을 사용한다. Playwright는 `PLAYWRIGHT_BASE_URL=https://localhost:8443`으로 실행하며 `playwright.config.js`와 spec이 직접 만드는 context에 `ignoreHTTPSErrors: true`를 둔다. 관리자 로그인이 rate limit에 걸리면 E2E 공통 helper(`frontend-tests/support/admin-login.js`)가 429일 때만 13초(bucket 1건 회복 12초 + 여유 1초, `RATE_LIMIT_RETRY_WAIT_MS = 13000`)를 기다려 재시도하므로(운영 계약값은 완화하지 않음), 전체 실행은 로그인 순서가 결정적이도록 `--workers=1`로 한다. `admin-login-rate-limit.spec.js`는 helper를 쓰지 않고 운영 계약값 그대로 429를 검증한다.

## Forwarded header / 쿠키 (CURRENT — P15-T1 완료)

Nginx는 기존대로 `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`를 전달한다. Spring은 `server.forward-headers-strategy: native`, `server.tomcat.use-relative-redirects: true`, `server.servlet.session.cookie.same-site: lax`를 prod profile에 적용하고, 관리자 login redirect는 `SecurityConfig`의 `LoginUrlAuthenticationEntryPoint.setFavorRelativeUris(true)`로 상대 URI(`/admin/login`)를 사용한다(위 Security "쿠키 / HTTPS 인식 / HSTS" 참고). Nginx TLS(`:443`)를 통한 실제 HTTPS 요청 경로도 CURRENT(P15-T3)다.

## 관리자 로그인 rate limit (CURRENT — P15-T3)

- Nginx `limit_req`를 정확히 `location = /api/admin/login`에만 적용한다: `limit_req_zone $binary_remote_addr zone=admin_login:1m rate=5r/m`, `limit_req zone=admin_login burst=5 nodelay`, `limit_req_status 429`.
- Nginx가 인터넷에 직접 노출된 가장 바깥 proxy이므로 `$binary_remote_addr`가 실제 클라이언트 IP다. 앞단에 Cloudflare/로드밸런서를 두게 되면 `real_ip` 설정이 별도로 필요하다(현재 nginx 설정에는 없음 - 도입 시 별도 변경 Task, OPERATIONS §10).
- 제한은 정확히 일치 location 하나에만 걸리며 다른 관리자 API/화면, 공개 화면, 정적 리소스는 제한하지 않는다. 즉시 허용량은 rate 1건 + burst 5건 = 6건이고 이후 12초당 1건씩 회복된다. 429 본문은 Nginx 기본 HTML(버전 비노출)이다.
- 애플리케이션 rate limiter dependency, CAPTCHA, 계정 잠금은 도입하지 않는다. 429 응답과 로그인 화면 처리는 API.md `POST /api/admin/login`을 따른다.

## 데이터 영속성 / volume 이름 / 업로드 경로 (CURRENT — P15-T2 완료, 백업/복원 절차 CURRENT — P15-T8)

- MariaDB named volume은 `volumes.db_data.name: monica-lab-homepage_db_data`로 **이름이 명시 고정**되어 있다. 이 값은 기존 compose project 이름에서 파생되던 실제 volume 이름과 동일하고, Compose가 volume label에 기록하는 설정 hash(`com.docker.compose.config-hash` = 정규화된 `{name, driver: local}`의 sha256)도 이전과 같아 기존 volume에 그대로 연결된다(재생성/이전 불필요). checkout 디렉토리/compose project 이름이 바뀌어도 새 빈 volume이 생성되지 않는다. `external: true`는 사용하지 않는다(신규 서버에서 `docker volume create` 사전 절차가 필요 없도록).
- **`name:`을 지정해도 `docker compose down -v`는 이 volume을 삭제한다**(external이 아닌 선언 volume). `down -v`, volume 삭제, 다른 이름으로의 volume 이전은 금지이며, 이 금지는 compose 파일이 아니라 운영 절차(`docs/OPERATIONS.md` §17 금지 명령)가 담당한다.
- 운영 compose의 app `UPLOAD_ROOT`는 `/app/uploads` **고정값**이다(`.env` 치환 없음). bind mount(`./data/uploads:/app/uploads`) 대상과 저장 경로가 어긋나 컨테이너 내부에 저장되는 실수를 차단한다. `application-prod.yml`의 `${UPLOAD_ROOT:/app/uploads}` override 능력은 Docker 외 실행 호환을 위해 유지하며, test/local profile은 기존 별도 설정(`build/test-uploads`, gitignored local yml)을 유지한다.
- 백업 대상: MariaDB 논리 dump(`mariadb-dump`, volume 파일 직접 복사는 사용하지 않음), `data/uploads`, `.env`, 배포 tag/commit 기록, (선택) TLS 파일. 코드/스크립트로 자동화하지 않고 OPERATIONS 절차(§12 백업, §13 복원/리허설)로 관리한다: 매일 DB/업로드 백업, 서버 내부 단기 보관 + **서버 외부 보관 위치 최소 1곳 필수**, 분기 1회 복원 리허설 권장.

## 환경변수 fail-fast (CURRENT — P15-T2 완료)

- 운영 compose는 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME`, `MARIADB_PASSWORD`, `MARIADB_ROOT_PASSWORD`를 `${VAR:?VAR is required}` 형식으로 참조한다(app의 `DB_PASSWORD`도 같은 형식으로 `MARIADB_PASSWORD`를 참조). 값이 unset이거나 빈 문자열이면 `docker compose config`/`up` 단계에서 즉시 실패한다. 이 5개 외 환경변수는 필수화하지 않았다.
- `.env.example`은 변수 이름과 계약만 보여주는 template이다. 필수 5개는 모두 빈 값이며(`changeme`/정책 통과 예시 비밀번호/기본 관리자 ID·이름 없음), 그대로 복사하면 compose가 실패한다. `UPLOAD_ROOT` 항목은 없다(compose에서 고정). 비밀번호 생성 방법만 주석으로 안내한다.
- `AdminInitializer`의 동작(필수 값이 비어 있으면 계정을 생성하지 않고 ERROR 로그)은 변경하지 않는다. 초기 관리자 생성 이후 `.env`의 `ADMIN_PASSWORD` 변경은 DB에 반영되지 않는다 - 비밀번호 변경은 관리자 화면(`/admin/password`, P15-T6), 분실 시 공식 자동 복구 기능(재설정 화면/API/script)은 없다. OPERATIONS §5에는 관리자 행 삭제 후 `AdminInitializer` 재생성 방식을 공식 절차가 아닌 비상 복구 후보(실행 전 백업·별도 승인/검증 필요)로만 기록하며, 공식화/검증은 Launch TBD다.

## 로그 (CURRENT — P15-T1, P15-T2 완료)

- prod: `spring.jpa.show-sql=false`, `spring.jpa.properties.hibernate.format_sql=false`(CURRENT, P15-T1 완료).
- Docker: `app`/`db`/`nginx` 3개 service 모두 `logging.driver: json-file`, `max-size: "10m"`, `max-file: "3"`(service당 최대 약 30MB, CURRENT — P15-T2 완료). compose top-level extension `x-logging: &default-logging` anchor 1곳에 정의하고 각 service가 `logging: *default-logging`으로 참조한다. 설정은 컨테이너가 (재)생성될 때 적용된다. Nginx 공식 이미지는 access/error log를 stdout/stderr로 symlink하고 MariaDB도 stderr로 출력하므로 세 service 로그 모두 이 rotation 대상이다. 장기 로그 보관/중앙 수집은 범위 밖이다.
- `CustomException` 5xx는 ERROR + stacktrace, 그 외(4xx 등)는 stacktrace 없는 WARN 한 줄(CURRENT, P15-T1 완료). 미처리 예외는 ERROR + stacktrace.

## 배포 단위 / release

- 운영 서버는 mutable 브랜치(develop/main)를 pull하지 않고 **release tag**(예: `v1.0.0`)를 checkout한다. 배포 단위는 "tag checkout → `docker compose up -d --build`"다(위 "Nginx 정적 리소스 공급" 결정). Flyway migration은 app 기동 시 자동 적용된다.
- release/tag/hotfix Git 흐름은 GIT_WORKFLOW.md "develop → main 승격", 서버 명령은 OPERATIONS §6(최초 배포)/§15(배포/rollback)를 따른다.
- Flyway migration은 forward-only다. 파괴적 migration이 포함된 release를 rollback하려면 배포 전 DB dump 복원이 필요하다(Phase 15 자체는 migration 0건).
