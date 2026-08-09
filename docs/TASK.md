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
| **ID** | `P{phase}-T{n}` 형식의 고유 식별자. 의존성 참조에 사용. 한 태스크에서 파생된 세부/병렬 태스크는 `P{phase}-T{n}{알파벳}`(예: `P5-T4A`, `P9-T2a`)으로 확장할 수 있다 |
| **의존성** | 선행되어야 하는 태스크 ID (없으면 `-`) |
| **산출물** | 생성/수정되는 파일·디렉토리 경로 (에이전트가 diff 범위를 예측 가능하게) |
| **작업 내용** | 실행할 구체적 작업 (모호한 동사 대신 확인 가능한 행위로 기술) |
| **완료 기준(DoD)** | 사람이 아니라 **명령어/코드로 자동 검증** 가능한 조건 |

> **PK 공통 계약**: ERD.md 기준 모든 Entity의 `id`는 JPA에서 `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용한다. DB는 Flyway에서 `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY`로 생성하며 애플리케이션이 `id`를 직접 할당하지 않는다.

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
- 작업 내용: local/prod 프로파일을 분리하고 DB 접속정보는 환경변수(`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`)로 참조한다. 업로드 루트는 `${UPLOAD_ROOT}`로 주입 가능하게 하고 prod 기본값은 `/app/uploads`로 둔다. prod에서 JPA `ddl-auto=validate`를 사용하며 `create`, `create-drop`, `update`는 금지한다. 관리 endpoint는 `/actuator/health`만 외부 health 용도로 노출하도록 설정한다.
- DoD: 설정 파일 정적 검증에서 `application-prod.yml`의 `ddl-auto=validate`, `${UPLOAD_ROOT:/app/uploads}`, health endpoint 노출 설정이 존재하고 `create|create-drop|update`가 prod 설정에 없음. 시크릿 평문 값이 Git 추적 설정에 없음

### P1-T4. MariaDB 연결
- 의존성: P1-T3
- 산출물: `application-local.yml` (datasource 설정), `docker-compose.local.yml`(선택, 로컬 DB 전용 — 배포용 `docker-compose.yml`은 P12-T1에서 별도 생성)
- 작업 내용: JDBC URL, driver-class-name(`org.mariadb.jdbc.Driver`) 설정
- DoD: 애플리케이션 기동 로그에 `HikariPool-1 - Start completed` 확인, 실패 시 종료코드 non-zero

### P1-T5. 라이브러리 의존성 추가
- 의존성: P1-T1
- 산출물: `build.gradle`
- 작업 내용: Spring Security, Spring Data JPA, QueryDSL(Q타입 생성 플러그인 포함), Validation, Lombok, Thymeleaf, Flyway(`flyway-core` + MariaDB 지원 모듈), Spring Boot Actuator, Jacoco(테스트 커버리지 플러그인, P10-T1 DoD 대비) 추가. Bootstrap/CKEditor5는 정적 리소스(CDN 또는 `src/main/resources/static/vendor`)로 처리
- DoD: `./gradlew compileJava` 성공, QueryDSL Q클래스가 `build/generated`에 생성됨, Gradle dependency 결과에 Flyway/Actuator가 존재, `./gradlew jacocoTestReport` 태스크가 정상 등록되어 실행 가능

### P1-T6. 테스트 프로파일 및 DB 전략 구성
- 의존성: P1-T4, P1-T5
- 산출물: `build.gradle`(Testcontainers 의존성 추가), `src/test/resources/application-test.yml`, `src/test/java/**/support/AbstractIntegrationTest.java`(공통 테스트 베이스, MariaDB Testcontainers 기동)
- 작업 내용: `@SpringBootTest`가 필요한 모든 Repository/통합 테스트(P3-T1, P4-T1, P5-T1, P6-T1 등)는 실제 배포 DB와 동일한 MariaDB 방언 차이를 조기에 발견하기 위해 H2가 아닌 **Testcontainers MariaDB 모듈**을 사용한다. `test` 프로파일은 고정된 datasource 값을 갖지 않고 Testcontainers가 기동 시 동적으로 주입하는 접속정보(`spring.datasource.url` 등)를 `@DynamicPropertySource` 또는 `@ServiceConnection`으로 연결한다. 이후 모든 Phase의 "통합 테스트" DoD는 이 프로파일을 기준으로 한다.
- DoD: 로컬 Docker 데몬이 실행 가능한 환경에서 `./gradlew test` 실행 시 MariaDB Testcontainers가 기동/종료되고, `AbstractIntegrationTest`를 상속한 샘플 테스트 1건이 통과한다. 이 Task의 완료 판정은 현재 테스트 결과만 사용하며, GitHub Actions 환경 재검증은 P12-T3의 별도 책임으로 둔다.

### P1-T7. Flyway baseline + production runtime 계약 검증
- 의존성: P1-T3, P1-T5, P1-T6
- 산출물: `src/main/resources/db/migration/V1__baseline_schema.sql`, `src/test/java/**/support/ProductionRuntimeConfigTest.java`(또는 동등 자동 검증)
- 작업 내용: ERD.md의 현재 Entity/컬럼/unique/PK 제약을 기준으로 초기 schema migration을 작성한다. 모든 Entity의 `id`는 ERD.md 확정 계약대로 `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY`를 사용한다. Flyway가 빈 MariaDB에 V1을 적용한 뒤 JPA prod 계약(`ddl-auto=validate`)으로 스키마가 일치하는지 검증한다. Actuator `/actuator/health`가 의존성/설정상 실제 존재함을 검증한다. 이후 schema 변경은 `db/migration/V{n}__*.sql`로만 추가하고 기존 적용 migration을 수정하지 않는다.
- DoD: 빈 Testcontainers MariaDB에서 Flyway migration 성공 → 애플리케이션 컨텍스트가 `ddl-auto=validate`로 기동 성공. migration 적용 후 `flyway_schema_history` 존재 확인. 테스트 컨텍스트에서 `/actuator/health` 200 + `status=UP`. `src/main/resources/db/migration/V1__baseline_schema.sql` 파일 존재

---

## Phase 2. 공통 기능

### P2-T1. BaseEntity
- 의존성: P1-T5, P1-T7
- 산출물: `common/entity/BaseEntity.java`, `common/config/CommonConfig.java`(JPA Auditing 활성화)
- 작업 내용: `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`, `createdAt`, `updatedAt`. 이 Task 안에서 `@EnableJpaAuditing`을 함께 구성하여 미래 P2-T3에 의존하지 않고 BaseEntity auditing을 검증할 수 있게 한다.
- DoD: P2-T3 없이 Repository 통합 테스트에서 임의 엔티티 저장 시 `createdAt`이 null이 아님

### P2-T2. ApiResponse / ErrorCode / GlobalExceptionHandler
- 의존성: P1-T5
- 산출물: `common/response/ApiResponse.java`, `common/exception/ErrorCode.java`, `common/exception/GlobalExceptionHandler.java`
- 작업 내용: 성공/실패 응답 포맷 통일, `@RestControllerAdvice`로 예외 처리
- DoD: 존재하지 않는 엔드포인트 호출 시 정의된 JSON 에러 포맷 반환(테스트 코드로 검증)

### P2-T3. CommonConfig
- 의존성: P2-T1
- 산출물: `common/config/CommonConfig.java`(P2-T1에서 생성한 설정 확장)
- 작업 내용: P2-T1에서 이미 활성화한 `JpaAuditing`은 중복 등록하지 않고, Bean Validation 등 나머지 공통 설정을 추가한다.
- DoD: 컨텍스트 로딩 테스트(`@SpringBootTest`) 통과하고 JPA Auditing 관련 Bean 중복 등록이 없음

### P2-T4. File 도메인 + 관리자 목록/업로드/다운로드/삭제 (Local Storage)
- 의존성: P2-T1, P2-T2
- 산출물: `file/entity/UploadFile.java`, `file/repository/FileRepository.java`, `file/service/FileService.java`, `file/controller/FileController.java`, `file/controller/AdminFileController.java`, `file/dto/FileResponse.java`
- 작업 내용: ERD.md File 테이블(`@Table(name = "file")`) 기준 Entity와 `FileRepository`를 생성한다. **Entity 클래스명은 `UploadFile`을 사용한다.** `AdminFileController`는 API.md 기준 `GET /api/admin/files`, `POST /api/admin/files`, `DELETE /api/admin/files/{id}`를 담당하고 `FileService → FileRepository → UploadFile` 체인으로 목록/업로드/삭제를 처리한다. 관리자 목록은 `page=0`, `size=20`, `sort=createdAt,DESC` 기본값과 API.md 허용 sort를 적용한다. 업로드는 UUID 파일명, `yyyy/MM/dd` 디렉토리, 확장자 whitelist, IMAGE magic byte, 5MB/10MB 크기 제한을 적용한다. `UploadFile.path`에는 `UPLOAD_ROOT`를 제외한 `yyyy/MM/dd/{uuid}.{ext}` 상대경로만 저장하고 실제 저장/조회/삭제 시 `FileService`가 `UPLOAD_ROOT`와 결합한다. `FileController`는 공개 `GET /api/files/{id}` 다운로드만 담당한다.
- DoD: SecurityFilterChain을 요구하지 않는 Controller/Service 통합 테스트에서 `GET /api/admin/files?page=0&size=20&sort=createdAt,DESC`가 `PageResponse<FileResponse>` 200을 반환하고 최신 업로드순이며, Repository 결과와 API `totalElements`가 일치한다. 업로드 후 UploadFile 레코드와 `업로드루트/yyyy/MM/dd/{uuid}.{ext}` 실파일 존재. 위장 IMAGE는 `INVALID_FILE_TYPE`(400), 용량 초과는 `FILE_SIZE_EXCEEDED`(400), 공개 다운로드 200, DELETE 후 레코드/실파일 삭제 및 재조회 404. `/api/admin/files`의 미인증 401/CSRF 검증은 SecurityFilterChain이 생성되는 P3-T2에서 수행한다.

### P2-T5. HtmlSanitizer 공통 유틸 (XSS 방지)
- 의존성: P1-T5
- 산출물: `common/util/HtmlSanitizer.java`
- 작업 내용: ARCHITECTURE.md "XSS 방지 정책" 기준. jsoup `Safelist` 또는 OWASP Java HTML Sanitizer 사용. 허용 태그는 `p`,`br`,`strong`,`em`,`u`,`h1~h6`,`table`,`tr`,`td`,`th`,`a[href]`,`img[src]`로 한정하고, `script`,`iframe`,`on*` 이벤트 속성, `javascript:` 스킴 링크는 모두 제거한다. Page/Program/Board/Popup Service(P4-T1, P5-T2, P6-T2, P7-T2)의 등록/수정 로직에서 이 유틸을 공통 호출한다.
- DoD: 단위 테스트에서 `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>`, `<a href="javascript:alert(1)">` 입력이 각각 sanitize 후 스크립트/이벤트 속성/스킴이 제거된 채로 반환됨을 검증. 허용 태그(`p`,`table` 등)는 그대로 보존됨을 검증

---

## Phase 3. 관리자 인증

### P3-T1. Admin 도메인 + BCrypt Bean + ApplicationRunner 초기 관리자
- 의존성: P2-T1
- 산출물: `admin/entity/Admin.java`, `admin/repository/AdminRepository.java`, `admin/service/AdminService.java`, `admin/config/AdminPasswordConfig.java`(또는 동등 config), `admin/config/AdminInitializer.java`(`ApplicationRunner`)
- 작업 내용: `login_id`(unique), `password`(BCrypt), `name`, `role=ROLE_ADMIN`을 ERD.md대로 구현한다. 이 Task 안에서 `PasswordEncoder` BCrypt Bean을 먼저 정의하여 미래 P3-T2 산출물에 의존하지 않는다. `ApplicationRunner`는 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME`을 읽고 동일 loginId가 없을 때만 1건 생성하며 BCrypt 해시를 저장한다. 운영 비밀번호를 `data.sql`/소스에 저장하지 않는다. 필수 환경변수가 없으면 prod에서는 계정을 임의 생성하지 않고 명확한 오류 로그를 남긴다.
- DoD: Repository 통합 테스트에서 중복 login_id 저장 시 `DataIntegrityViolationException`. 테스트 환경변수를 주입해 initializer 실행 시 관리자 1건 생성, 재실행 시 건수 증가 없음, `PasswordEncoder.matches(주입한 평문, 저장 hash)=true`. `resources/data.sql`에 관리자 비밀번호가 없고 P3-T2 없이 해당 테스트/컨텍스트가 통과

### P3-T2. SecurityConfig + Session + CSRF
- 의존성: P3-T1
- 산출물: `config/SecurityConfig.java`
- 작업 내용: P3-T1에서 생성한 `PasswordEncoder` Bean을 주입하여 `SecurityFilterChain`을 정의한다. `/admin/login`(GET, 로그인 화면)과 `POST /api/admin/login`은 인증 대상에서 제외(permitAll)하고, 그 외 `/admin/**`, `/api/admin/**`는 세션 기반 ROLE_ADMIN 인증을 요구한다(ARCHITECTURE.md Security 섹션 기준). ARCHITECTURE.md "CSRF 정책" 기준 `CookieCsrfTokenRepository.withHttpOnlyFalse()`를 적용해 `XSRF-TOKEN` 쿠키를 발급하고, `POST /api/admin/login`은 CSRF 토큰 없이도 호출 가능하도록 예외 처리한다.
- DoD: Security 설정 테스트에서 `GET /admin/login`과 `POST /api/admin/login`이 `permitAll`로 등록되고, 그 외 `/admin/**`, `/api/admin/**`가 ROLE_ADMIN 인증 대상으로 등록되어 있음을 검증한다. 기존 P2-T4 API를 사용해 미인증 `GET /api/admin/files`는 401, CSRF 토큰 없는 `POST /api/admin/files`는 403임을 검증한다. `POST /api/admin/login`의 실제 200/401 및 `GET /admin/login` 화면 200은 Controller/View가 생성되는 P3-T3에서 검증한다. P3-T2에서는 미래 P3-T3 산출물을 생성하거나 호출하지 않는다.

### P3-T3. 로그인/로그아웃 구현 + 관리자 정보 조회
- 의존성: P3-T2
- 산출물: `admin/controller/AdminViewController.java`(GET `/admin/login` 화면만 렌더링), `admin/controller/AdminAuthController.java`(API.md 기준 `POST /api/admin/login`, `POST /api/admin/logout`), `admin/controller/AdminController.java`(API.md 기준 `GET /api/admin/me`), `templates/admin/login.html`
- 작업 내용: `/admin/login`은 로그인 폼 화면(GET)만 제공하고, 폼 제출은 JS(fetch)로 API.md의 `POST /api/admin/login`을 호출한다. 인증 성공 시 세션 생성 후 `ApiResponse.success` 반환, 프론트엔드에서 `/admin/dashboard`로 이동한다. 로그아웃은 `POST /api/admin/logout` 호출 후 세션 무효화, 프론트엔드에서 `/admin/login`으로 이동한다. `/admin/**` 화면 경로와 `/api/admin/**` API 경로는 ARCHITECTURE.md 기준 동일한 세션 인증(ROLE_ADMIN)을 공유한다. `AdminController`는 API.md 기준 `GET /api/admin/me` 하나만 제공하며, 세션의 인증 주체(`Authentication`)에서 `Admin.id`를 조회해 `AdminService`를 통해 `id`/`loginId`/`name`/`role`을 반환한다(타 관리자 계정 조회/등록/수정 API는 두지 않는다).
- DoD: `POST /api/admin/login` 성공 시 200 + `ApiResponse.success` + 세션 쿠키 발급, 실패 시 401 + `ApiResponse.fail`. `POST /api/admin/logout` 후 인증 필요 API(`/api/admin/**`) 호출 시 401. 로그인 성공 세션으로 `GET /api/admin/me` 호출 시 200 + 로그인한 관리자 정보 반환, 미인증 `GET /api/admin/me`는 401. `GET /admin/login`은 미인증 상태에서도 200

### P3-T4. 인증 실패 처리 및 접근 권한 설정
- 의존성: P3-T3
- 산출물: `config/SecurityConfig.java`(수정), `common/exception/CustomAuthenticationEntryPoint.java`
- 작업 내용: 인증 실패 시 API는 커스텀 `UNAUTHORIZED` 응답을 사용하고 `/admin/**` 화면 경로는 로그인 화면으로 이동하는 인가 규칙을 설정한다.
- DoD: 미인증 `GET /api/admin/me`가 401 + `UNAUTHORIZED`, 미인증 `GET /admin/login`은 200. Security 설정 단위 테스트에서 `/admin/**`가 인증 대상으로 등록되어 있음을 검증(아직 생성되지 않은 미래 View 경로를 DoD에서 호출하지 않음)

### P3-T5. 관리자 공통 fetch 유틸 (CSRF 헤더 자동 첨부)
- 의존성: P3-T2, P3-T3
- 산출물: `static/js/admin/common-fetch.js`
- 작업 내용: ARCHITECTURE.md "CSRF 정책" 기준. `/admin/**` 화면의 모든 JS는 이 공통 fetch 유틸을 통해서만 `/api/admin/**`을 호출한다. 유틸은 `XSRF-TOKEN` 쿠키 값을 읽어 요청 헤더 `X-XSRF-TOKEN`에 자동으로 담아 전송한다. 이후 모든 관리자 화면(Page/Program/Board/Banner/Popup/File) Task는 이 유틸을 재사용한다.
- DoD: `common-fetch.js`의 쿠키 파싱/헤더 구성 함수를 순수 함수로 분리하고, Node.js 내장 실행 환경 또는 동등한 경량 JS 테스트로 `XSRF-TOKEN` 쿠키 입력 시 `POST/PUT/PATCH/DELETE` 요청 헤더에 `X-XSRF-TOKEN` 값이 포함되는지 자동 검증한다. Playwright는 요구하지 않는다.

---

## Phase 4. CMS 페이지 관리 (기관소개)

### P4-T1. Page Entity + 고정 페이지 초기화/조회/수정
- 의존성: P2-T1, P2-T2, P2-T5, P3-T4
- 산출물: `page/entity/CmsPage.java`, `page/entity/PageType.java`(enum: `GREETING`, `INTRODUCTION`, `HISTORY`, `LOCATION`), `page/repository/PageRepository.java`, `page/service/PageService.java`, `page/controller/AdminPageController.java`, `page/dto/PageRequest.java`, `page/dto/PageResponse.java`
- 작업 내용: 페이지 타입(`GREETING`, `INTRODUCTION`, `HISTORY`, `LOCATION`)을 enum으로 관리(ERD.md 기준). 4개 타입은 고정 리소스로 애플리케이션 초기화 시 누락된 레코드만 생성하며, `AdminPageController`는 API.md의 `GET /api/admin/pages/{pageType}`와 `PUT /api/admin/pages/{pageType}`만 제공하고 POST/DELETE API는 제공하지 않는다. 타입별 단일 레코드 정책을 사용한다. `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다. **Entity 클래스명은 `CmsPage`를 사용한다(`org.springframework.data.domain.Page<T>`와의 이름 충돌 방지 목적. 테이블명(`page`)/패키지명/API 경로는 영향 없음, CODING_RULES.md "Naming" 섹션 기준). Repository/Service/Controller의 목록 조회 메서드가 `Pageable`/`Page<T>`를 반환하는 경우 반드시 FQCN 또는 명시적 import로 구분한다.** 공개 조회 Controller(`PageController`)는 이 Task에서 만들지 않고 P4-T3에서 별도로 생성한다(공개 API/화면과 관리자 CRUD의 책임 분리).
- DoD: 초기화 후 4개 타입 레코드가 각각 정확히 1건 존재하고, 관리자 조회→수정 통합 테스트가 통과한다. 재기동/초기화 로직 재실행 시 중복 레코드가 생성되지 않는다. `<script>` 포함 content 저장 시 정제되어 저장됨을 검증

### P4-T2. CKEditor5 적용 및 이미지 업로드 연동
- 의존성: P4-T1, P2-T4
- 산출물: `templates/admin/page/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 공통 `POST /api/admin/files`와 연결한다. Page 전용 업로드 Controller/API는 생성하지 않는다.
- DoD: CKEditor 업로드 어댑터가 공통 `POST /api/admin/files`와 `fileType=IMAGE`를 사용하도록 템플릿/JS 정적 테스트로 검증하고, 별도 File API 통합 테스트에서 업로드 응답 `url` 존재와 실파일 생성을 검증한다. 실제 관리자 화면 진입 E2E는 해당 P9 View Task 이후에 수행한다.

### P4-T3. 공개 Page 조회 Controller (API + 화면)
- 의존성: P4-T1
- 산출물: `page/controller/PageController.java`(API.md 기준 `GET /api/pages/{pageType}`, `ApiResponse` 반환), `page/controller/PageViewController.java`(ARCHITECTURE.md URL 섹션 기준 `GET /pages/{type}`, Thymeleaf 뷰 반환)
- 작업 내용: 두 경로 모두 인증 없이 접근 가능하도록 Security 설정을 확인한다(ARCHITECTURE.md "비로그인 접근" 대상). API Controller와 View Controller는 별도 클래스로 분리하여 JSON 응답과 화면 렌더링 책임을 섞지 않는다(P3-T3의 AdminViewController/AdminAuthController 분리 패턴과 동일). `PageViewController`는 P8-T2에서 생성될 `templates/home/page/*.html`의 확정 View 이름만 반환하며, 이 Task에서 임시 템플릿을 생성하지 않는다.
- DoD: 인증 없이 `GET /api/pages/{pageType}`가 4개 타입 각각 200 + `ApiResponse.success`를 반환한다. Controller 단위/정적 테스트에서 `GET /pages/{type}` 매핑과 4개 타입별 확정 View 이름 반환을 검증한다. 실제 HTML 200 렌더링은 템플릿이 생성되는 P8-T2에서 검증한다.

---

## Phase 5. 프로그램 관리

### P5-T1. Program 도메인 기반
- 의존성: P2-T1, P2-T2, P3-T4
- 산출물: `program/entity/Program.java`, `program/entity/ProgramType.java`, `program/entity/RecruitStatus.java`(또는 동일 enum 위치), `program/repository/ProgramRepository.java`, `program/service/ProgramService.java`, `program/dto/ProgramRequest.java`, `program/dto/ProgramResponse.java`
- 작업 내용: ERD.md/API.md의 핵심 필드를 이 시점에 모두 정의한다: `programType`, `title`, `content`, `thumbnail`, `attachment`, `googleFormUrl`, `recruitStatus`, `isPublic`. `ProgramType=COURSE/SPECIAL`, `RecruitStatus=OPEN/CLOSED`. `googleFormUrl`은 값이 있을 때 http/https URL 형식 검증을 DTO에 포함한다. POST 기본값은 `recruitStatus=OPEN`, `isPublic=false`. 공개 조회 Controller와 관리자 Controller는 아직 만들지 않는다.
- DoD: Entity/DTO 필드가 ERD.md/API.md 계약과 일치하는 정적/단위 테스트, enum 저장/조회 Repository 통합 테스트, 잘못된 googleFormUrl DTO Validation 실패 테스트 통과

### P5-T2. 관리자 Program CRUD + 관리자 GET + 공개 필터 Service 계약
- 의존성: P5-T1, P2-T5
- 산출물: `program/controller/AdminProgramController.java`, `program/service/ProgramService.java`(수정), `program/repository/ProgramRepository.java`(수정), `src/test/java/**/program/**`
- 작업 내용: API.md의 관리자 `GET /api/admin/programs`, `GET /api/admin/programs/{id}`, `POST`, `PUT`, `DELETE`, `PATCH .../visibility`, `PATCH .../status` 전체를 구현한다. 관리자 GET은 `isPublic`과 관계없이 조회한다. PUT은 API.md의 전체 수정 필수 필드를 적용하고 PATCH는 전용 단일 필드 DTO를 사용한다. 공개 Controller는 만들지 않지만 Service/Repository에 공개 목록/상세용 `isPublic=true` 필터 계약을 구현한다. `content` 저장 시 HtmlSanitizer를 적용한다. P5-T2는 파일 업로드 UI/CKEditor/공개 HTTP Controller 없이 독립 완료되어야 한다.
- DoD: 인증된 관리자 HTTP 테스트에서 비공개 Program도 목록/상세 200, POST 201, PUT 200, visibility/status PATCH 200, DELETE 204. POST 기본값 `OPEN/false`, 잘못된 googleFormUrl 400, `<script>` content 정제 확인. Service/Repository 테스트에서 공개 목록/상세 조회 로직은 `isPublic=true`만 반환. 공개 HTTP 수준 검증은 P5-T5에서만 수행

### P5-T4. 썸네일/첨부파일 연동
- 의존성: P5-T2, P2-T4
- 산출물: `templates/admin/program/form.html`(썸네일/첨부파일 업로드 UI), `program/dto/ProgramRequest.java`(수정, 도메인 검증 반영)
- 작업 내용: Program 전용 업로드 엔드포인트를 별도로 생성하지 않는다(API.md "용도별 별도 엔드포인트를 두지 않는다" 원칙 준수). 화면 JS가 기존 `POST /api/admin/files`를 썸네일은 `fileType=IMAGE`, 첨부는 `fileType=ATTACHMENT`로 호출하여 File 업로드 후 반환된 `url`을 Program 등록/수정 폼에 담아 `AdminProgramController`(P5-T2)의 `POST`/`PUT`으로 전달한다. 이미지 전용/문서 확장자 허용 검증은 P2-T4 FileService가 `fileType` 기준으로 이미 수행하므로 이 Task에서 중복 검증 로직을 만들지 않는다.
- DoD: `fileType=IMAGE`로 이미지 아닌 파일 업로드 시 `INVALID_FILE_TYPE`(400)을 확인한다. File API로 업로드해 받은 `url`을 Program POST/PUT DTO의 `thumbnail`/`attachment`에 전달하는 API 통합 테스트로 정상 저장을 검증하고, `form.html`이 동일 File API와 필드를 사용하도록 정적 테스트한다. 실제 화면 E2E는 P9-T2b 이후에 수행한다.

### P5-T4A. Program 검색(QueryDSL)
- 의존성: P5-T1
- 산출물: `program/repository/ProgramRepositoryCustom.java`(QueryDSL), `program/dto/ProgramSearchCondition.java`
- 작업 내용: API.md `GET /api/programs`의 `keyword`(제목/내용 대상) 쿼리 파라미터를 QueryDSL 동적 조건으로 구현한다. `programType` 필터와 동시 조합 가능해야 하며(BooleanBuilder 또는 BooleanExpression 조합), `Pageable`을 지원한다. Board의 `BoardRepositoryCustom`(P6-T3)과 동일한 패턴을 재사용한다.
- DoD: `keyword`만 적용, `programType`만 적용, 둘을 동시 적용한 3가지 조건 각각에 대해 검색 결과가 조건에 맞는 항목만 반환되는지 QueryDSL 테스트로 검증

### P5-T5. 공개 Program 조회 Controller (API + 화면)
- 의존성: P5-T2, P5-T4A
- 산출물: `program/controller/ProgramController.java`(API.md 기준 `GET /api/programs`, `GET /api/programs/{id}`), `program/controller/ProgramViewController.java`(ARCHITECTURE.md URL 섹션 기준 `GET /programs`, `GET /programs/{id}`, Thymeleaf 뷰 반환)
- 작업 내용: API.md에 정의된 `GET /api/programs`, `GET /api/programs/{id}`를 구현한다. `isPublic=false`인 프로그램은 공개 목록에서 제외하고 공개 상세는 404 처리한다. `programType`, `keyword`(P5-T4A 기준) 쿼리 파라미터로 필터링한다. 관리자 조회 API(`GET /api/admin/programs`, `GET /api/admin/programs/{id}`)는 공개 여부와 관계없이 반환한다.
- DoD: 인증 없이 목록/상세 조회 200, 비공개 프로그램 상세 조회 시 404, `programType` 필터 적용 시 해당 타입만 반환, `keyword` 검색 시 제목/내용에 일치하는 항목만 반환

### P5-T5A. 관리자 Program 검색 계약 보완
- 의존성: P5-T2, P5-T4A
- 산출물: `program/controller/AdminProgramController.java`(수정), `program/service/ProgramService.java`(수정), `src/test/java/**/program/**`(관리자 검색 테스트 추가)
- 작업 내용: API.md `GET /api/admin/programs`의 `programType`/`keyword` 쿼리 파라미터 계약을 실제 구현에 연결한다. P5-T4A에서 구현한 `ProgramSearchCondition`/`ProgramRepositoryCustom.search()`(QueryDSL)를 그대로 재사용하며 별도 검색 로직을 새로 만들지 않는다. `AdminProgramController.list()`가 `programType`/`keyword`를 `@RequestParam`으로 받아 `ProgramService`에 전달하고, `ProgramService`는 `ProgramSearchCondition`의 `isPublic`을 `null`(필터 미강제)로 구성해 `programRepository.search(...)`를 호출하도록 `findAll(pageable)` 호출을 대체한다. `GET /api/programs`(P5-T5)의 공개 목록 `isPublic=true` 강제 정책과 그 구현은 변경하지 않는다.
- DoD: 인증된 관리자 HTTP 테스트에서 `programType`만 적용, `keyword`만 적용, 두 조건 동시 적용의 3가지 케이스 각각 조건에 맞는 항목만 반환됨을 검증하고, 3가지 케이스 모두에서 비공개(`isPublic=false`) Program도 결과에 포함됨을 검증한다. 공개 `GET /api/programs`는 기존과 동일하게 `isPublic=true`만 반환함을 회귀 테스트로 재확인한다.

### P5-T6. Program CKEditor5 이미지 업로드 연동
- 의존성: P5-T2, P2-T4
- 산출물: `templates/admin/program/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결(P4-T2와 동일한 연동 방식 재사용)
- DoD: CKEditor 업로드 어댑터가 공통 `POST /api/admin/files`와 `fileType=IMAGE`를 사용하도록 템플릿/JS 정적 테스트로 검증하고, 별도 File API 통합 테스트에서 업로드 응답 `url` 존재와 실파일 생성을 검증한다. 실제 관리자 화면 진입 E2E는 해당 P9 View Task 이후에 수행한다.

---

## Phase 6. 게시판 관리

### P6-T1. Board 도메인 + BoardType
- 의존성: P2-T1, P2-T2, P3-T4
- 산출물: `board/entity/Board.java`, `board/entity/BoardType.java`(enum: `NOTICE`,`GALLERY`,`ARCHIVE`), `board/repository/BoardRepository.java`, `board/service/BoardService.java`, `board/dto/BoardRequest.java`, `board/dto/BoardResponse.java`
- 작업 내용: ERD.md/API.md 기준 Board 도메인 기반(Entity/Enum/Repository/Service/DTO)만 생성한다. HTTP Controller는 이 Task에서 생성하지 않으며 `AdminBoardController`, `BoardController`, `BoardViewController`는 P6-T2가 전담한다.
- DoD: 타입별 저장/조회 Repository 통합 테스트 통과. P6-T2의 Controller 없이 독립 완료 가능

### P6-T2. CRUD (목록/상세/등록/수정/삭제)
- 의존성: P6-T1, P2-T5
- 산출물: `board/controller/AdminBoardController.java`, `board/controller/BoardController.java`(API.md 기준 공개 API), `board/controller/BoardViewController.java`(ARCHITECTURE.md URL 섹션 기준 `GET /boards`, `GET /boards/{id}` 화면)
- 작업 내용: API.md의 관리자 `GET /api/admin/boards`, `GET /api/admin/boards/{id}` 및 POST/PUT/DELETE/PATCH visibility를 구현하고, 관리자 GET은 `isPublic=false`도 반환한다. 공개 `BoardController`는 `isPublic=true`만 반환한다. `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다. `BoardController`(API)와 `BoardViewController`(화면)는 책임을 분리한다.
- DoD: 관리자 CRUD/GET 통합 테스트 통과(비공개 Board가 관리자 목록/상세 200), `<script>` content 정제, 공개 API 목록/상세 조회 200, visibility false 전환 후 공개 API 목록/상세에서 제외되고 상세는 `BOARD_NOT_FOUND`(404). `BoardViewController`는 Controller 단위/정적 테스트에서 `GET /boards`, `GET /boards/{id}` 매핑과 확정 View 이름 반환까지만 검증한다. 실제 공개 HTML 200 렌더링은 `templates/home/board/*.html`이 생성되는 P8-T4에서 검증한다.

### P6-T3. 검색/페이징
- 의존성: P6-T2
- 산출물: `board/repository/BoardRepositoryCustom.java`(QueryDSL), `board/dto/BoardSearchCondition.java`
- 작업 내용: 제목/내용 검색, `Pageable` 지원
- DoD: 검색 조건에 맞는 결과만 반환되는지 QueryDSL 테스트로 검증

### P6-T4. 조회수 증가 처리
- 의존성: P6-T2
- 산출물: `board/service/BoardService.java`(조회수 증가 로직)
- 작업 내용: 상세 조회 시 조회수 증가(동시성 고려: `@Query` UPDATE 또는 낙관적 락). (content sanitize와 공개여부 제어는 P6-T2에서, 대표이미지/첨부파일 연동은 P6-T4A에서 각각 이미 처리하므로 이 Task는 조회수 증가에만 집중한다)
- DoD: 동시 요청 100회 시 조회수 정확히 100 증가(부하 테스트 또는 동시성 단위 테스트)

### P6-T4A. Board 썸네일/첨부파일 연동
- 의존성: P6-T2, P2-T4
- 산출물: `templates/admin/board/form.html`(대표이미지/첨부파일 업로드 UI), `board/dto/BoardRequest.java`(수정, 도메인 검증 반영)
- 작업 내용: Board 전용 업로드 엔드포인트를 별도로 생성하지 않는다(API.md "용도별 별도 엔드포인트를 두지 않는다" 원칙 준수). 화면 JS가 기존 `POST /api/admin/files`를 대표이미지(갤러리 `thumbnail`)는 `fileType=IMAGE`, 첨부파일(자료실 `attachment`)은 `fileType=ATTACHMENT`로 호출하여 File 업로드 후 반환된 `url`을 Board 등록/수정 폼에 담아 `AdminBoardController`(P6-T2)의 `POST`/`PUT`으로 전달한다. 이미지 전용/문서 확장자 허용 검증은 P2-T4 FileService가 `fileType` 기준으로 이미 수행하므로 이 Task에서 중복 검증 로직을 만들지 않는다(P5-T4와 동일한 패턴).
- DoD: `fileType=IMAGE`로 이미지 아닌 파일 업로드 시 `INVALID_FILE_TYPE`(400)을 확인한다. File API로 업로드해 받은 `url`을 Board POST/PUT DTO의 `thumbnail`/`attachment`에 전달하는 API 통합 테스트로 정상 저장을 검증하고, `form.html`이 동일 File API와 필드를 사용하도록 정적 테스트한다. 실제 화면 E2E는 P9-T2c 이후에 수행한다.

### P6-T5. Board CKEditor5 이미지 업로드 연동
- 의존성: P6-T2, P2-T4
- 산출물: `templates/admin/board/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결(P4-T2와 동일한 연동 방식 재사용). 갤러리(`GALLERY`)는 대표 이미지 업로드와 별도 항목이므로 혼동하지 않는다.
- DoD: CKEditor 업로드 어댑터가 공통 `POST /api/admin/files`와 `fileType=IMAGE`를 사용하도록 템플릿/JS 정적 테스트로 검증하고, 별도 File API 통합 테스트에서 업로드 응답 `url` 존재와 실파일 생성을 검증한다. 실제 관리자 화면 진입 E2E는 해당 P9 View Task 이후에 수행한다.

---

## Phase 7. 메인 관리

### P7-T1. Banner CRUD + 정렬 + 공개여부 + 이미지 업로드 연동
- 의존성: P2-T1, P2-T2, P2-T4, P3-T4
- 산출물: `banner/entity/Banner.java`, `BannerRepository`, `BannerService`, `BannerController`, `AdminBannerController`(ARCHITECTURE.md 명명 기준), `banner/dto/BannerRequest.java`, `banner/dto/BannerResponse.java`, `templates/admin/banner/form.html`(이미지 업로드 UI)
- 작업 내용: API.md의 관리자 `GET /api/admin/banners`, `GET /api/admin/banners/{id}`, POST/PUT/DELETE, visibility/order PATCH를 구현하며 관리자 GET은 `isVisible=false`도 반환한다. 공개 GET은 `isVisible=true`만 반환한다. `sortOrder` 필드, 관리자 화면에서 순서 변경 API. Banner 전용 업로드 엔드포인트를 별도로 생성하지 않는다(API.md "용도별 별도 엔드포인트를 두지 않는다" 원칙 준수). 화면 JS가 기존 `POST /api/admin/files`를 `fileType=IMAGE`로 호출하여 File 업로드 후 반환된 `url`을 Banner 등록/수정 폼의 `image` 필드에 담아 `AdminBannerController`의 `POST`/`PUT`으로 전달한다(P5-T4/P6-T4A와 동일한 패턴).
- DoD: 비노출 Banner가 관리자 목록/상세 GET 200임을 확인, 정렬 변경 API 호출 후 조회 순서가 반영됨(테스트), `PATCH /api/admin/banners/{id}/visibility` 호출로 `is_visible=false` 전환 시 해당 배너가 공개 목록 API(`GET /api/banners`) 응답에서 제외됨을 통합 테스트로 검증, `fileType=IMAGE`로 이미지 아닌 파일 업로드 시 `INVALID_FILE_TYPE`(400) 확인, 업로드된 File의 `url`이 Banner의 `image` 필드에 정상 저장됨을 통합 테스트로 검증

### P7-T2. Popup CRUD + 기간설정 + 공개여부
- 의존성: P2-T1, P2-T2, P2-T5, P3-T4
- 산출물: `popup/entity/Popup.java`, `PopupRepository`, `PopupService`, `PopupController`, `AdminPopupController`(ARCHITECTURE.md 명명 기준), `popup/dto/PopupRequest.java`, `popup/dto/PopupResponse.java`
- 작업 내용: API.md의 관리자 `GET /api/admin/popups`, `GET /api/admin/popups/{id}`, POST/PUT/DELETE/visibility PATCH를 구현하며 관리자 GET은 비노출 및 기간 밖 Popup도 반환한다. 공개 GET은 `isVisible=true`이고 현재 시간이 노출기간 내인 항목만 반환한다. `startDate`, `endDate` 범위 밖이면 공개 API에서 자동 제외. `content` 저장 시 `HtmlSanitizer`(P2-T5)를 통해 정제한다.
- DoD: 비노출/기간 밖 Popup이 관리자 목록/상세 GET 200, 기간이 지난 팝업이 공개 목록에 나타나지 않음(날짜 조작 테스트), `<script>` 포함 content 저장 시 정제되어 저장됨을 검증

### P7-T3. Popup CKEditor5 이미지 업로드 연동
- 의존성: P7-T2, P2-T4
- 산출물: `templates/admin/popup/form.html`
- 작업 내용: CKEditor5 이미지 업로드 어댑터를 P2-T4 FileService(`POST /api/admin/files`)와 연결(P4-T2와 동일한 연동 방식 재사용)
- DoD: CKEditor 업로드 어댑터가 공통 `POST /api/admin/files`와 `fileType=IMAGE`를 사용하도록 템플릿/JS 정적 테스트로 검증하고, 별도 File API 통합 테스트에서 업로드 응답 `url` 존재와 실파일 생성을 검증한다. 실제 관리자 화면 진입 E2E는 해당 P9 View Task 이후에 수행한다.

---

## Phase 8. 홈페이지 (공개 영역)

### P8-T1. 메인 화면 (배너/팝업/기관소개 요약/최신 게시글)
- 의존성: P5-T2, P7-T1, P7-T2, P6-T2, P4-T3
- 산출물: `home/controller/HomeController.java`, `templates/home/index.html`
- 작업 내용: ARCHITECTURE.md Home 기능 목록 기준. 배너/팝업/최신 공지·갤러리/프로그램 바로가기에 더해, PageService(P4-T1)를 조합하여 기관소개 인사말(`GREETING`) 요약을 메인 화면에 노출한다. `바로가기 메뉴`는 별도 도메인/Entity/API 없이 고정 링크 3개(`기관소개` → `/pages/GREETING`, `프로그램` → `/programs`, `게시판` → `/boards`)를 렌더링한다.
- DoD: `GET /` 200 응답, 응답 HTML에 배너/팝업/인사말 요약/최신글/프로그램 바로가기/바로가기 메뉴 영역 태그가 존재하고, 바로가기 메뉴의 3개 `href`가 ARCHITECTURE.md의 고정 경로와 일치함을 HTML 파싱 테스트로 검증

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
- DoD: 비공개 게시글은 목록 조회 응답에 포함되지 않고, 상세 조회 시 `BOARD_NOT_FOUND`(404)를 반환한다(Program의 P5-T5 패턴과 동일. 익명 사용자에게는 `ACCESS_DENIED`가 트리거되는 경로가 없으므로 403은 사용하지 않는다, CODING_RULES.md ErrorCode 카탈로그 비고 기준)

---

## Phase 9. 관리자 CMS

### P9-T1. Dashboard
- 의존성: P6-T2, P5-T2, P3-T5
- 산출물: `admin/controller/DashboardController.java`(API, `GET /api/admin/dashboard`), `admin/controller/AdminViewController.java`(수정, `GET /admin/dashboard` 화면 렌더링 추가), `templates/admin/dashboard.html`
- 작업 내용: 최근 게시글 N건, 프로그램 현황(모집중/마감 카운트), API.md에 확정된 고정 빠른 메뉴 6개(기관소개/프로그램/게시판/배너/팝업/파일 관리)를 반환한다. ARCHITECTURE.md "Admin 화면(View) / API 컨트롤러 명명 규칙" 기준, `DashboardController`는 JSON 데이터만 반환하고 화면 렌더링은 P3-T3에서 생성된 `AdminViewController`가 담당한다(로그인 화면과 동일 클래스). `dashboard.html`은 P3-T5 공통 fetch 유틸로 `GET /api/admin/dashboard`를 호출해 데이터를 채운다. 이 시점의 `dashboard.html`은 공통 헤더/사이드바 레이아웃 없이 콘텐츠 영역만 우선 구현하며, 공통 레이아웃 상속은 P9-T2a에서 일괄 적용한다(ARCHITECTURE.md "Admin(Dashboard 포함)" 기준, Dashboard도 공통 레이아웃 적용 대상에 포함됨).
- DoD: `GET /api/admin/dashboard` 응답에 세 영역의 데이터가 모두 포함되고 `quickMenus`의 6개 `label`/`url`과 순서가 API.md 계약과 정확히 일치한다. 미인증 상태로 `GET /admin/dashboard` 접근 시 302 리다이렉트(인증 후 200)

### P9-T2a. 관리자 공통 레이아웃
- 의존성: P3-T3, P3-T4, P3-T5, P9-T1
- 산출물: `templates/admin/layout/*.html`(공통 헤더/사이드바/푸터 레이아웃, 각 도메인 화면이 상속), `templates/admin/dashboard.html`(수정, 공통 레이아웃 상속 적용)
- 작업 내용: P3-T5 common-fetch.js를 사용하는 공통 레이아웃 뼈대 생성. 헤더는 `GET /api/admin/me`(P3-T3)를 호출해 로그인한 관리자명을 표시한다. ARCHITECTURE.md "Admin 화면(View) / API 컨트롤러 명명 규칙" 기준 이 규칙은 Admin(Dashboard 포함) 전 도메인에 적용되므로, P9-T1에서 레이아웃 없이 먼저 만들어진 `dashboard.html`을 이 시점에 공통 레이아웃을 상속하도록 수정한다. 이후 P9-T2b~T2g는 처음부터 이 레이아웃을 상속하여 신규 작성한다.
- DoD: 레이아웃 템플릿이 하위 도메인 화면 1개 이상에서 정상 렌더링됨을 통합 테스트로 확인한다. `GET /admin/dashboard` 응답 HTML에 공통 헤더의 관리자명 표시용 DOM 영역과 사이드바가 존재하고, 헤더 JS가 `GET /api/admin/me`를 호출해 `name`을 해당 영역에 주입하도록 정적 테스트한다. 별도 API 통합 테스트에서 인증 세션의 `GET /api/admin/me`가 관리자 `name`을 반환함을 검증한다.

### P9-T2b. Program 관리자 화면
- 의존성: P9-T2a, P5-T2, P5-T4, P5-T4A, P5-T6
- 산출물: `program/controller/AdminProgramViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/programs`, `/admin/programs/new`, `/admin/programs/{id}/edit` 화면 렌더링), `templates/admin/program/list.html`, `templates/admin/program/form.html`(P5-T6 기존 form.html과 통합)
- 작업 내용: `AdminProgramViewController`는 화면 렌더링만 담당하며, 실제 CRUD/검색은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminProgramController`(P5-T2)의 API를 호출해 처리한다.
- DoD: `/admin/programs`, `/admin/programs/new`, `/admin/programs/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2c. Board 관리자 화면
- 의존성: P9-T2a, P6-T2, P6-T3, P6-T4A, P6-T5
- 산출물: `board/controller/AdminBoardViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/boards`, `/admin/boards/new`, `/admin/boards/{id}/edit` 화면 렌더링), `templates/admin/board/list.html`, `templates/admin/board/form.html`(P6-T4A/P6-T5 기존 form.html과 통합)
- 작업 내용: `AdminBoardViewController`는 화면 렌더링만 담당하며, 실제 CRUD/검색은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminBoardController`(P6-T2)의 API를 호출해 처리한다.
- DoD: `/admin/boards`, `/admin/boards/new`, `/admin/boards/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2d. Page 관리자 화면
- 의존성: P9-T2a, P4-T1, P4-T2
- 산출물: `page/controller/AdminPageViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/pages`, `/admin/pages/{pageType}/edit` 화면 렌더링), `templates/admin/page/list.html`, `templates/admin/page/form.html`(P4-T2 기존 form.html과 통합)
- 작업 내용: `AdminPageViewController`는 화면 렌더링만 담당하며, 실제 조회/수정은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminPageController`(P4-T1)의 API를 호출해 처리한다. Page는 타입별 단일 레코드이므로 `/new` 라우트는 두지 않는다.
- DoD: `/admin/pages`, `/admin/pages/{pageType}/edit`(4개 타입) 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2e. Banner 관리자 화면
- 의존성: P9-T2a, P7-T1
- 산출물: `banner/controller/AdminBannerViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/banners`, `/admin/banners/new`, `/admin/banners/{id}/edit` 화면 렌더링), `templates/admin/banner/list.html`, `templates/admin/banner/form.html`(P7-T1 기존 form.html과 통합)
- 작업 내용: `AdminBannerViewController`는 화면 렌더링만 담당하며, 실제 CRUD/정렬은 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminBannerController`(P7-T1)의 API를 호출해 처리한다.
- DoD: `/admin/banners`, `/admin/banners/new`, `/admin/banners/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2f. Popup 관리자 화면
- 의존성: P9-T2a, P7-T2, P7-T3
- 산출물: `popup/controller/AdminPopupViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/popups`, `/admin/popups/new`, `/admin/popups/{id}/edit` 화면 렌더링), `templates/admin/popup/list.html`, `templates/admin/popup/form.html`(P7-T3 기존 form.html과 통합)
- 작업 내용: `AdminPopupViewController`는 화면 렌더링만 담당하며, 실제 CRUD는 화면의 JS가 P3-T5 공통 fetch 유틸로 `AdminPopupController`(P7-T2)의 API를 호출해 처리한다.
- DoD: `/admin/popups`, `/admin/popups/new`, `/admin/popups/{id}/edit` 화면 200 응답, 인가되지 않은 사용자는 401/302로 차단

### P9-T2g. File 관리자 화면
- 의존성: P9-T2a, P2-T4
- 산출물: `file/controller/AdminFileViewController.java`(ARCHITECTURE.md 명명 규칙 기준, `GET /admin/files` 화면 렌더링), `templates/admin/file/list.html`(ARCHITECTURE.md Admin URL `/admin/files` 기준, 업로드 이력 조회 및 삭제)
- 작업 내용: `AdminFileViewController`는 화면 렌더링만 담당하며, 목록 데이터는 API.md의 `GET /api/admin/files`, 삭제는 `DELETE /api/admin/files/{id}`를 P3-T5 공통 fetch 유틸로 호출한다.
- DoD: `/admin/files` 목록 화면 200 응답, 템플릿/JS 정적 테스트에서 삭제 핸들러가 `DELETE /api/admin/files/{id}` 호출 성공 후 목록 재조회 함수를 실행하도록 검증한다. 별도 API 통합 테스트에서 DELETE 204와 재조회 404를 확인하고, 인가되지 않은 사용자는 401/302로 차단한다.

---

## Phase 10. 테스트

### P10-T1. 기능 테스트 스위트
- 의존성: P1-T6, P6-T4, P8-T1, P8-T2, P8-T3, P8-T4, P9-T2b, P9-T2c, P9-T2d, P9-T2e, P9-T2f, P9-T2g
- 산출물: `src/test/java/**` (로그인, 권한, CRUD, 검색, 파일 업로드, Google Form 연결). 모든 통합 테스트는 P1-T6의 `AbstractIntegrationTest`(Testcontainers MariaDB)를 상속한다.
- DoD: `./gradlew test` 전체 통과, 커버리지 리포트 생성(jacoco 등)

### P10-T2. 예외 처리 테스트
- 의존성: P10-T1
- 산출물: `src/test/java/**exception**`
- 작업 내용: Validation 실패, 인증 실패, 파일 오류, 잘못된 요청 각각에 대한 케이스. `ACCESS_DENIED`, `DUPLICATE_LOGIN_ID`는 API 레벨 요청 흐름이 존재하지 않으므로 CODING_RULES.md ErrorCode 카탈로그의 각 비고에 명시된 대체 검증 방식(전자는 `AccessDeniedHandler` 단위 테스트, 후자는 `AdminRepository` unique 제약 통합 테스트)을 그대로 사용한다.
- DoD: 정의된 `ErrorCode` 별로 최소 1개 이상의 테스트 존재, 전부 통과(단 `ACCESS_DENIED`, `DUPLICATE_LOGIN_ID`는 위 대체 검증 방식으로 충족)

---

## Phase 11. UI/UX 개선

### P11-T0. 프론트엔드 테스트 도구 설치 (Playwright / Lighthouse CI)
- 의존성: P1-T1
- 산출물: `package.json`, `package-lock.json`(또는 동등 lockfile), `.gitignore`(수정, `node_modules/` 추가), `frontend-tests/playwright.config.js`, `frontend-tests/lighthouserc.js`
- 작업 내용: Java/Gradle 프로젝트와 별개로 Node.js 기반 프론트엔드 테스트 전용 디렉토리(`frontend-tests/`)를 구성한다. `npm init` 후 `@playwright/test`(P11-T1의 뷰포트 스크린샷/픽셀 diff용)와 `@lhci/cli`(P11-T2의 Lighthouse 접근성 점수용)를 devDependencies로 추가한다. Gradle 빌드와는 독립적으로 `npm ci && npx playwright install --with-deps`로 실행 가능해야 하며, Gradle에 Node 플러그인을 강제 통합하지 않는다(빌드 도구 분리 원칙).
- DoD: `npm ci` 종료 코드 `0`, `npx playwright --version` 및 `npx lhci --version` 정상 출력, `node_modules/`가 `git status`에 잡히지 않음

### P11-T1. 반응형 적용
- 의존성: P8-T1, P8-T2, P8-T3, P8-T4, P11-T0
- 산출물: `static/css/**`, `frontend-tests/visual-regression.spec.js`
- DoD: Playwright로 375px/768px/1440px 각 뷰포트에서 공개 주요 화면을 열어 수평 overflow가 없고 주요 내비게이션/본문/버튼이 visible 상태인지 자동 검증한다. 픽셀 기준 이미지 비교는 본 프로젝트 범위에서 사용하지 않는다.

### P11-T2. 관리자 UI 개선 + CKEditor 스타일 + 이미지 최적화 + 접근성
- 의존성: P9-T2a, P9-T2b, P9-T2c, P9-T2d, P9-T2e, P9-T2f, P9-T2g, P11-T0
- 산출물: `static/css/admin/**`, `frontend-tests/lighthouserc.js`(설정 보강)
- DoD: P11-T0에서 설치한 Lighthouse CI로 측정한 접근성 점수 90 이상(고정 임계치), 이미지 `loading="lazy"` 속성 적용 여부를 HTML 파싱으로 확인

---

## Phase 12. 배포

### P12-T1. Dockerfile / docker-compose
- 의존성: P1-T7, P10-T1, P11-T1, P11-T2
- 산출물: `Dockerfile`, `docker-compose.yml`, `.env.example`(시크릿 값 없이 변수명만)
- 작업 내용: prod profile로 실행하고 `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD`, `ADMIN_NAME`, `UPLOAD_ROOT`, DB 접속 환경변수를 Compose에서 주입한다. 업로드는 `./data/uploads:/app/uploads` bind mount, MariaDB는 named volume `db_data:/var/lib/mysql`로 영속화한다. 애플리케이션 기동 시 Flyway migration 후 JPA `ddl-auto=validate`를 통과해야 한다. Spring Boot Actuator `/actuator/health`를 healthcheck로 사용한다. 운영 배포는 수동 `docker compose`이며 자동 CD는 만들지 않는다.
- DoD: `docker build` 성공. 깨끗한 volume에서 `docker compose up` 시 Flyway 적용 + 앱 기동 + `/actuator/health` 200. 테스트 레코드/업로드 파일 생성 후 app 컨테이너 재생성 시 둘 다 유지. MariaDB 컨테이너 재생성(동일 named volume) 후 DB 데이터 유지. `docker compose config` 결과에 `./data/uploads:/app/uploads`와 `db_data:/var/lib/mysql` 존재

### P12-T2. Nginx (Reverse Proxy + Static Resource)
- 의존성: P12-T1
- 산출물: `nginx/nginx.conf`, `docker-compose.yml`(수정: nginx service/port/volume 연결)
- 작업 내용: P12-T1의 Compose에 Nginx service를 추가하고 `nginx/nginx.conf`를 mount한다. 호스트 80 포트는 Nginx가 받고 애플리케이션 컨테이너로 reverse proxy한다. Nginx가 직접 서빙할 프로젝트 정적 리소스는 배포 checkout의 `./src/main/resources/static`을 Nginx 기본 정적 루트 `/usr/share/nginx/html`에 read-only bind mount(`./src/main/resources/static:/usr/share/nginx/html:ro`)하여 공급한다. `/css/**`, `/js/**`, `/images/**`, `/vendor/**` 등 해당 정적 경로는 Nginx가 직접 처리하고 그 외 애플리케이션 요청은 Spring Boot 컨테이너로 reverse proxy한다. 별도 임시 복사 방식이나 shared volume을 Claude Code가 선택하지 않는다.
- DoD: `docker compose config`에서 nginx service, 호스트 80 포트, `nginx/nginx.conf` mount, `./src/main/resources/static:/usr/share/nginx/html:ro` mount가 확인된다. `docker compose up` 후 80번 포트를 통해 애플리케이션 접근 가능하고, `src/main/resources/static`의 검증용 정적 리소스가 80번 포트에서 Nginx에 의해 직접 200 응답됨을 응답 헤더로 자동 검증한다.

### P12-T3. GitHub Actions CI
- 의존성: P12-T1
- 산출물: `.github/workflows/ci.yml`
- 작업 내용: `ci.yml`은 `ubuntu-latest` 러너(Docker 내장, P1-T6 Testcontainers 실행 조건 충족)에서 `./gradlew test`와 `./gradlew build`를 실행한다. 별도의 MariaDB 서비스 컨테이너(`services:`)를 구성하지 않는다. 실제 운영 서버 배포는 본 문서 범위에서 수동 Docker Compose 배포로 하며 자동 CD workflow는 만들지 않는다.
- DoD: `ci.yml` 정적 검증에서 `pull_request` trigger, `ubuntu-latest`, `./gradlew test`, `./gradlew build`가 존재하고 `services:` 기반 MariaDB와 배포/CD step이 없음을 확인한다. 로컬에서 `./gradlew test`와 `./gradlew build`가 모두 성공하면 Task 완료로 판정한다. 이후 실제 PR 생성 시 GitHub Actions에서 동일 workflow와 Testcontainers MariaDB 기동/종료를 확인하는 것은 원격 운영 확인이며 이 Task의 로컬 완료를 차단하지 않는다.

---

# 완료 기준 (Definition of Done) — 자동 검증 가능한 형태로 재기술

| 항목 | 기존 표현 | 자동 검증 방법 |
|---|---|---|
| 관리자 로그인 가능 | 관리자 로그인 가능 | P3-T3 통합 테스트 통과 |
| 기관소개 CMS 수정 가능 | 기관소개 CMS 수정 가능 | P4-T1 고정 페이지 조회/수정 테스트 통과 |
| 프로그램 관리 가능 | 프로그램 관리 가능 | P5-T2 테스트 통과 |
| 게시판 관리 가능 | 게시판 관리 가능 | P6-T2, P6-T3, P6-T4A 테스트 통과 |
| 배너 관리 가능 | 배너 관리 가능 | P7-T1 테스트 통과 |
| 팝업 관리 가능 | 팝업 관리 가능 | P7-T2 테스트 통과 |
| Google Form 정상 연결 | Google Form 정상 연결 | P8-T3 링크 검증 테스트 통과 |
| 파일 관리 정상 동작 | 목록/업로드/다운로드/삭제 | P2-T4 테스트 통과 |
| XSS 방지 적용 완료 | XSS 방지 | P2-T5 단위 테스트 + P4-T1/P5-T2/P6-T2/P7-T2 sanitize 검증 통과 |
| CSRF 보호 적용 완료 | CSRF 보호 | P3-T2, P3-T5 테스트 통과 |
| 예외 처리 완료 | 예외 처리 완료 | P10-T2 통과 |
| 반응형 적용 | 반응형 적용 | P11-T1 확인 |
| 권한 검증 완료 | 권한 검증 완료 | P3-T4, P9-T2a~T2g 통과 |
| 코드 리뷰 완료 | 코드 리뷰 완료 | PR approve 기록 (GitHub) |
| 테스트 완료 | 테스트 완료 | `./gradlew test` 성공 + 커버리지 리포트 |
| 운영 런타임/배포 완료 | Flyway/prod/영속성/CI | P1-T7 + P12-T1~T3 통과, 헬스체크 200 |

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
> 네 Phase가 모두 수렴하는 지점은 Phase8(홈페이지)이며, P8-T1은 P4-T3·P5-T2·P6-T2·P7-T1·P7-T2를
> 모두 필요로 한다(HomeController는 ARCHITECTURE.md "Home" 섹션 기준으로 각 도메인의 공개 Controller가
> 아니라 Service를 직접 조합하므로, Program은 공개 Controller가 완성되는 P5-T5가 아니라 Service/공개여부
> 로직이 완성되는 P5-T2까지만 필요하다). 위 다이어그램은 각 태스크의 `의존성` 필드와 항상 동기화되어야 한다.

에이전트는 각 Phase 내 태스크를 ID 순서대로 실행하되, `의존성` 필드에 명시된
태스크가 완료(DoD 통과)되지 않으면 다음 태스크로 진행하지 않습니다.