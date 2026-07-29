# TASK.md (Harness Engineering 방식)

Version 2.0 — AI 코딩 에이전트 실행용 재구성

---

## 이 문서의 원칙

기존 체크리스트는 "무엇을 만드는가"만 나열되어 있어, 사람이 판단해서 순서·완료 기준을
채워야 했습니다. 이 버전은 에이전트(Claude Code 등)가 사람의 개입 없이
**하나씩 집어서 실행 → 스스로 검증 → 다음으로 이동**할 수 있도록 태스크를 재구성합니다.

각 태스크는 다음 5요소를 모두 갖습니다.

| 요소 | 설명 |
|---|---|
| **ID** | `P{phase}-T{n}` 형식의 고유 식별자. 의존성 참조에 사용 |
| **의존성** | 선행되어야 하는 태스크 ID (없으면 `-`) |
| **산출물** | 생성/수정되는 파일·디렉토리 경로 (에이전트가 diff 범위를 예측 가능하게) |
| **작업 내용** | 실행할 구체적 작업 (모호한 동사 대신 확인 가능한 행위로 기술) |
| **완료 기준(DoD)** | 사람이 아니라 **명령어/코드로 자동 검증** 가능한 조건 |

원본에 있던 `- [ ] 항목명` 형태(사람이 눈으로 체크)는 전부 제거하고,
**실행 가능한 검증 커맨드**로 대체했습니다.

> **docs/AI_WORKFLOW.md와의 관계**: AI_WORKFLOW.md의 표준 개발 절차(요구사항 확인 → 문서 수정 →
> Git Commit → AI 구현 → 개발자 리뷰 → **테스트** → Commit → Merge)는 기능 단위 반복을 원칙으로 한다.
> 본 문서의 각 태스크는 DoD 자체에 해당 기능의 테스트 통과를 포함하므로, 태스크를 완료할 때마다
> 그 자리에서 테스트를 통과시키고 커밋하는 것이 기본 흐름이다. `Phase 10`(P10-T1, P10-T2)은
> 이 기능 단위 테스트를 대체하는 것이 아니라, Phase 1~9 전체에 대한 회귀 테스트와
> ErrorCode 전수 테스트를 추가로 보강하는 단계다.

---

## Phase 1. 프로젝트 환경 구성

### P1-T1. Spring Boot 프로젝트 생성 및 Gradle 설정
- 의존성: `-`
- 산출물: `build.gradle`, `settings.gradle`, `src/main/java/**/Application.java`
- 작업 내용: Spring Boot 3.x + Java 21 기준 프로젝트 생성. `group`, `version`, `sourceCompatibility` 명시.
- DoD: `./gradlew build` 종료 코드 `0`, `./gradlew bootRun` 후 기본 포트(8080) 응답 200 또는 404(라우트 없음은 정상)

### P1-T2. Git Repository 연결
- 의존성: P1-T1
- 산출물: `.git/`, `.gitignore`
- 작업 내용: `.gitignore`에 `build/`, `.gradle/`, `*.log`, `application-local.yml` 등 포함
- DoD: `git status`에 빌드 산출물이 잡히지 않음

### P1-T3. application.yml 프로파일 분리
- 의존성: P1-T1
- 산출물: `src/main/resources/application.yml`, `application-local.yml`, `application-prod.yml`
- 작업 내용: `spring.profiles.active`로 local/prod 분리, DB 접속정보는 환경변수 참조(`${DB_URL}` 등)
- DoD: `local` 프로파일로 `bootRun` 시 정상 기동, 시크릿 값이 git에 커밋되지 않음(grep으로 확인)

### P1-T4. MariaDB 연결
- 의존성: P1-T3
- 산출물: `application-local.yml` (datasource 설정), `docker-compose.local.yml`(선택, 로컬 DB 전용 — 배포용 `docker-compose.yml`은 P12-T1에서 별도 생성)
- 작업 내용: JDBC URL, driver-class-name(`org.mariadb.jdbc.Driver`) 설정
- DoD: 애플리케이션 기동 로그에 `HikariPool-1 - Start completed` 확인, 실패 시 종료코드 non-zero

### P1-T5. 라이브러리 의존성 추가
- 의존성: P1-T1
- 산출물: `build.gradle`
- 작업 내용: Spring Security, Spring Data JPA, QueryDSL(Q타입 생성 플러그인 포함), Validation, Lombok, Thymeleaf, Jacoco(테스트 커버리지 플러그인, P10-T1 DoD 대비) 추가. Bootstrap/CKEditor5는 정적 리소스(CDN 또는 `src/main/resources/static/vendor`)로 처리
- DoD: `./gradlew compileJava` 성공, QueryDSL Q클래스가 `build/generated`에 생성됨, `./gradlew jacocoTestReport` 태스크가 정상 등록되어 실행 가능

---

## Phase 2. 공통 기능

### P2-T1. BaseEntity
- 의존성: P1-T5
- 산출물: `common/entity/BaseEntity.java`
- 작업 내용: `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`, `createdAt`, `updatedAt`
- DoD: 단위 테스트에서 임의 엔티티 저장 시 `createdAt`이 null이 아님

### P2-T2. ApiResponse / ErrorCode / GlobalExceptionHandler
- 의존성: P1-T5
- 산출물: `common/response/ApiResponse.java`, `common/exception/ErrorCode.java`, `common/exception/GlobalExceptionHandler.java`
- 작업 내용: 성공/실패 응답 포맷 통일, `@RestControllerAdvice`로 예외 처리
- DoD: 존재하지 않는 엔드포인트 호출 시 정의된 JSON 에러 포맷 반환(테스트 코드로 검증)

### P2-T3. CommonConfig
- 의존성: P2-T1
- 산출물: `common/config/CommonConfig.java`
- 작업 내용: `JpaAuditing`, `Bean Validation` 등 공통 빈 등록
- DoD: 컨텍스트 로딩 테스트(`@SpringBootTest`) 통과

### P2-T4. File 도메인 + 파일 업로드 (Local Storage)
- 의존성: P2-T1, P2-T2
- 산출물: `file/entity/File.java`, `file/repository/FileRepository.java`, `file/service/FileService.java`, `file/controller/FileController.java`, `file/controller/AdminFileController.java`
- 작업 내용: ERD.md File 테이블 기준 Entity 생성(BaseEntity 상속). UUID 파일명 생성, `yyyy/MM/dd` 날짜별 디렉토리 자동 생성, 이미지/첨부파일 확장자 화이트리스트 검증(CODING_RULES.md ALLOWED_IMAGE_EXTENSIONS/ALLOWED_ATTACHMENT_EXTENSIONS 기준), 파일 크기 검증(MAX_UPLOAD_SIZE=10MB, MAX_IMAGE_SIZE=5MB). 업로드 시 File 레코드 저장 후 id 반환(`POST /api/admin/files`), `GET /api/files/{id}`로 다운로드, `DELETE /api/admin/files/{id}`로 레코드 및 실파일 삭제(API.md 기준)
- DoD: 업로드 후 File 레코드가 DB에 저장되고 파일 시스템에 `업로드루트/yyyy/MM/dd/{uuid}.{ext}` 경로 존재 확인, 화이트리스트 외 확장자는 `INVALID_FILE_TYPE`(400), 용량 초과 시 `FILE_SIZE_EXCEEDED`(400), `GET /api/files/{id}` 200 + 파일 스트림 반환, `DELETE` 후 재조회 시 404

### P2-T5. HtmlSanitizer 공통 유틸 (XSS 방지)
- 의존성: P1-T5
- 산출물: `common/util/HtmlSanitizer.java`
- 작업 내용: ARCHITECTURE.md "XSS 방지 정책" 기준. jsoup `Safelist` 또는 OWASP Java HTML Sanitizer 사용. 허용 태그는 `p`,`br`,`strong`,`em`,`u`,`h1~h6`,`table`,`tr`,`td`,`th`,`a[href]`,`img[src]`로 한정하고, `script`,`iframe`,`on*` 이벤트 속성, `javascript:` 스킴 링크는 모두 제거한다. Page/Program/Board/Popup Service(P4-T1, P5-T2, P6-T2, P7-T2)의 등록/수정 로직에서 이 유틸을 공통 호출한다.
- DoD: 단위 테스트에서 `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>`, `<a href="javascript:alert(1)">` 입력이 각각 sanitize 후 스크립트/이벤트 속성/스킴이 제거된 채로 반환됨을 검증. 허용 태그(`p`,`table` 등)는 그대로 보존됨을 검증

---

## Phase 3. 관리자 인증

### P3-T1. Admin 도메인 (Entity/Repository/Service/Controller) + 초기 계정
- 의존성: P2-T1
- 산출물: `admin/entity/Admin.java`, `admin/repository/AdminRepository.java`, `admin/service/AdminService.java`, `admin/controller/AdminController.java`, `resources/data.sql`(또는 `ApplicationRunner` 기반 초기 계정 생성)
- 작업 내용: `login_id`(unique), `password`(BCrypt 해시 저장), `name`, `role`(ERD.md 기준). 비밀번호는 CODING_RULES.md 정책(최소 8자, 영문/숫자/특수문자 중 2종 이상) 충족 값으로 초기 계정 1건 생성(회원가입 화면 없음, PRD.md 원칙 준수)
- DoD: Repository 통합 테스트에서 중복 login_id 저장 시 `DataIntegrityViolationException`, 애플리케이션 기동 후 초기 관리자 계정으로 로그인 가능

### P3-T2. SecurityConfig + BCrypt + CSRF
- 의존성: P3-T1
- 산출물: `config/SecurityConfig.java`
- 작업 내용: `PasswordEncoder` = `BCryptPasswordEncoder`, `SecurityFilterChain` 정의. `/admin/login`(GET, 로그인 화면)과 `POST /api/admin/login`은 인증 대상에서 제외(permitAll)하고, 그 외 `/admin/**`, `/api/admin/**`는 세션 기반 ROLE_ADMIN 인증을 요구한다(ARCHITECTURE.md Security 섹션 기준). ARCHITECTURE.md "CSRF 정책" 기준 `CookieCsrfTokenRepository.withHttpOnlyFalse()`를 적용해 `XSRF-TOKEN` 쿠키를 발급하고, `POST /api/admin/login`은 CSRF 토큰 없이도 호출 가능하도록 예외 처리한다.
- DoD: 평문 비밀번호로 로그인 시도 시 실패, BCrypt 해시 비교 성공 시 인증 통과 (테스트 코드). 미인증 상태로 `GET /admin/login` 접근 시 200(리다이렉트 루프 없음). CSRF 토큰 없이 `POST /api/admin/programs` 호출 시 403, `POST /api/admin/login`은 CSRF 토큰 없이도 200/401 정상 응답.

### P3-T3. 로그인/로그아웃 구현
- 의존성: P3-T2
- 산출물: `admin/controller/AdminViewController.java`(GET `/admin/login` 화면만 렌더링), `admin/controller/AdminAuthController.java`(API.md 기준 `POST /api/admin/login`, `POST /api/admin/logout`), `templates/admin/login.html`
- 작업 내용: `/admin/login`은 로그인 폼 화면(GET)만 제공하고, 폼 제출은 JS(fetch)로 API.md의 `POST /api/admin/login`을 호출한다. 인증 성공 시 세션 생성 후 `ApiResponse.success` 반환, 프론트엔드에서 `/admin/dashboard`로 이동한다. 로그아웃은 `POST /api/admin/logout` 호출 후 세션 무효화, 프론트엔드에서 `/admin/login`으로 이동한다. `/admin/**` 화면 경로와 `/api/admin/**` API 경로는 ARCHITECTURE.md 기준 동일한 세션 인증(ROLE_ADMIN)을 공유한다.
- DoD: `POST /api/admin/login` 성공 시 200 + `ApiResponse.success` + 세션 쿠키 발급, 실패 시 401 + `ApiResponse.fail`. `POST /api/admin/logout` 후 인증 필요 API(`/api/admin/**`) 호출 시 401. 미인증 상태로 `/admin/dashboard` 접근 시 302로 `/admin/login` 리다이렉트

### P3-T4. 인증 실패 처리 및 접근 권한 설정
- 의존성: P3-T3
- 산출물: `config/SecurityConfig.java`(수정), `common/exception/CustomAuthenticationEntryPoint.java`
- 작업 내용: 인증 실패 시 커스텀 에러 응답, `/admin/**` 인가 규칙
- DoD: 미인증 상태로 `/admin/**` 접근 시 401 또는 로그인 페이지 리다이렉트 (통합 테스트)

### P3-T5. 관리자 공통 fetch 유틸 (CSRF 헤더 자동 첨부)
- 의존성: P3-T2, P3-T3
- 산출물: `static/js/admin/common-fetch.js`
- 작업 내용: ARCHITECTURE.md "CSRF 정책" 기준. `/admin/**` 화면의 모든 JS는 이 공통 fetch 유틸을 통해서만 `/api/admin/**`을 호출한다. 유틸은 `XSRF-TOKEN` 쿠키 값을 읽어 요청 헤더 `X-XSRF-TOKEN`에 자동으로 담아 전송한다. 이후 모든 관리자 화면(Page/Program/Board/Banner/Popup/File) Task는 이 유틸을 재사용한다.
- DoD: 이 유틸을 통해 보낸 `POST/PUT/PATCH/DELETE` 요청 헤더에 `X-XSRF-TOKEN`이 자동 포함됨을 브라우저 테스트 또는 JS 단위 테스트로 검증

---

## Phase 4. CMS 페이지 관리 (기관소개)

### P4-T1. Page Entity/CRUD
- 의존성: P2-T1, P2-T2, P2-T5, P3-T4
- 산출물: `page/entity/Page.java`, `page/repository/PageRepository.java`, `page/service/PageService.java`, `page/controller/AdminPageController.java`, `page/dto/PageRequest.java`, `page/dto/PageResponse.java`
- 작업 내용: 페이지 타입(`GREETING`, `INTRODUCTION`, `HISTORY`, `LOCATION`)을 enum으로 관리(ERD.md 기준). 타입별 단일 레코드 정책 사용(페이지당 최신 1건만 유지). `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다. 공개 조회 Controller(`PageController`)는 이 Task에서 만들지 않고 P4-T3에서 별도로 생성한다(공개 API/화면과 관리자 CRUD의 책임 분리).
- DoD: 4개 타입 각각에 대해 생성→조회→수정→삭제 통합 테스트 통과. `<script>` 포함 content 저장 시 정제되어 저장됨을 검증

### P4-T2. CKEditor5 적용 및 이미지 업로드 연동
- 의존성: P4-T1, P2-T4
- 산출물: `templates/admin/page/form.html`, `page/controller/AdminPageImageUploadController.java`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결
- DoD: 에디터에서 이미지 업로드 시 반환 JSON에 `url` 필드 포함, 실제 파일이 스토리지에 존재

### P4-T3. 공개 Page 조회 Controller (API + 화면)
- 의존성: P4-T1
- 산출물: `page/controller/PageController.java`(API.md 기준 `GET /api/pages/{pageType}`, `ApiResponse` 반환), `page/controller/PageViewController.java`(ARCHITECTURE.md URL 섹션 기준 `GET /pages/{type}`, Thymeleaf 뷰 반환)
- 작업 내용: 두 경로 모두 인증 없이 접근 가능하도록 Security 설정을 확인한다(ARCHITECTURE.md "비로그인 접근" 대상). API Controller와 View Controller는 별도 클래스로 분리하여 JSON 응답과 화면 렌더링 책임을 섞지 않는다(P3-T3의 AdminViewController/AdminAuthController 분리 패턴과 동일).
- DoD: 인증 없이 `GET /api/pages/{pageType}` 200 + `ApiResponse.success`, `GET /pages/{type}` 200 + HTML 응답 (4개 타입 각각 검증)

---

## Phase 5. 프로그램 관리

### P5-T1. Program 도메인
- 의존성: P2-T1, P2-T2, P3-T4
- 산출물: `program/entity/Program.java`, `program/repository/ProgramRepository.java`, `program/service/ProgramService.java`, `program/controller/AdminProgramController.java`, `program/dto/ProgramRequest.java`, `program/dto/ProgramResponse.java`
- 작업 내용: `ProgramType` enum(`COURSE`, `SPECIAL`), `isPublic`, `recruitStatus` 필드. 공개 조회 Controller(`ProgramController`)는 이 Task에서 만들지 않고 P5-T5에서 별도로 생성한다(공개 API/화면과 관리자 CRUD의 책임 분리).
- DoD: 타입별 필터 조회 리포지토리 테스트 통과

### P5-T2. 프로그램 CRUD + 공개여부/모집상태
- 의존성: P5-T1, P2-T5
- 산출물: `program/controller/AdminProgramController.java`(수정)
- 작업 내용: 등록/수정/삭제, `isPublic=false`인 프로그램은 공개 API에서 제외. `recruitStatus`는 자동 전환 없이 `PATCH /api/admin/programs/{id}/status`를 통한 관리자 수동 변경만 지원(ERD.md 기준). `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다.
- DoD: 비공개 프로그램이 공개 목록 API 응답에 포함되지 않음(테스트), `PATCH .../status` 호출 없이는 recruitStatus가 변경되지 않음(테스트), `<script>` 포함 content 저장 시 정제되어 저장됨을 검증

### P5-T3. Google Form URL 관리
- 의존성: P5-T1
- 산출물: `program/entity/Program.java`(필드 추가), 관리 폼
- 작업 내용: URL 형식 검증(`@URL` 또는 정규식)
- DoD: 잘못된 URL 형식 입력 시 Validation 에러 응답(400)

### P5-T4. 썸네일/첨부파일 업로드
- 의존성: P5-T1, P2-T4
- 산출물: `program/controller/AdminProgramFileController.java`
- 작업 내용: 썸네일은 이미지 전용, 첨부는 문서 확장자 허용
- DoD: 이미지 아닌 파일을 썸네일로 업로드 시 400

### P5-T4A. Program 검색(QueryDSL)
- 의존성: P5-T1
- 산출물: `program/repository/ProgramRepositoryCustom.java`(QueryDSL), `program/dto/ProgramSearchCondition.java`
- 작업 내용: API.md `GET /api/programs`의 `keyword`(제목/내용 대상) 쿼리 파라미터를 QueryDSL 동적 조건으로 구현한다. `programType` 필터와 동시 조합 가능해야 하며(BooleanBuilder 또는 BooleanExpression 조합), `Pageable`을 지원한다. Board의 `BoardRepositoryCustom`(P6-T3)과 동일한 패턴을 재사용한다.
- DoD: `keyword`만 적용, `programType`만 적용, 둘을 동시 적용한 3가지 조건 각각에 대해 검색 결과가 조건에 맞는 항목만 반환되는지 QueryDSL 테스트로 검증

### P5-T5. 공개 Program 조회 Controller (API + 화면)
- 의존성: P5-T2, P5-T3, P5-T4A
- 산출물: `program/controller/ProgramController.java`(API.md 기준 `GET /api/programs`, `GET /api/programs/{id}`), `program/controller/ProgramViewController.java`(ARCHITECTURE.md URL 섹션 기준 `GET /programs`, `GET /programs/{id}`, Thymeleaf 뷰 반환)
- 작업 내용: `isPublic=false`인 프로그램은 목록/상세 모두 404 처리. `programType`, `keyword`(P5-T4A 기준) 쿼리 파라미터로 필터링(API.md Query 기준)
- DoD: 인증 없이 목록/상세 조회 200, 비공개 프로그램 상세 조회 시 404, `programType` 필터 적용 시 해당 타입만 반환, `keyword` 검색 시 제목/내용에 일치하는 항목만 반환

### P5-T6. Program CKEditor5 이미지 업로드 연동
- 의존성: P5-T2, P2-T4
- 산출물: `templates/admin/program/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결(P4-T2와 동일한 연동 방식 재사용)
- DoD: 에디터에서 이미지 업로드 시 반환 JSON에 `url` 필드 포함, 실제 파일이 스토리지에 존재

---

## Phase 6. 게시판 관리

### P6-T1. Board 도메인 + BoardType
- 의존성: P2-T1, P2-T2, P3-T4
- 산출물: `board/entity/Board.java`, `board/entity/BoardType.java`(enum: `NOTICE`,`GALLERY`,`ARCHIVE`), Repository/Service/Controller, `board/dto/BoardRequest.java`, `board/dto/BoardResponse.java`
- DoD: 타입별 저장/조회 통합 테스트 통과

### P6-T2. CRUD (목록/상세/등록/수정/삭제)
- 의존성: P6-T1, P2-T5
- 산출물: `board/controller/AdminBoardController.java`, `board/controller/BoardController.java`(API.md 기준 공개 API), `board/controller/BoardViewController.java`(ARCHITECTURE.md URL 섹션 기준 `GET /boards`, `GET /boards/{id}` 화면)
- 작업 내용: `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다. `BoardController`(API)와 `BoardViewController`(화면)는 책임을 분리한다.
- DoD: 각 CRUD 엔드포인트에 대한 통합 테스트 5종 통과, `<script>` 포함 content 저장 시 정제되어 저장됨을 검증, `GET /boards`, `GET /boards/{id}` 화면 200 응답

### P6-T3. 검색/페이징
- 의존성: P6-T2
- 산출물: `board/repository/BoardRepositoryCustom.java`(QueryDSL), `board/dto/BoardSearchCondition.java`
- 작업 내용: 제목/내용 검색, `Pageable` 지원
- DoD: 검색 조건에 맞는 결과만 반환되는지 QueryDSL 테스트로 검증

### P6-T4. 조회수, 공개여부, 이미지/파일 업로드
- 의존성: P6-T2, P2-T4
- 산출물: `board/entity/Board.java`(필드 추가), `board/service/BoardService.java`
- 작업 내용: 상세 조회 시 조회수 증가(동시성 고려: `@Query` UPDATE 또는 낙관적 락). (content sanitize는 P6-T2에서 이미 처리하므로 이 Task는 중복 적용하지 않는다)
- DoD: 동시 요청 100회 시 조회수 정확히 100 증가(부하 테스트 또는 동시성 단위 테스트)

### P6-T5. Board CKEditor5 이미지 업로드 연동
- 의존성: P6-T2, P2-T4
- 산출물: `templates/admin/board/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결(P4-T2와 동일한 연동 방식 재사용). 갤러리(`GALLERY`)는 대표 이미지 업로드와 별도 항목이므로 혼동하지 않는다.
- DoD: 에디터에서 이미지 업로드 시 반환 JSON에 `url` 필드 포함, 실제 파일이 스토리지에 존재

---

## Phase 7. 메인 관리

### P7-T1. Banner CRUD + 정렬 + 공개여부
- 의존성: P2-T1, P2-T2, P2-T4, P3-T4
- 산출물: `banner/entity/Banner.java`, `BannerRepository`, `BannerService`, `BannerController`, `AdminBannerController`(ARCHITECTURE.md 명명 기준), `banner/dto/BannerRequest.java`, `banner/dto/BannerResponse.java`
- 작업 내용: `sortOrder` 필드, 관리자 화면에서 순서 변경 API
- DoD: 정렬 변경 API 호출 후 조회 순서가 반영됨(테스트)

### P7-T2. Popup CRUD + 기간설정 + 공개여부
- 의존성: P2-T1, P2-T2, P2-T5, P3-T4
- 산출물: `popup/entity/Popup.java`, `PopupRepository`, `PopupService`, `PopupController`, `AdminPopupController`(ARCHITECTURE.md 명명 기준), `popup/dto/PopupRequest.java`, `popup/dto/PopupResponse.java`
- 작업 내용: `startDate`, `endDate` 범위 밖이면 공개 API에서 자동 제외. `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다.
- DoD: 기간이 지난 팝업이 공개 목록에 나타나지 않음(날짜 조작 테스트), `<script>` 포함 content 저장 시 정제되어 저장됨을 검증

### P7-T3. Popup CKEditor5 이미지 업로드 연동
- 의존성: P7-T2, P2-T4
- 산출물: `templates/admin/popup/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결(P4-T2와 동일한 연동 방식 재사용)
- DoD: 에디터에서 이미지 업로드 시 반환 JSON에 `url` 필드 포함, 실제 파일이 스토리지에 존재

---

## Phase 8. 홈페이지 (공개 영역)

### P8-T1. 메인 화면 (배너/팝업/기관소개 요약/최신 게시글)
- 의존성: P5-T2, P7-T1, P7-T2, P6-T2, P4-T3
- 산출물: `home/controller/HomeController.java`, `templates/home/index.html`
- 작업 내용: ARCHITECTURE.md Home 기능 목록 기준. 배너/팝업/최신 공지·갤러리/프로그램 바로가기에 더해, PageService(P4-T1)를 조합하여 기관소개 인사말(`GREETING`) 요약을 메인 화면에 노출한다.
- DoD: `GET /` 200 응답, 응답 HTML에 배너/팝업/인사말 요약/최신글/프로그램 바로가기 영역 태그 존재(HTML 파싱 테스트)

### P8-T2. 기관소개 페이지 조회
- 의존성: P4-T3
- 산출물: `templates/home/page/*.html`(`PageViewController`가 렌더링, P4-T3 기준)
- DoD: 4개 페이지 타입 각각 `GET /pages/{type}` 200 응답

### P8-T3. 프로그램 목록/상세 + Google Form 이동
- 의존성: P5-T5
- 산출물: `templates/home/program/*.html`(`ProgramViewController`가 렌더링, P5-T5 기준)
- DoD: 상세 페이지의 "신청하기" 링크 `href`가 등록된 Google Form URL과 일치

### P8-T4. 게시판 (공지/갤러리/자료실) 공개 화면
- 의존성: P6-T3
- 산출물: `templates/home/board/*.html`
- DoD: 비공개 게시글이 목록/상세에서 접근 불가(403 또는 404)

---

## Phase 9. 관리자 CMS

### P9-T1. Dashboard
- 의존성: P6-T2, P5-T2, P3-T5
- 산출물: `admin/controller/DashboardController.java`(API, `GET /api/admin/dashboard`), `admin/controller/AdminViewController.java`(수정, `GET /admin/dashboard` 화면 렌더링 추가), `templates/admin/dashboard.html`
- 작업 내용: 최근 게시글 N건, 프로그램 현황(모집중/마감 카운트), 빠른 메뉴 링크. ARCHITECTURE.md "Admin 화면(View) / API 컨트롤러 명명 규칙" 기준, `DashboardController`는 JSON 데이터만 반환하고 화면 렌더링은 P3-T3에서 생성된 `AdminViewController`가 담당한다(로그인 화면과 동일 클래스). `dashboard.html`은 P3-T5 공통 fetch 유틸로 `GET /api/admin/dashboard`를 호출해 데이터를 채운다.
- DoD: `GET /api/admin/dashboard` 응답에 세 영역의 데이터가 모두 포함됨. 미인증 상태로 `GET /admin/dashboard` 접근 시 302 리다이렉트(인증 후 200)

### P9-T2a. 관리자 공통 레이아웃
- 의존성: P3-T4, P3-T5
- 산출물: `templates/admin/layout/*.html`(공통 헤더/사이드바/푸터 레이아웃, 각 도메인 화면이 상속)
- 작업 내용: P3-T5 common-fetch.js를 사용하는 공통 레이아웃 뼈대 생성. 이후 P9-T2b~T2g는 이 레이아웃을 상속만 한다.
- DoD: 레이아웃 템플릿이 하위 도메인 화면 1개 이상에서 정상 렌더링됨(통합 테스트)

### P9-T2b. Program 관리자 화면
- 의존성: P9-T2a, P5-T2, P5-T3, P5-T4, P5-T4A, P5-T6
- 산출물: `program/controller/AdminProgramViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/programs`, `/admin/programs/new`, `/admin/programs/{id}/edit` 화면 렌더링), `templates/admin/program/list.html`, `templates/admin/program/form.html`(P5-T6 기존 form.html과 통합)
- 작업 내용: `AdminProgramViewController`는 화면 렌더링만 담당하며, 실제 CRUD/검색은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminProgramController`(P5-T2)의 API를 호출해 처리한다.
- DoD: `/admin/programs`, `/admin/programs/new`, `/admin/programs/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2c. Board 관리자 화면
- 의존성: P9-T2a, P6-T2, P6-T3, P6-T5
- 산출물: `board/controller/AdminBoardViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/boards`, `/admin/boards/new`, `/admin/boards/{id}/edit` 화면 렌더링), `templates/admin/board/list.html`, `templates/admin/board/form.html`(P6-T5 기존 form.html과 통합)
- 작업 내용: `AdminBoardViewController`는 화면 렌더링만 담당하며, 실제 CRUD/검색은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminBoardController`(P6-T2)의 API를 호출해 처리한다.
- DoD: `/admin/boards`, `/admin/boards/new`, `/admin/boards/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2d. Page 관리자 화면
- 의존성: P9-T2a, P4-T1, P4-T2
- 산출물: `page/controller/AdminPageViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/pages`, `/admin/pages/{pageType}/edit` 화면 렌더링), `templates/admin/page/list.html`(P4-T2 기존 form.html과 통합)
- 작업 내용: `AdminPageViewController`는 화면 렌더링만 담당하며, 실제 조회/수정은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminPageController`(P4-T1)의 API를 호출해 처리한다. Page는 타입별 단일 레코드이므로 `/new` 라우트는 두지 않는다.
- DoD: `/admin/pages`, `/admin/pages/{pageType}/edit`(4개 타입) 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2e. Banner 관리자 화면
- 의존성: P9-T2a, P7-T1
- 산출물: `banner/controller/AdminBannerViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/banners`, `/admin/banners/new`, `/admin/banners/{id}/edit` 화면 렌더링), `templates/admin/banner/list.html`, `templates/admin/banner/form.html`
- 작업 내용: `AdminBannerViewController`는 화면 렌더링만 담당하며, 실제 CRUD/정렬은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminBannerController`(P7-T1)의 API를 호출해 처리한다.
- DoD: `/admin/banners`, `/admin/banners/new`, `/admin/banners/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2f. Popup 관리자 화면
- 의존성: P9-T2a, P7-T2, P7-T3
- 산출물: `popup/controller/AdminPopupViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/popups`, `/admin/popups/new`, `/admin/popups/{id}/edit` 화면 렌더링), `templates/admin/popup/list.html`(P7-T3 기존 form.html과 통합)
- 작업 내용: `AdminPopupViewController`는 화면 렌더링만 담당하며, 실제 CRUD는 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminPopupController`(P7-T2)의 API를 호출해 처리한다.
- DoD: `/admin/popups`, `/admin/popups/new`, `/admin/popups/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2g. File 관리자 화면
- 의존성: P9-T2a, P2-T4
- 산출물: `file/controller/AdminFileViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/files` 화면 렌더링), `templates/admin/file/list.html`(ARCHITECTURE.md Admin URL `/admin/files` 기준, 업로드 이력 조회 및 삭제)
- 작업 내용: `AdminFileViewController`는 화면 렌더링만 담당하며, 목록 데이터 조회 및 삭제는 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminFileController`(P2-T4)의 API를 호출해 처리한다.
- DoD: `/admin/files` 목록 화면 200 응답, 삭제 버튼 클릭 시 `DELETE /api/admin/files/{id}` 호출 후 목록 갱신, 인가되지 않은 사용자는 401/302로 차단

---

## Phase 10. 테스트

### P10-T1. 기능 테스트 스위트
- 의존성: Phase 1~9 전체
- 산출물: `src/test/java/**` (로그인, 권한, CRUD, 검색, 파일 업로드, Google Form 연결)
- DoD: `./gradlew test` 전체 통과, 커버리지 리포트 생성(jacoco 등)

### P10-T2. 예외 처리 테스트
- 의존성: P10-T1
- 산출물: `src/test/java/**exception**`
- 작업 내용: Validation 실패, 인증 실패, 파일 오류, 잘못된 요청 각각에 대한 케이스
- DoD: 정의된 `ErrorCode` 별로 최소 1개 이상의 테스트 존재, 전부 통과

---

## Phase 11. UI/UX 개선

### P11-T1. 반응형 적용
- 의존성: P8 전체
- 산출물: `static/css/**`
- DoD: Playwright(또는 Puppeteer) 기반 뷰포트별(375px/768px/1440px) 스크린샷을 자동 캡처하여 기준 이미지 대비 픽셀 diff 비율이 임계치(예: 2%) 이하

### P11-T2. 관리자 UI 개선 + CKEditor 스타일 + 이미지 최적화 + 접근성
- 의존성: P9-T2a, P9-T2b, P9-T2c, P9-T2d, P9-T2e, P9-T2f, P9-T2g
- 산출물: `static/css/admin/**`
- DoD: Lighthouse CI 접근성 점수 90 이상(고정 임계치), 이미지 `loading="lazy"` 속성 적용 여부를 HTML 파싱으로 확인

---

## Phase 12. 배포

### P12-T1. Dockerfile / docker-compose
- 의존성: P10-T1 통과
- 산출물: `Dockerfile`, `docker-compose.yml`
- DoD: `docker build` 성공, `docker compose up` 후 헬스체크 엔드포인트 200

### P12-T2. Nginx (Reverse Proxy + Static Resource)
- 의존성: P12-T1
- 산출물: `nginx/nginx.conf`
- DoD: 80번 포트를 통해 애플리케이션 접근 가능, 정적 리소스는 Nginx가 직접 서빙(응답 헤더로 확인)

### P12-T3. GitHub Actions CI/CD
- 의존성: P12-T1
- 산출물: `.github/workflows/ci.yml`, `.github/workflows/cd.yml`
- DoD: PR 생성 시 CI 워크플로우 자동 실행 및 `./gradlew test` 결과가 PR 상태 체크에 반영

---

# 완료 기준 (Definition of Done) — 자동 검증 가능한 형태로 재기술

| 항목 | 기존 표현 | 자동 검증 방법 |
|---|---|---|
| 관리자 로그인 가능 | 관리자 로그인 가능 | P3-T3 통합 테스트 통과 |
| 기관소개 CMS 수정 가능 | 기관소개 CMS 수정 가능 | P4-T1 CRUD 테스트 통과 |
| 프로그램 관리 가능 | 프로그램 관리 가능 | P5-T2 테스트 통과 |
| 게시판 관리 가능 | 게시판 관리 가능 | P6-T2, P6-T3 테스트 통과 |
| 배너 관리 가능 | 배너 관리 가능 | P7-T1 테스트 통과 |
| 팝업 관리 가능 | 팝업 관리 가능 | P7-T2 테스트 통과 |
| Google Form 정상 연결 | Google Form 정상 연결 | P8-T3 링크 검증 테스트 통과 |
| 파일 업로드 정상 동작 | 파일 업로드 정상 동작 | P2-T4 테스트 통과 |
| XSS 방지 적용 완료 | XSS 방지 | P2-T5 단위 테스트 + P4-T1/P5-T2/P6-T2/P7-T2 sanitize 검증 통과 |
| CSRF 보호 적용 완료 | CSRF 보호 | P3-T2, P3-T5 테스트 통과 |
| 예외 처리 완료 | 예외 처리 완료 | P10-T2 통과 |
| 반응형 적용 | 반응형 적용 | P11-T1 확인 |
| 권한 검증 완료 | 권한 검증 완료 | P3-T4, P9-T2a~T2g 통과 |
| 코드 리뷰 완료 | 코드 리뷰 완료 | PR approve 기록 (GitHub) |
| 테스트 완료 | 테스트 완료 | `./gradlew test` 성공 + 커버리지 리포트 |
| 배포 완료 | 배포 완료 | P12-T1~T3 통과, 헬스체크 200 |

---

## 실행 순서 요약 (의존성 그래프 기준 위상정렬)

```
                           ┌→ Phase4 ─┐
                           ├→ Phase5 ─┤
Phase1 → Phase2 → Phase3 ─┼→ Phase6 ─┼→ Phase8 → Phase9 → Phase10 → Phase11 → Phase12
                           └→ Phase7 ─┘
```

> 주: Phase3(관리자 인증)은 Phase4/5/6/7의 관리자용(Admin) 태스크 전체의 선행 조건이다.
> P5-T1·P6-T1·P7-T1·P7-T2의 `의존성`에는 P3-T4(관리자 인가 완료)가 명시되어 있다.
> Phase4(Page)·Phase5(Program)·Phase6(Board)·Phase7(Banner/Popup)는 서로 직접적인 선행 관계가
> 없으므로 Phase3 완료 후 **병렬로 진행 가능**하다(각 Phase의 첫 태스크가 P3-T4에만 의존).
> 네 Phase가 모두 수렴하는 지점은 Phase8(홈페이지)이며, P8-T1은 P4-T3·P5-T5·P6-T2·P7-T1·P7-T2를
> 모두 필요로 한다. 위 다이어그램은 각 태스크의 `의존성` 필드와 항상 동기화되어야 한다.

에이전트는 각 Phase 내 태스크를 ID 순서대로 실행하되, `의존성` 필드에 명시된
태스크가 완료(DoD 통과)되지 않으면 다음 태스크로 진행하지 않습니다.