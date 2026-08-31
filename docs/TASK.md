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
- 산출물: `board/repository/BoardRepositoryCustom.java`(QueryDSL), `board/dto/BoardSearchCondition.java`, `board/repository/BoardRepository.java`(수정), `board/service/BoardService.java`(수정), `board/controller/AdminBoardController.java`(수정), `board/controller/BoardController.java`(수정)
- 작업 내용: API.md `GET /api/boards`, `GET /api/admin/boards`의 `boardType`/`keyword` 쿼리 파라미터를 QueryDSL 동적 조건으로 구현한다. `boardType`과 `keyword`(제목/내용 대상)는 동시 조합 가능해야 하며(BooleanBuilder 또는 BooleanExpression 조합), `Pageable`을 지원한다. 공개 `BoardController`는 `isPublic=true`를 강제하고, 관리자 `AdminBoardController`는 공개 여부를 강제하지 않는다(API.md 계약). Program의 `ProgramRepositoryCustom`/`ProgramSearchCondition` 최종 패턴을 재사용하며, 이번 Task에서 admin/public 목록 Controller까지 연결해 API.md 계약을 완성한다.
- DoD: `BoardRepositoryCustom`은 QueryDSL 테스트로 `keyword`만 적용, `boardType`만 적용, 둘을 동시 적용한 조건 각각 조건에 맞는 항목만 반환됨과, `isPublic` 조건이 결합될 때도 조건에 맞는 항목만 반환됨을 검증한다. HTTP 레벨에서는 인증된 관리자 검색 결과에 `boardType`/`keyword` 필터가 실제로 적용되며 비공개 Board도 포함됨을 통합 테스트로 검증하고, 공개 검색 결과에는 동일한 필터가 적용되며 비공개 Board는 제외됨을 검증한다.

### P6-T4. 조회수 증가 처리
- 의존성: P6-T2
- 산출물: `board/service/BoardService.java`(조회수 증가 로직)
- 작업 내용: 상세 조회 시 조회수 증가(동시성 고려: `@Query` UPDATE 또는 낙관적 락). (content sanitize와 공개여부 제어는 P6-T2에서, 대표이미지/첨부파일 연동은 P6-T4A에서 각각 이미 처리하므로 이 Task는 조회수 증가에만 집중한다)
- DoD: 동시 요청 100회 시 조회수 정확히 100 증가(부하 테스트 또는 동시성 단위 테스트)

> **P13-T19 재정정**: 이 Task에서 도입한 조회수 증가 기능(`view_count` 컬럼, `increaseViewCount`,
> 상세 조회 시 UPDATE)은 P13-T19에서 완전히 제거됐다. 상세 조회(`BoardService.getPublicById`)는
> 이제 순수 조회이며 DB UPDATE가 발생하지 않는다.

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

## Phase 13. 공개 UI/UX 개선

### P13-T0. Phase 13 문서 계약 및 UI 기반 확정
- 의존성: P12-T1, P12-T2, P12-T3
- 산출물: `docs/TASK.md`(본 Phase 13 추가), `docs/PRD.md`, `docs/FEATURES.md`(메인 프로그램 최신글 노출 요구사항 반영), `docs/ARCHITECTURE.md`(Home 섹션 갱신)
- 작업 내용: 코드/템플릿/CSS 변경 없이 문서 계약만 갱신한다. 메인 화면에 노출할 "최신 프로그램" 요구사항을 PRD.md/FEATURES.md에 기존 최신 공지/갤러리와 동일한 수준으로 명시하고, HomeController가 기존 ProgramService를 조합해 최신 3건을 모델에 제공함을 ARCHITECTURE.md에 반영한다. 이후 P13-T1~T7은 이 문서를 Source of Truth로 진행한다.
- DoD: 변경 범위가 `docs/**`로만 한정됨(코드/템플릿/CSS diff 없음), `./gradlew build`/`./gradlew test` 결과에 영향 없음(문서 전용 변경), PR 리뷰로 4개 문서의 신규/변경 문구 확인.

### P13-T1. 공개 공통 Layout/Header/Footer
- 의존성: P13-T0
- 산출물: `src/main/resources/templates/home/layout/default.html`, `.../home/layout/header.html`, `.../home/layout/footer.html`, 기존 `home/index.html`, `home/page/detail.html`, `home/program/list.html`, `home/program/detail.html`, `home/board/list.html`, `home/board/detail.html`(root `<html>`을 layout fragment 참조로 교체)
- 작업 내용: `admin/layout/default.html`과 동일한 순수 Thymeleaf fragment 패턴(`th:fragment="layout(content)"` / `th:replace`, 신규 라이브러리 미사용)으로 공개 화면 공통 Header(모바일 햄버거 포함)/Footer를 구성한다. 기존 id(`#quick-menu`, `#greeting`, `#latest-notices`, `#latest-gallery`, `#program-shortcut`, `#program-type-filter`, `#program-list`, `#board-type-filter`, `#board-list`, `#apply-link`, `#attachment-link`, `#prev-page`, `#next-page`)와 태그 구조(`ul`/`li`/`a`)는 그대로 유지한다.
- DoD: 기존 `HomeControllerTest`, `ProgramViewControllerTest`, `BoardViewControllerTest`, `PageViewControllerTest` 무변경 통과. `frontend-tests/visual-regression.spec.js` 375/768/1440 통과. 모바일 뷰포트에서 햄버거 토글 시 내비게이션이 노출되는 Playwright 케이스 신규 추가.

### P13-T2. 메인 Hero/연구소 소개/Program 카드/CTA + HomeController Program 데이터 연결
- 의존성: P13-T0, P13-T1
- 산출물: `src/main/java/com/monicalab/home/controller/HomeController.java`(`ProgramService` 주입, `latestPrograms` model attribute 추가), `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`(케이스 추가), `home/index.html`, `static/css/home.css`
- 작업 내용: Banner를 Hero로, 인사말(GREETING)을 연구소 소개 섹션으로 구성한다. 기존 `ProgramService.getPublicList(null, null, createdAt desc pageable)`을 재사용해 **공개 가능한 프로그램을 최신순 최대 3건**만 카드로 노출한다. `recruitStatus` 기준 서버 측 필터링은 추가하지 않고, 응답에 이미 포함된 `recruitStatus` 값으로 카드에 상태 배지만 표시한다. 프로그램이 0건이어도 완성된 레이아웃의 empty state를 표시한다. 하단 CTA 섹션을 추가한다.
- DoD: `HomeControllerTest`에 "프로그램 최대 3건 카드 노출 / 4건 이상 등록 시에도 정확히 3건만 노출 / 0건 시 empty state" 케이스 추가 후 통과, 기존 케이스 무변경 통과. Banner/Program/Greeting 각각 0건일 때도 레이아웃 깨짐 없이 empty state 렌더 확인.

### P13-T3. 메인 Notice/Gallery
- 의존성: P13-T0, P13-T1, P13-T2
- 산출물: `home/index.html`, `static/css/home.css`
- DoD: `#latest-notices`/`#latest-gallery` 구조 유지, `HomeControllerTest` 무변경 통과, 0건 empty state 확인.

### P13-T4. Program 목록/상세 UI
- 의존성: P13-T0, P13-T1
- 산출물: `home/program/list.html`, `home/program/detail.html`, `static/css/home.css`
- 작업 내용: Sub Page 공통 Hero/Breadcrumb 적용. `program/detail.html`에 현재 누락된 viewport meta가 P13-T1 layout 적용으로 자동 해결됨을 확인한다.
- DoD: `ProgramViewControllerTest` 무변경 통과, "등록된 프로그램이 없습니다" empty state 유지, Playwright 반응형 통과.

### P13-T5. Board NOTICE/ARCHIVE 목록/상세 UI
- 의존성: P13-T0, P13-T1
- 산출물: `home/board/list.html`, `home/board/detail.html`, `static/css/home.css`
- DoD: `BoardViewControllerTest` 무변경 통과, `#board-list li` 구조 유지, Sub Page Hero/Breadcrumb 적용 확인.

### P13-T6. Gallery 목록/상세 UI
- 의존성: P13-T0, P13-T1, P13-T5
- 산출물: `home/board/list.html`, `home/board/detail.html`(`boardType == 'GALLERY'` 조건부 카드 그리드 스타일), `static/css/home.css`
- 작업 내용: `#board-list`의 `ul`/`li` 구조를 유지한 채 GALLERY일 때만 CSS grid로 카드형 레이아웃을 적용한다(DOM 재구성 없이 스타일로만 구현).
- DoD: `BoardViewControllerTest` 무변경 통과, thumbnail이 없는 게시물에 대한 placeholder/empty 처리 확인.

### P13-T7. 전체 반응형/모바일 메뉴/접근성 및 회귀 검증
- 의존성: P13-T1, P13-T2, P13-T3, P13-T4, P13-T5, P13-T6
- 산출물: `frontend-tests/visual-regression.spec.js`(케이스 보강), `static/css/home.css`(최종 다듬기)
- DoD: 6개 공개 페이지 × 375/768/1440 Playwright 전체 통과, `./gradlew build`/`./gradlew test` 전체 통과, 수동 접근성 점검(포커스 순서, 명도 대비, 이미지 alt, 폼 label) 체크리스트 통과, 기존 `frontend-tests/lighthouserc.js`(`/admin/login`) 점수 회귀 없음 확인.

### P13-T8. Hero 배너 캐러셀 문서 계약 확정
- 의존성: P13-T2, P13-T7
- 산출물: `docs/PRD.md`, `docs/FEATURES.md`, `docs/TASK.md`(본 항목), `docs/ARCHITECTURE.md`(Home 섹션), `docs/API.md`(Banner `sortOrder` 필드), `docs/ERD.md`(Banner `sort_order` 컬럼)
- 작업 내용: 코드/템플릿/CSS/JS 변경 없이 문서 계약만 갱신한다. `PRD.md`에는 공개 홈페이지에서 복수 활성 배너를 Hero 캐러셀로 노출한다는 사용자 요구사항을 추가하고, `FEATURES.md` 홈페이지 메인 기능 목록에는 캐러셀 기능(복수 노출/자동전환/수동전환)을 반영한다. `ARCHITECTURE.md` Home 섹션에는 `HomeController`가 전체 `banners`를 프론트에 그대로 넘기고 `static/js/home/hero-carousel.js`가 표시 상태/자동전환/접근성을 전담하는 구조를 명시한다. `API.md`/`ERD.md`에는 `sortOrder`(=`sort_order`)가 공개 메인 캐러셀 노출 순서이며 값이 작을수록 먼저 노출됨을 명시한다.

  메인 Hero 캐러셀 동작 계약을 다음과 같이 확정한다.
  1. `isVisible=true`인 배너 전체를 `sortOrder ASC, createdAt DESC` 순으로 캐러셀에 배치한다.
  2. 최초 진입 시 정렬 결과의 첫 번째 배너를 표시한다.
  3. 배너가 2개 이상이면 5초 주기로 자동 전환한다.
  4. 이전/다음 버튼을 제공한다.
  5. 배너 위치를 나타내는 인디케이터를 제공한다.
  6. 명시적인 자동재생 일시정지/재생 컨트롤을 제공한다.
  7. 캐러셀에 마우스 hover 또는 키보드 focus가 있는 동안 자동 전환을 일시정지한다.
  8. `prefers-reduced-motion: reduce` 환경에서는 자동 재생을 시작하지 않는다.
  9. 배너가 1개면 자동전환·이전/다음 버튼·인디케이터·재생 컨트롤을 모두 숨기고 정적으로 표시한다.
  10. 배너가 0개면 기존 `hero__empty` 상태를 그대로 유지한다.
  11. 키보드로 이전/다음 및 각 컨트롤(재생/일시정지, 인디케이터)을 조작할 수 있어야 하며, 각 컨트롤에는 접근 가능한 이름(`aria-label` 등)을 제공한다.
  12. 비활성 슬라이드 내부의 링크/컨트롤은 키보드 focus 대상이 되지 않도록 처리한다(예: `inert` 또는 `tabindex="-1"`).
  13. 인디케이터는 `role="tablist"` 등 복잡한 패턴을 강제하지 않고, `button` 기반의 단순하고 접근 가능한 구현을 우선한다.
- DoD: 변경 범위가 `docs/**`로만 한정됨(코드/템플릿/CSS/JS diff 없음), `./gradlew build`/`./gradlew test` 결과에 영향 없음(문서 전용 변경), PR 리뷰로 6개 문서의 신규/변경 문구 확인.

### P13-T9. Hero 배너 캐러셀 구현
- 의존성: P13-T8
- 산출물: `home/index.html`, `static/css/home.css`, `static/js/home/hero-carousel.js`(신규), `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `frontend-tests/*.spec.js`
- 작업 내용: P13-T8에서 확정한 계약대로 Hero 캐러셀을 구현한다. `Controller`/`Service`/`Repository`/API 동작은 변경하지 않는 것을 기본 원칙으로 한다(이미 전체 배너를 올바른 순서로 제공하고 있음). 기존 `heroShowsOnlyFirstPublicBannerWhenMultiplePublicBannersExist` 테스트는 "복수 배너 등록 시 첫 번째만 노출"이라는 낡은 단일 노출 계약을 고정하고 있으므로, 복수 배너 전체가 캐러셀로 렌더링됨을 검증하는 테스트로 교체한다.
- DoD:
  - `HomeControllerTest`: `heroShowsOnlyFirstPublicBannerWhenMultiplePublicBannersExist`를 배너 N개 등록 시 전체 슬라이드가 렌더링됨을 검증하는 테스트로 교체, 배너 1개일 때 이전/다음·인디케이터·재생 컨트롤이 렌더링되지 않음을 검증하는 케이스 추가, 기존 `heroShowsEmptyStateWhenNoBannersExist` 무변경 통과.
  - 신규 Playwright 케이스: 배너 2개 이상 시 5초 자동전환, 이전/다음 버튼 클릭 이동, 인디케이터 클릭 이동, 재생/일시정지 컨트롤 토글, hover/focus 시 자동전환 정지, `prefers-reduced-motion` 에뮬레이션 시 자동전환 미시작, 키보드(방향키/Tab)로 컨트롤 조작 가능, 비활성 슬라이드 내부 링크가 Tab 포커스에서 제외됨을 확인.
  - 기존 `frontend-tests/visual-regression.spec.js` 375/768/1440 전체 통과.
  - `./gradlew build`/`./gradlew test` 전체 통과, Node 관리자 JS 테스트 무변경 통과.
  - Docker 8088 수동 확인(배너 2개 이상 등록 후 자동전환/버튼/인디케이터/재생 컨트롤 동작 육안 확인).

### P13-T10. 공개 팝업 레이어 UI 계약
- 의존성: P13-T1, P13-T9
- 산출물: `docs/PRD.md`, `docs/FEATURES.md`, `docs/TASK.md`(본 항목), `docs/ARCHITECTURE.md`(Home 섹션)
- 작업 내용: 코드/템플릿/CSS/JS/테스트 변경 없이 문서 계약만 갱신한다. 현재 공개 메인(`/`)의 Popup은 `home/index.html`이 제목만 본문 흐름에 나열하는 최소 placeholder 상태이며(P8-T1 이후 P13의 어떤 태스크에서도 다뤄진 적 없음), 이번 태스크에서 실제 공개 팝업 UX를 문서로 확정한다. `docs/PRD.md`/`docs/FEATURES.md` 홈페이지 기능 목록에 팝업이 상단 floating 카드로 노출됨을 반영하고, `docs/ARCHITECTURE.md` Home 섹션에는 `HomeController`가 `PopupService.getPublicList()`가 반환한 공개 노출 대상 Popup 전체를 `popups` 모델 속성으로 그대로 전달하며 서버는 1건으로 자르거나 표시 상태를 관리하지 않는다는 것, 실제 표시 개수·순서·닫기·오늘 하루 보지 않기 등 UI 상태는 프론트 `static/js/home/popup-modal.js`가 전담한다는 구조를 명시한다. `docs/API.md`는 `PopupResponse.content`와 공개 조회 조건(`isVisible=true` + 노출기간)이 이미 충분히 정의돼 있어 변경하지 않는다.

  > **P13-T11 구현 중 재정정**: 아래 1, 6, 7번은 최초 확정본(중앙 모달 오버레이 + 순차 표시 + 배경 차단)에서
  > 비차단형 "최대 3개 동시 floating 카드 + 보충" 방식으로 변경됐다. 2~5, 9번은 실질적으로 그대로 유지되며,
  > 8번은 카드 크기/오프셋 수치만 보완됐다. `docs/ARCHITECTURE.md` Home 섹션의 "서버는 전체를 그대로 넘기고
  > 프론트가 표시 상태를 전담한다"는 구조 원칙은 이 재정정과 그대로 부합하므로 변경하지 않았다.

  공개 팝업 레이어 UI 계약을 다음과 같이 확정한다.
  1. visible이며 노출기간 내인 Popup 중 `createdAt DESC` 기준 **최대 3개까지 동시에** 화면 상단에 floating 카드로 표시한다. 배경 콘텐츠 위에 떠 있지만 배경을 차단하지 않는다(비차단형 - 아래 6, 7번 참고).
  2. 제목과 관리자 CKEditor content 전체(기존 `PopupResponse.content` + `HtmlSanitizer` 처리 결과 그대로)를 표시한다. 새 Popup 전용 API/DTO를 만들지 않고, 인사말(GREETING)이 이미 쓰는 `th:utext` 패턴을 재사용한다.
  3. content 안 이미지(`<img src="/api/files/...">`)는 원본 비율을 유지한 채 팝업 폭을 넘지 않도록 반응형으로 표시하며, 모바일에서 가로 overflow를 만들지 않는다.
  4. 명확한 `닫기` 버튼을 제공하고, `ESC` 키로도 팝업을 닫을 수 있다(닫는 대상은 7번 참고).
  5. Popup별 "오늘 하루 보지 않기"를 제공한다. "24시간 숨김"이 아니라 **브라우저 로컬 날짜 기준으로 그 날짜 동안만** 해당 Popup ID를 숨기며, 브라우저의 날짜가 바뀌면 다시 표시 대상이 될 수 있다. 서버 저장 없이 `localStorage`에 Popup ID별로 독립 저장하고, 다른 브라우저/기기와 상태를 공유하지 않는다.
  6. visible이며 기간 내인 Popup이 여러 건이면 최신순으로 최대 3개까지 동시에 표시한다(순차 표시 아님). 화면에 보이는 Popup 하나를 `닫기` 또는 `오늘 하루 보지 않기`로 제거하면, 아직 표시되지 않은 다음 대기 Popup이 있을 경우 그 자리를 채워 다시 최대 3개를 유지한다(더 이상 대기 Popup이 없으면 남은 Popup만 유지). `닫기`는 이번 방문에서만 유효하고(새로고침하면 다시 노출 대상이 될 수 있음), `오늘 하루 보지 않기`만 당일 재방문에도 유지된다. 각 Popup의 닫기/오늘 하루 보지 않기는 다른 Popup 상태에 영향을 주지 않는다. 프론트는 서버가 정렬한 `createdAt DESC` 순서를 그대로 사용하고 별도 재정렬하지 않는다. 모두 닫히면 Popup 영역 자체가 사라진다.
  7. 접근성: `role="dialog"`는 유지하되 배경을 차단하지 않으므로 `aria-modal`은 쓰지 않는다. Popup이 열릴 때 강제로 포커스를 이동시키지 않고, 닫을 때 별도의 포커스 복원도 하지 않는다(배경이 계속 상호작용 가능한 상태이므로 포커스를 임의로 옮기면 오히려 방해가 된다) - 각 닫기 버튼은 명확한 접근 가능한 이름을 갖고 자연스러운 Tab 순서로 도달 가능하면 충분하다. `ESC`는 현재 표시 중인 Popup 가운데 가장 위(=가장 최신) 1건만 닫으며, 반복하면 최신순으로 하나씩 닫힌다. Popup이 떠 있는 동안 배경 페이지의 스크롤/클릭을 막지 않는다(배경 스크롤 잠금 없음). Tab 순환을 팝업 내부로 제한하는 완전한 focus trap은 이 태스크의 필수 계약으로 요구하지 않는다.
  8. 반응형: 데스크톱/태블릿은 헤더와 적절한 여백(`max(96px, 뷰포트 높이의 12%)`)을 둔 화면 수평 중앙 부근을 기준으로, 최신 Popup이 z-index 최상단에 오고 다음 Popup일수록 오른쪽/아래로 40px 안팎 offset을 두어 완전히 겹치지 않게 배치한다(24px는 뒤쪽 카드의 버튼이 앞쪽 카드에 가려 클릭이 안 되는 문제가 있어 40px로 확정). 각 Popup 헤더는 드래그 핸들이며, Pointer Events(별도 라이브러리 없이 vanilla JS)로 사용자가 원하는 위치로 옮길 수 있다. 드래그로 잡은 Popup은 즉시 다른 Popup보다 z-index 최상단이 되고, 옮긴 위치는 화면 밖으로 나가지 않게 clamp되며, 다른 Popup이 닫히거나 보충돼도 되돌아가지 않고 해당 방문 동안 유지된다(localStorage 저장은 하지 않음 - 새로고침하면 기본 배치로 복귀). 375px 등 480px 미만 좁은 화면에서는 가로 offset과 드래그 모두 비활성화하고 세로 offset만 사용해 가로 overflow와 스크롤 제스처 충돌이 절대 발생하지 않게 한다. content가 길면 화면 전체 높이를 넘기지 않도록 팝업 내부 스크롤을 허용한다. 375/768/1024/1440 기준으로 레이아웃이 안정적이어야 한다.
  9. Popup의 `isVisible`/`startDate`/`endDate` 공개 조회 조건, `PopupService`/`HomeController` 흐름, 관리자 CRUD 계약은 모두 기존 그대로 유지한다. 이 문서 태스크에서 타임존 설정(`docker-compose.yml`의 `TZ`)은 건드리지 않는다.
- DoD: 변경 범위가 `docs/**`로만 한정됨(코드/템플릿/CSS/JS/테스트 diff 없음), `./gradlew build`/`./gradlew test` 결과에 영향 없음(문서 전용 변경), PR 리뷰로 4개 문서의 신규/변경 문구 확인. (P13-T11 재정정 시점에는 구현과 같은 PR로 병합됨 - 아래 P13-T11 DoD 참고.)

### P13-T11. 공개 팝업 레이어 UI 구현
- 의존성: P13-T10
- 산출물: `home/index.html`, `static/css/home.css`, `static/js/home/popup-modal.js`(신규), `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `src/test/js/home/*.test.js`(신규), `frontend-tests/*.spec.js`
- 작업 내용: P13-T10에서 확정한 계약대로 공개 팝업 레이어 UI를 구현한다. `PopupService`/`HomeController`/API/DTO는 변경하지 않는 것을 원칙으로 한다(`PopupResponse.content`가 이미 모델까지 전달되고 있음). 기존 `#popups` id는 그대로 유지해 `homeReturns200WithAllRequiredAreasAndFixedQuickMenuLinks`(`#popups` 존재 확인)와 `homeRendersGreetingContentAsUnescapedHtmlAndListsDomainData`(`#popups` 텍스트에 제목 포함 확인) 두 기존 테스트가 구조 변경 없이 계속 통과하도록 한다.
- DoD:
  - 기존 `#popups` id 유지, `PopupService`/`HomeController`/API/DTO 무변경을 코드 리뷰로 확인.
  - `HomeControllerTest`: content/이미지가 실제 마크업에 렌더링됨을 검증하는 케이스(`role="dialog"`는 확인하되 `aria-modal`은 없음을 확인), 여러 Popup 등록 시 전부 DOM에 SSR 시점부터 hidden 상태로 존재함을 검증하는 케이스 추가. 기존 두 테스트 무변경 통과.
  - `src/test/js/home/popup-modal.test.js`(신규): 오늘 하루 보지 않기의 브라우저 로컬 날짜 비교 순수 로직, 최대 3개 노출 대상 선정(닫힌 것 제외하고 다음 대기로 보충) 순수 로직, `clampPosition`(viewport 경계값 포함)/`computeDefaultPosition`(중앙 기준·rank별 offset·모바일 세로 전용)/`currentTopmostPopupId`(드래그로 순서가 바뀌어도 실제 최상단 판정) 순수 로직 단위 테스트.
  - 신규 Playwright 케이스: 최신 3개 동시 표시 + 4번째는 최초 hidden, 제목/content/이미지 렌더링, 하나를 닫으면 다음 대기 Popup으로 보충되어 다시 3개 유지, 오늘 하루 보지 않기도 동일하게 보충되고 새로고침 후에도 유지, 개별 닫기가 다른 Popup에 영향 없음, `ESC`로 최상단 1건만 닫히고 반복 시 최신순으로 닫힘, Popup이 떠 있어도 배경 스크롤/배경 링크 클릭이 가능함, 최신 Popup의 z-index가 가장 높음, 1024/1440에서 수평 중앙 배치 확인, 데스크톱 40px offset 확인, 헤더 드래그로 위치 이동, 닫기/오늘 하루 보지 않기 버튼 위 pointerdown은 드래그로 처리되지 않음, 드래그 시 즉시 z-index 최상단, viewport 밖으로 드래그해도 clamp, 드래그 후 다른 Popup이 닫히고 보충돼도 위치 유지, 375px에서 드래그 비활성화, 375/768/1024/1440 overflow 없음, 실제 CKEditor 업로드 이미지가 공개 화면에 렌더링됨.
  - 기존 `frontend-tests/visual-regression.spec.js` 전체 통과.
  - `./gradlew build`/`./gradlew test` 전체 통과, 관리자 Node 테스트 무변경 통과.
  - Docker 8088 수동 확인(CKEditor 이미지 포함 팝업 실제 노출/닫기/오늘 하루 보지 않기/드래그 육안 확인, 배경 스크롤/클릭이 막히지 않음을 육안 확인).

---

### P13-T12. 메인 섹션 제목 링크화 + Program 목록 썸네일
- 의존성: P13-T3, P13-T4
- 산출물: `home/index.html`, `home/program/list.html`, `static/css/home.css`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `src/test/java/com/monicalab/program/controller/ProgramViewControllerTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 코드/문서 두 가지를 다룬다. Controller/Service/DTO는 변경하지 않는다.
  1. 메인 `#latest-programs`/`#latest-notices`/`#latest-gallery`의 `<h2 class="section-title">`를 유지한 채 그 안에 `<a class="section-title__link">`를 배치해 각각 `/programs`, `/boards?boardType=NOTICE`, `/boards?boardType=GALLERY`로 이동하게 한다. `#latest-notices`/`#latest-gallery`에 있던 `.section__more`("전체보기") 링크는 제거하고, `#latest-programs`에는 원래 없던 전체보기를 새로 만들지 않는다. `#program-shortcut` CTA는 건드리지 않는다.
  2. `home/program/list.html`의 `#program-list`(`ul > li > a`, P13-T1 태그 구조 유지 계약 대상)는 최상위 구조를 그대로 두고, 각 `a` 내부에 썸네일(`ProgramResponse.thumbnail`, 기존 데이터 그대로 재사용)과 제목, 기존 타입/상태 뱃지를 배치하는 리스트형으로 개선한다(카드형 grid로 재구성하지 않음). 썸네일이 null/빈 문자열/공백만 있는 문자열이어도 항상 placeholder로 대체되어 레이아웃이 깨지지 않게 하며(`#strings.isEmpty(#strings.trim(...))`, 둘 다 null-safe), 썸네일 영역은 데스크톱/태블릿 112×80px, 375px 부근 모바일 80×60px로 고정하고 `object-fit: cover`로 원본 비율과 무관하게 행 높이를 일정하게 유지한다.
- DoD:
  - `HomeControllerTest`: 3개 섹션 제목 링크의 href를 각각 검증하는 케이스, `#latest-notices`/`#latest-gallery`에 `.section__more`가 더 이상 없음을 검증하는 케이스 추가. 기존 `#latest-notices a`/`#latest-gallery a` 범용 selector 사용 테스트는 `.notice-list__link`/`.gallery-card__link` 구체적 selector로 교체(검증 강도 약화 아님, 다른 기존 테스트에 이미 쓰인 selector와 통일). 나머지 기존 테스트 무변경 통과.
  - `ProgramViewControllerTest`: `#program-list`가 `ul > li > a` 구조를 유지한 채 `a` 내부에 썸네일/제목/기존 타입·상태 정보를 렌더링함을 검증하는 케이스, 썸네일 null/빈 문자열/공백 문자열 각각에서 placeholder가 표시됨을 검증하는 케이스(직접 `programRepository.saveAndFlush`로 실제 저장 가능한 데이터만 사용) 추가. 기존 테스트 무변경 통과.
  - 신규 Playwright 케이스: 메인 3개 섹션 제목 클릭 시 각 목록 페이지로 이동, "전체보기" 텍스트가 더 이상 존재하지 않음, `/programs` 목록에서 썸네일 있는/없는 프로그램이 각각 img/placeholder로 표시됨, 375/768/1024/1440에서 프로그램 목록에 overflow가 없음. 기존 "공백 없는 긴 제목이 있어도 가로 스크롤이 생기지 않는다"(`/programs`) 테스트는 새 마크업에서도 그대로 통과.
  - `./gradlew build`/`./gradlew test` 전체 통과, Node 전체(홈/관리자) 무변경 통과, `frontend-tests/visual-regression.spec.js` 전체 통과.
  - Docker 8088 수동 확인(375/768/1024/1440에서 메인 섹션 제목 링크와 `/programs` 썸네일 목록 육안 확인).

---

### P13-T13. 공개 Popup 제목 영역 시인성 개선
- 의존성: P13-T11
- 산출물: `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: `.popup-modal__header`가 `.popup-modal__body`와 동일한 흰색(`--color-bg`) 배경을 써서 시각적으로 구분되지 않던 문제를, 기존 디자인 토큰(`--color-surface`, `--color-border`, `--radius`)만 재사용해 개선한다. `.popup-modal__header`에 옅은 배경(`background: var(--color-surface)`)과 구분선(`border-bottom: 1px solid var(--color-border)`)을 추가하고, 카드 전체의 `border-radius: var(--radius)`와 자연스럽게 이어지도록 상단 두 모서리에만 동일한 radius(`border-radius: var(--radius) var(--radius) 0 0`)를 적용한다. 새 CSS 변수·새 색상값·opacity/투명 효과는 추가하지 않는다. popup DOM 구조, `popup-modal.js`, dialog 접근성(`role`/`aria-labelledby`/focus trap/ESC/focus restore/background scroll lock), 닫기 버튼 동작, "오늘 하루 보지 않기", 다중 Popup 순차 표시/offset 로직은 변경하지 않는다.
- DoD:
  - `.popup-modal__header`가 `.popup-modal__body`(또는 카드 배경)와 시각적으로 구분되는 배경을 가진다. 기존 Java Popup 관련 테스트(DOM/속성 검증)는 무변경 통과.
  - 신규 Playwright 케이스: `.popup-modal__header`의 computed `backgroundColor`가 흰색 배경(카드/본문)과 실제로 다름을 검증. CSS 구현 세부값(`border-bottom` 존재 여부 등)까지 과도하게 고정하지 않고, "header가 본문과 시각적으로 구분된다"는 동작 수준까지만 검증한다.
  - 기존 "공개 Popup 레이어" Playwright describe 전체(드래그, offset, z-index, ESC, 오늘 하루 보지 않기 등) 무변경 통과.
  - `./gradlew build` 성공, Docker 8088 수동 확인(375/768/1024/1440에서 header/body 구분, 상단 radius, 제목/닫기 버튼 대비, 긴 제목 2줄 이상, 다중 Popup 겹침, 드래그 후 스타일 유지, 기존 상호작용 회귀 없음).

---

### P13-T14. 게시판/프로그램 목록 필터 · UI · 페이지네이션 개선
- 의존성: P13-T5, P13-T12
- 산출물: `home/board/list.html`, `home/program/list.html`, `home/fragments/pagination.html`(신설), `static/css/home.css`, `board/controller/BoardViewController.java`, `program/controller/ProgramViewController.java`, `common/util/PaginationSupport.java`(신설), `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `src/test/java/com/monicalab/program/controller/ProgramViewControllerTest.java`, `src/test/java/com/monicalab/common/util/PaginationSupportTest.java`(신설), `frontend-tests/visual-regression.spec.js`
- 작업 내용: Controller만 변경하고 Service/Repository/DTO는 변경하지 않는다.
  1. `BoardViewController`/`ProgramViewController`(공개 View 컨트롤러 2곳만, admin/REST API 컨트롤러는 무변경)의 `@PageableDefault` size를 20 → 10으로 변경한다.
  2. `#board-type-filter`/`#program-type-filter`의 `[전체]`/`[공지사항]` 같은 대괄호 텍스트 방식을 제거하고, `boardType`/`programType` 쿼리 파라미터 기준 `th:classappend`로 `is-active` 클래스를 붙이는 방식으로 바꾼다(기존 `.hero__indicator.is-active` 네이밍 재사용). 기본 링크 색상은 검정 계열, active는 bold+살짝 큰 글자, hover/focus-visible에서 밑줄을 제공한다. 두 필터는 옵션 개수·라벨이 서로 달라 공통 fragment로 묶지 않고 `.filter-nav`/`.filter-nav__link` CSS 클래스만 공유한다.
  3. `#board-list`(`ul>li>a` 구조, P13-T1 계약 유지 대상)는 P13-T12에서 `#program-list`에 적용한 것과 동일한 원칙(`ul`/`li`/`a` 골격은 유지하고 `a` 내부에 요소 추가)으로, 게시판 분류명(`badge`)/제목/"조회 N"을 한 줄에, 작성일시(`BoardResponse.createdAt()`, 기존 데이터 그대로 재사용)를 아래 보조 줄에 표시한다. 제목 기본 색상은 검정 계열, `a` 전체가 클릭 영역이다. `#program-list`의 P13-T12 아이템 구조(썸네일/제목/타입·상태 뱃지)는 이번 Task에서 재구성하지 않는다.
  4. Board/Program이 공유하는 `home/fragments/pagination.html`(신설) fragment로 `#pagination`(id 자체는 P13-T1 계약 대상이 아니라 자유롭게 재설계, 단 `#prev-page`/`#next-page` id는 유지)을 교체한다. 페이지 번호를 최대 10개 단위로 그룹 표시(`#numbers.sequence(page/10*10, min(page/10*10+9, totalPages-1))`)하고, 이전/다음/현재 페이지 `is-active`를 제공하며, 전체를 하단 가운데 정렬한다. `boardType`/`programType`/`keyword`는 모든 링크·폼에서 유지한다. Map 기반 파라미터 전달은 쓰지 않고(null 값을 허용하지 않는 `Map.of`의 위험을 피하기 위해) `boardType`/`programType`/`keyword`를 fragment의 개별 named parameter로 전달한다(Thymeleaf `@{}`가 null 파라미터를 빈 값으로 안전하게 렌더링하고 컨트롤러가 이를 정상적으로 null로 바인딩함을 실측 확인).
  5. 직접 페이지 이동: 1-based `pageJump` 문자열 쿼리 파라미터(Spring의 0-based `page`와 별도) + GET `<form>`(JS 없음)으로 구현한다. 신설된 `common/util/PaginationSupport.resolve(Pageable, String pageJump, Function<Pageable, PageResponse<T>>)`가 파싱/clamp를 전담한다: `pageJump`가 없으면 원래 조회 결과를 그대로 반환하고(기존 `?page=` 요청의 동작 범위를 넓히지 않음), 있으면 1 이하는 첫 페이지로, 숫자가 아니면(Integer.MIN_VALUE 등 오버플로 경계값 포함, `requested <= 1 ? 0 : requested - 1`로 뺄셈 전에 분기해 오버플로를 피함) 원래 `Pageable`을 그대로 사용해 Spring 바인딩 오류·JSON 에러 없이 항상 200 OK HTML을 반환하며, totalPages를 초과하면 마지막 유효 페이지로 1회 재조회한다(`totalPages == 0`이면 이 재조회 자체를 하지 않아 안전하다).
- DoD:
  - `BoardViewControllerTest`/`ProgramViewControllerTest`: 기본 size=10, active filter(전체 포함), Board 목록의 분류명/제목/조회수/작성일시 렌더링(Program 목록 구조 회귀 없음), 페이지 번호 1~10 그룹과 11페이지 이상에서의 그룹 전환(`size=1` override로 15건만으로 재현, 141건 등 불필요하게 큰 fixture 생성 금지), boardType/programType/keyword 유지(기존 prev/next 테스트 무변경 통과), `pageJump` 정상/1 이하/초과/숫자 아님/데이터 0건 각각에서 200 OK 유지 케이스 추가.
  - `PaginationSupportTest`(신규): Spring 컨텍스트 없는 순수 단위 테스트로 `pageJump` 없음/공백, 1 이하 clamp, `Integer.MIN_VALUE` 오버플로 미발생, totalPages 초과 clamp, 숫자 아님(정수 초과 포함)/소수점 무시, totalPages=0 안전, pageJump 없는 `?page=999` 요청은 clamp되지 않음(범위 확장 금지 확인)을 모두 검증.
  - 신규 Playwright 케이스: Board 필터 active 시각 상태(computed font-weight), Board 목록 UI(분류명/제목/조회수 한 줄, 작성일시 아래 줄) boundingBox 확인, Board/Program pagination 중앙 정렬과 현재 페이지 `is-active`, `pageJump` 폼 제출 후 URL이 `pageJump=2`를 유지한 채(리다이렉트 설계가 아니므로 `page=1`로 바뀌는 것을 기대하지 않음) 2페이지 active/내용이 표시됨, 375/768/1024/1440에서 Board/Program 목록+pagination에 overflow 없음. 기존 P13-T12 썸네일/`ul>li>a` 회귀, 긴 제목 회귀 describe는 무변경 통과.
  - `./gradlew build`/Playwright 전체 통과, Docker 8088 수동 확인(375/768/1024/1440에서 필터 active, 목록 UI, pagination 그룹/직접 이동).

> **P13-T19 재정정**: 위 DoD의 "Board 목록의 분류명/제목/**조회수**/작성일시 렌더링"과 "Board 목록 UI(분류명/
> 제목/**조회수** 한 줄, 작성일시 아래 줄)" 요구는 P13-T19에서 조회수 기능 자체가 완전히 제거되면서
> supersede됐다. `#board-list` 아이템의 분류명/제목/작성일시 구조(및 P13-T18의 첨부파일/썸네일
> 미리보기와 무관한 부분)는 조회수 표시만 빠진 채 그대로 유지된다.

---

### P13-T15. 공개 Popup 제목 영역 배경색 개선
- 의존성: P13-T13
- 산출물: `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: P13-T13에서 적용한 `.popup-modal__header`의 `background: var(--color-surface)`(`#f8f9fa`, 흰색 계열)가 여전히 `.popup-modal__body`(`--color-bg`, 흰색)와 시각적으로 뚜렷이 구분되지 않는다는 피드백에 따라, 흰색 계열이 아닌 불투명 배경으로 교체한다. `.popup-modal__header`의 `background`를 `var(--color-text)`(진한 잉크색)로, `.popup-modal__title`의 `color`를 `var(--color-text)`에서 `var(--color-primary-contrast)`(흰색)로 교체한다. 새 CSS 변수·새 색상값·opacity/투명 효과는 추가하지 않고 기존 토큰만 재사용한다. `padding`/`border-bottom`(`--color-border`)/`border-radius`(`--radius`)/`.popup-modal__close`(`--color-text-muted`, 미변경)/`.popup-modal__body`/`.popup-modal__hide-today`/popup DOM 구조/`popup-modal.js`/dialog 접근성/닫기 버튼 동작/"오늘 하루 보지 않기"/다중 Popup 순차 표시·offset 로직은 변경하지 않는다.
- DoD:
  - `.popup-modal__header`가 흰색 계열이 아닌 불투명 배경을 가지며 `.popup-modal__body`(카드 배경)와 시각적으로 구분된다. 기존 Java Popup 관련 테스트(DOM/속성 검증)는 무변경 통과.
  - 기존 Playwright 케이스(`headerBackground !== cardBackground`)는 그대로 유지해 통과시킨다.
  - 신규 Playwright 케이스: `.popup-modal__header`의 computed `backgroundColor`와 `.popup-modal__title`의 computed `color` 간 WCAG 상대휘도 기준 대비비를 계산해 4.5:1 이상임을 검증한다(exact hex 하드코딩 금지). 보조적으로 헤더 배경이 흰색(`rgb(255, 255, 255)`)이 아님을 느슨하게 확인한다.
  - 기존 "공개 Popup 레이어" Playwright describe 전체(드래그, offset, z-index, ESC, 오늘 하루 보지 않기 등) 무변경 통과.
  - `./gradlew build` 성공, Docker 8088 수동 확인(375/768/1024/1440에서 header/body 구분, 제목/닫기 버튼 대비, 긴 제목 2줄 이상, 다중 Popup 겹침, 드래그 후 스타일 유지, 기존 상호작용 회귀 없음).

---

### P13-T16. 게시판 강의 후기 유형 추가 및 메인/메뉴 노출
- 의존성: P13-T12, P13-T14
- 산출물: `board/entity/BoardType.java`, `home/controller/HomeController.java`, `home/index.html`, `home/board/list.html`, `home/layout/header.html`, `home/layout/footer.html`, `admin/board/list.html`, `admin/board/form.html`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `src/test/java/com/monicalab/board/controller/AdminBoardControllerTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 관리자가 외부에서 받은 강의 후기(주로 이미지)를 등록해 홍보하고 방문자는 조회만 하는 기능을 신규 Entity/API 없이 기존 `Board` 도메인 확장으로 구현한다. 별점/댓글/사용자 작성/승인 대기/작성자 인증 등은 구현하지 않는다.
  1. `BoardType`에 `REVIEW`를 추가한다(`NOTICE, GALLERY, ARCHIVE, REVIEW`). `board_type`이 `VARCHAR(20) NOT NULL`(DB 레벨 CHECK/ENUM 제약 없음)이라 Flyway migration은 필요 없다. `BoardService`/`AdminBoardController`/`BoardController`/`BoardRequest`/`BoardResponse`는 이미 `boardType`에 대해 완전히 제네릭하므로 변경하지 않는다.
  2. `admin/board/list.html`의 검색 필터, `admin/board/form.html`의 등록/수정 폼 `<select>`에 `REVIEW`(`강의 후기`) 옵션을 추가한다. 기존 CKEditor 이미지 업로드, 대표 이미지/첨부파일 업로드(`/api/admin/files`), `isPublic` 공개 여부 CRUD는 그대로 재사용하고 변경하지 않는다.
  3. `HomeController`에 `LATEST_REVIEW_LIMIT = 3`(기존 `LATEST_BOARD_LIMIT = 5`와는 별도 상수) 및 `latestReviewPageable()`을 추가하고, `boardService.getPublicList(BoardType.REVIEW, null, latestReviewPageable())` 결과를 `latestReviews` 모델 속성으로 제공한다.
  4. `home/index.html`의 `#latest-programs` 섹션 바로 다음, `#latest-notices` 섹션 이전에 `#latest-reviews` 섹션을 추가한다. `#latest-gallery`의 기존 `.gallery-grid`/`.gallery-card__*` class를 그대로 재사용하고(신규 CSS 없음), section-title 링크는 `/boards?boardType=REVIEW`로 연결한다. 후기가 없으면 기존 `empty-state` 패턴을 재사용한다.
  5. `home/board/list.html`의 `#board-type-filter`에 "강의 후기"(`boardType=REVIEW`) 앵커를 추가한다. 기존 P13-T14의 active 판정(`th:classappend` + `boardType.name()` 비교) 및 `keyword` 쿼리 파라미터 유지 계약은 그대로 따른다.
  6. `home/layout/header.html`의 `#quick-menu`, `home/layout/footer.html`의 `.site-footer__nav`에 "프로그램" 다음, "게시판" 이전 순서로 "강의 후기"(`/boards?boardType=REVIEW`) 링크를 추가한다. `home/program/list.html`의 `#program-type-filter`(COURSE/SPECIAL, P13-T14 계약 대상)는 절대 변경하지 않으며, `Program` 엔티티에 후기 데이터를 넣지 않는다.
- DoD:
  - `BoardType.REVIEW`로 관리자 등록/수정/삭제/공개 전환이 기존 `/api/admin/boards`로 정상 동작하고, `/boards?boardType=REVIEW`가 공개 목록/상세를 정상 렌더링하며 비공개 후기는 노출되지 않는다.
  - 메인 `/`에서 `#latest-programs` 바로 다음에 `#latest-reviews`가 위치하고 최신 3건(`LATEST_REVIEW_LIMIT`)만 노출되며, 0건이면 empty-state가 표시된다.
  - `#quick-menu`/`.site-footer__nav` 모두 `기관소개 → 프로그램 → 강의 후기 → 게시판` 4개 링크를 가지며 "강의 후기"는 `/boards?boardType=REVIEW`로 이동한다. 기존 `homeReturns200WithAllRequiredAreasAndFixedQuickMenuLinks` 테스트와 모바일 햄버거 메뉴 Playwright 테스트는 4개 링크 기준으로 갱신되어 통과한다.
  - `#board-type-filter`에 "강의 후기" 필터가 추가되고 기존 active/쿼리 파라미터 계약대로 동작한다. `#program-type-filter`(COURSE/SPECIAL)는 DOM/코드 무변경.
  - `admin/board/list.html`/`form.html`에서 REVIEW 선택 시 기존 CKEditor/대표 이미지/첨부파일/공개여부 CRUD가 NOTICE/GALLERY/ARCHIVE와 동일하게 동작. 별점/댓글/사용자 작성/승인 대기 관련 코드는 추가되지 않는다.
  - Flyway 신규 migration 파일 없음.
  - Java 테스트: `BoardIntegrationTest`의 기존 `@ParameterizedTest @EnumSource(BoardType.class)` 왕복 테스트가 `REVIEW`도 자동으로 커버함을 활용하고, 등록/수정/삭제/공개 여부 자체의 공통 동작은 기존 Board 테스트가 이미 보장하므로 중복 작성하지 않는다. 대신 (a) `HomeControllerTest`에 `#latest-reviews` 렌더링/최신 3건 제한/썸네일·placeholder/empty-state/section-title 링크 테스트를 신규 추가하고, (b) `AdminBoardControllerTest`에 "관리자가 REVIEW로 등록 → 비공개 상태에서는 공개 목록에 없음 → 공개 전환 → 공개 목록/상세 노출"이라는 핵심 경로 스모크 테스트 1건만 추가한다.
  - Playwright: `#latest-reviews` 섹션 노출 및 위치(`#latest-programs`와 `#latest-notices` 사이), section-title 클릭 시 `/boards?boardType=REVIEW` 이동, footer "강의 후기" 링크 노출, `#board-type-filter`의 REVIEW active 상태, 모바일 햄버거 메뉴의 4개 링크 검증(갱신)까지 신규/수정 테스트로 확인한다. 기존 P13-T12/P13-T14 describe와 `#program-type-filter` 관련 테스트는 무변경 통과.
  - `./gradlew build` 성공, Playwright 전체 통과, Docker 8088 수동 확인(375/768/1024/1440에서 `#latest-reviews` 그리드 overflow 없음, header/footer 4개 링크 표시, `/boards` 필터 nav overflow 없음, REVIEW 상세 이미지가 모바일 폭을 넘지 않음).
  - 게시글 상세 이미지 표시 크기 축소, 기관소개 메뉴 개편은 이번 Task 범위에 포함하지 않는다(별도 후속 Task).

> **P13-T17 재정정**: 위 DoD의 "`#quick-menu`/`.site-footer__nav` 모두 `기관소개 → 프로그램 → 강의 후기 → 게시판`
> 4개 링크를 가지며" 문구 중 1번째 링크는 P13-T17에서 라벨이 `연구소 소개`, 이동 대상이 `/pages/GREETING`에서
> `/pages/INTRODUCTION`으로 변경됐다. 4개 링크의 순서·구조(강의 후기가 3번째)는 그대로 유지된다.

---

### P13-T17. 공개 화면 명칭/네비게이션/홈 구성 정리
- 의존성: P13-T16
- 산출물: `home/layout/header.html`, `home/layout/footer.html`, `home/layout/default.html`, `home/index.html`, `admin/layout/header.html`, `admin/layout/default.html`, `admin/page/list.html`, `home/controller/HomeController.java`, `static/css/home.css`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 공개 화면의 기관 명칭·네비게이션 라벨·홈 구성을 정리한다. 새 Entity/API/DB 컬럼은 만들지 않는다.
  1. 공식 한글 명칭을 `모니카영어교육연구소`로 통일한다. 대상: 공개 Header/Footer 브랜드 텍스트, 공개 페이지 `<title>`/fallback title(`home/layout/default.html`), 관리자 Header("모니카영어교육연구소 관리자"), 공개/관리자 Footer 카피라이트(`&copy; 모니카영어교육연구소` — 공식 영문명이 확인되지 않아 새 영문명을 만들지 않고 기존 한글명으로 교체). `com.monicalab` 패키지/클래스/`MonicaLabHomepageApplication` 등 내부 기술 식별자는 변경하지 않는다.
  2. 공개 Header(`#quick-menu`)/Footer(`.site-footer__nav`)의 "기관소개" 메뉴 라벨을 "연구소 소개"로, 이동 대상을 `/pages/GREETING`에서 `/pages/INTRODUCTION`으로 변경한다. `admin/page/list.html`의 페이지 목록 행 라벨(PageType.INTRODUCTION을 가리키는 고정 UI 문자열)도 "연구소 소개"로 동일하게 통일한다. `PageType.INTRODUCTION`의 DB `title` 데이터, "기관소개 관리"(admin 사이드바/대시보드 quickMenu/CmsPage 도메인 섹션명 전반) 등 도메인 그룹 명칭은 이번 Task에서 변경하지 않는다.
  3. `home/index.html`의 `#greeting`(인사말 요약) 섹션을 삭제한다. `HomeController`의 `greeting` model attribute, `PageService`/`PageType` 의존성을 함께 제거한다. `/pages/GREETING` 상세 페이지 자체(`PageController`/`PageViewController`)는 유지한다.
  4. `home/index.html`의 `#program-shortcut`(하단 CTA) 섹션을 삭제한다.
  5. `home/layout/footer.html`에 화면 텍스트 `www.monicaenglish.com`, `href="https://www.monicaenglish.com"` 링크를 추가한다. 새 창(`target`) 속성은 기존 Footer 링크 정책(모두 현재 창 이동)을 그대로 따라 추가하지 않는다.
  6. `#greeting`/`#program-shortcut` 제거로 더 이상 참조되지 않는 `static/css/home.css`의 `.institute-intro*`, `.cta`/`.cta__*` 규칙을 제거한다.
- DoD:
  - 공개/관리자 화면에서 "모니카 연구소"/"Monika Research Institute" 문자열이 0건이고 "모니카영어교육연구소"로 대체됨을 확인한다(관리자 Header/Footer 포함).
  - `#quick-menu`/`.site-footer__nav`의 첫 번째 링크 라벨이 "연구소 소개", href가 `/pages/INTRODUCTION`이다. 나머지 3개 링크(프로그램/강의 후기/게시판) 순서·href는 P13-T16과 동일하게 무변경이다. `/pages/GREETING`은 별도로 여전히 200을 반환한다.
  - `admin/page/list.html`의 PageType.INTRODUCTION 행 라벨이 "연구소 소개"다.
  - `home/index.html` 응답에 `#greeting`, `#program-shortcut`가 존재하지 않는다. `HomeController`가 더 이상 `PageService`를 참조하지 않는다.
  - `home/layout/footer.html`에 텍스트 `www.monicaenglish.com` + href `https://www.monicaenglish.com` 링크가 존재하고 `target` 속성이 없다.
  - `static/css/home.css`에 `.institute-intro`/`.cta` 관련 규칙이 남아있지 않다(grep 0건).
  - `HomeControllerTest`: 기존 `homeReturns200WithAllRequiredAreasAndFixedQuickMenuLinks`가 `#greeting`/`#program-shortcut` 단언 없이 갱신된 4개 링크 기준으로 통과, `pageService.update(GREETING, ...)` 기반이던 기존 테스트는 greeting 관련 셋업/단언을 제거하고 나머지 도메인 데이터 검증만 남긴 채 통과, `#greeting`/`#program-shortcut` 부재를 확인하는 신규 케이스 통과. P13-T16에서 추가된 `#latest-reviews` 관련 테스트는 무변경 통과(회귀 없음).
  - Playwright: 헤더/푸터 브랜드 텍스트, "연구소 소개" 라벨/링크, footer 도메인 링크(텍스트/href/target 없음), `#greeting`/`#program-shortcut` 부재, `/pages/GREETING` 200 유지를 검증하는 신규 케이스 통과. 기존 P13-T12/P13-T14/P13-T16 describe와 공개 Popup 레이어 describe는 셀렉터만 `#latest-programs .section-title__link`로 교체된 부분(구 `#program-shortcut a.btn` 대체) 외에는 무변경 통과.
  - `./gradlew build` 성공, Node 전체 무변경 통과, Playwright 전체 통과, Docker 8088 수동 확인(375/768/1024/1440에서 브랜드명/연구소 소개 링크/footer 도메인 표시, overflow 없음, 강의 후기 섹션/필터/메뉴 회귀 없음).
  - 조회수 제거, 관리자 첨부파일 미리보기, 게시글 링크/썸네일/자유노출/상세 이미지 크기는 이번 Task 범위에 포함하지 않는다(별도 후속 Task).

---

### P13-T18. 관리자 Board/Program 수정 화면 기존 썸네일/첨부파일 미리보기
- 의존성: P13-T17
- 산출물: `static/js/admin/admin-file-preview.js`(신규), `admin/board/form.html`, `admin/program/form.html`, `static/css/admin/admin.css`, `src/test/js/admin/admin-file-preview.test.js`(신규), `src/test/js/admin/board-admin-view.test.js`, `src/test/js/admin/program-admin-view.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 관리자가 Board/Program 수정 화면에 진입했을 때 이미 등록된 `thumbnail`/`attachment`가 화면에 전혀 보이지 않던 문제를 고친다(원인: `<input type="file">`은 보안상 값을 미리 채울 수 없는데 hidden input에만 기존 URL을 넣고 있었음 — 유지 흐름 자체는 정상 동작했으나 화면에 표시가 안 됨).
  1. Board/Program 각 폼의 썸네일/첨부파일 `<input type="file">` 위에 미리보기 블록(`#thumbnailPreview`/`#attachmentPreview`, 기본 `hidden`)을 추가한다. 썸네일은 실제 `<img>` 미리보기 + "새 창에서 보기"(`target="_blank" rel="noopener noreferrer"`) 링크, 첨부파일은 "현재 등록된 첨부파일 다운로드" 고정 텍스트 링크(서버가 이미 `Content-Disposition: attachment`로 응답하므로 `target="_blank"` 미사용, 공개 상세 페이지 "첨부파일 다운로드" 링크와 동일한 패턴 재사용)로 표시한다. Board/Program 응답에는 원본 파일명이 없고(ERD.md 설계상 FK 대신 URL 문자열만 저장) `GET /api/admin/files/{id}` 단건 조회 API도 없으므로, 새 API를 추가하지 않고 URL 링크만으로 "식별 가능한 정보"를 제공한다.
  2. 신규 공용 모듈 `static/js/admin/admin-file-preview.js`(`AdminFilePreview.renderImagePreview`/`renderLinkPreview`)를 만들어 Board/Program 폼이 함께 사용한다. URL을 `trim()`한 뒤 빈 문자열(공백만 있는 경우 포함)이면 컨테이너를 `hidden` 처리하고 `src`/`href` 속성을 제거하며, `innerHTML`은 사용하지 않는다.
  3. 편집 진입 시 기존 populate 콜백(`Promise.all(...).then(...)`)에서 hidden input 값을 채우는 것과 같은 자리에 미리보기 렌더 호출을 추가한다. 신규 등록 화면(`editingBoardId`/`editingProgramId` 없음)은 이 콜백 자체가 실행되지 않으므로 미리보기가 항상 `hidden` 상태로 유지된다(별도 분기 불필요).
  4. `#thumbnailInput`/`#attachmentInput`의 `change` 핸들러에서 업로드 성공 시(hidden input 값을 갱신하는 자리) 미리보기도 함께 새 URL로 갱신한다.
  5. 기존 hidden input 유지 방식(새 파일 미선택 시 PUT payload에 기존 URL 그대로 전송)은 변경하지 않는다. `board-file-upload.js`/`program-file-upload.js`의 기존 중복은 리팩토링하지 않는다. 파일 삭제 기능, 원본 파일명 조회 API는 추가하지 않는다.
- DoD:
  - Board/Program 수정 진입 시 기존 `thumbnail`이 있으면 `<img>` 미리보기가 렌더링되고, 기존 `attachment`가 있으면 다운로드 링크가 렌더링된다. 값이 없으면(신규 등록 화면 포함) 컨테이너가 `hidden`이다.
  - `admin-file-preview.js`: URL이 `null`/`undefined`/`''`/공백 전용 문자열이면 컨테이너 hidden + `src`/`href` 제거, 유효한 문자열(앞뒤 공백 trim)이면 컨테이너 표시 + `src`/`href` 설정을 단위 테스트로 검증.
  - `board-admin-view.test.js`/`program-admin-view.test.js`: 미리보기 스크립트 로드, 신규 등록 화면 기본 hidden, 편집 진입 시 populate 콜백에서 렌더 호출, 신규 업로드 성공 시 렌더 갱신 호출, 첨부파일 링크에 `target` 없음, 썸네일 "새 창에서 보기" 링크에 `target="_blank" rel="noopener noreferrer"` 존재를 정적 테스트로 검증.
  - Playwright: Board/Program 각각 (a) 기존 썸네일/첨부파일이 있는 게시글/프로그램 수정 진입 시 미리보기 visible과 실제 URL 일치, (b) 신규 등록 화면에서 미리보기 hidden, (c) 새 파일을 선택하지 않고 저장했을 때 실제 PUT payload(또는 저장 후 재조회 응답)의 `thumbnail`/`attachment`가 기존 URL과 동일하게 유지됨(프론트 hidden value 유지 동작 자체를 증명), (d) 새 파일 업로드 시 미리보기가 즉시 갱신됨을 신규 케이스로 확인한다.
  - `./gradlew build` 성공, Node/Java 전체 무변경 통과(Java는 API/DTO/Entity 변경이 없어 회귀만 확인), Playwright 전체 통과, Docker 8088 수동 확인.
  - Banner의 동일한 문제는 확인만 하고 이번 Task에서 고치지 않는다(후속 Task 후보로 기록). 조회수 제거, 기존 파일 삭제 기능, 링크/썸네일/상세 이미지 크기 등은 이번 Task 범위에 포함하지 않는다.
  - DB/Entity/DTO/API/Flyway 변경 없음.

---

### P13-T19. Board 조회수(viewCount) 기능 완전 제거
- 의존성: P13-T18
- 산출물: `board/entity/Board.java`, `board/dto/BoardResponse.java`, `board/repository/BoardRepository.java`, `board/repository/BoardRepositoryImpl.java`, `board/service/BoardService.java`, `db/migration/V2__drop_board_view_count.sql`(신규), `admin/board/list.html`, `home/board/list.html`, `home/board/detail.html`, `static/css/home.css`, `src/test/java/com/monicalab/board/**`(다수, 아래 DoD 참고), `src/test/java/com/monicalab/admin/controller/DashboardControllerTest.java`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `src/test/java/com/monicalab/support/ProductionRuntimeConfigTest.java`, `src/test/js/admin/board-admin-view.test.js`, `frontend-tests/visual-regression.spec.js`, `docs/ERD.md`, `docs/API.md`, `docs/ARCHITECTURE.md`, `docs/FEATURES.md`
- 작업 내용: UI에서 숨기는 수준이 아니라 조회수 기능을 Entity/DB부터 완전히 제거한다.
  1. `Board.viewCount`/`view_count` 컬럼, `BoardResponse.viewCount`, `BoardRepository.increaseViewCount()`, `BoardRepositoryImpl`의 `viewCount` 정렬 분기, `BoardService`의 `viewCount` 초기화·`ALLOWED_SORT_PROPERTIES`의 `viewCount`를 전부 제거한다. `BoardService.getPublicById()`는 확인→증가→재조회 3단계에서 `findByIdAndIsPublicTrue()` 단일 조회로 단순화하고 `@Transactional(readOnly = true)`로 바꾼다(더 이상 DB UPDATE가 발생하지 않는다).
  2. 기존 `V1__baseline_schema.sql`은 수정하지 않고 신규 `V2__drop_board_view_count.sql`(`ALTER TABLE board DROP COLUMN view_count;`)을 추가한다. 기존 `view_count` 데이터는 폐기되며 다른 컬럼에는 영향이 없다.
  3. `sort=viewCount`는 `ALLOWED_SORT_PROPERTIES`에서 제거되는 것만으로 기존 `INVALID_INPUT_VALUE`(400) 정책에 자동으로 편입된다(별도 예외 분기 불필요).
  4. `admin/board/list.html`의 `<th>조회수</th>`/조회수 셀 생성 로직, `home/board/list.html`/`home/board/detail.html`의 "조회 N" 표시, `static/css/home.css`의 `.board-list__views` 규칙을 제거한다.
  5. `BoardViewCountConcurrencyTest.java`는 파일 전체를 삭제한다. 그 외 조회수 관련 테스트는 fixture만 정리하거나(단순 `.viewCount(...)` 호출 제거), 조회수 자체를 검증하던 테스트는 삭제 후 "상세 GET 반복 호출에도 `updatedAt` 불변 + 응답에 `viewCount` 필드 없음"을 확인하는 회귀 테스트로 교체한다. `sort=viewCount` 거부를 확인하는 신규 테스트를 관리자/공개 API 각각에 추가한다. `ProductionRuntimeConfigTest`에 V2 migration 적용 성공 + `information_schema.columns`로 `view_count` 컬럼 부재를 확인하는 테스트를 추가한다.
- DoD:
  - `Board`/`BoardResponse`/`BoardRepository`/`BoardRepositoryImpl`/`BoardService` 어디에도 `viewCount`/`view_count` 문자열이 없다(`V1` 원문 제외).
  - 빈 Testcontainers MariaDB에 V1+V2 적용 후 `ddl-auto=validate`로 정상 기동, `flyway_schema_history`에 버전 2가 success로 기록, `information_schema.columns`에 `board.view_count` 없음.
  - `GET /api/boards/{id}`를 동일 게시글에 2회 이상 호출해도 `board.updatedAt`이 불변(회귀 테스트로 확인). 응답 JSON에 `viewCount` 키가 없다.
  - `sort=viewCount`(관리자 `/api/admin/boards`, 공개 `/api/boards` 모두)는 `INVALID_INPUT_VALUE`(400).
  - 공개 게시판 목록/상세, 관리자 게시판 목록 어디에도 조회수 표시 요소(`.board-list__views`, `<th>조회수</th>`, viewCount 셀 생성 로직)가 없다(targeted 요소 검증, 페이지 전체 텍스트의 "조회" 단어 존재 여부 같은 과도하게 넓은 assertion은 사용하지 않는다).
  - 게시판 목록/상세/필터/페이지네이션은 조회수 제거 전과 동일하게 정상 동작(기존 P13-T14/P13-T16 관련 테스트 무변경 통과로 회귀 확인).
  - `docs/ERD.md`/`docs/API.md`/`docs/ARCHITECTURE.md`/`docs/FEATURES.md` 갱신. `docs/PRD.md`는 관련 언급이 없어 무변경. `docs/TASK.md`의 P6-T4/P13-T14는 원문을 유지한 채 재정정 각주로 보완.
  - `./gradlew build` 성공, Java/Node/Playwright 전체 통과, Docker 8088 수동 확인.
  - Banner/링크(외부·내부)/강의후기·수강신청·갤러리 목록 썸네일/메인 자유노출/상세 이미지 크기/드래그 레이아웃 편집 등은 이번 Task 범위에 포함하지 않는다.

---

### P13-T20. CKEditor 본문 링크 새 탭/내부 이동 처리
- 의존성: P13-T19
- 산출물: `common/util/ContentLinkRenderer.java`(신규), `board/controller/BoardViewController.java`, `program/controller/ProgramViewController.java`, `page/controller/PageViewController.java`, `home/controller/HomeController.java`, `home/board/detail.html`, `home/program/detail.html`, `home/page/detail.html`, `home/index.html`, `src/test/java/com/monicalab/common/util/ContentLinkRendererTest.java`(신규), `src/test/java/com/monicalab/common/util/HtmlSanitizerTest.java`, `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `src/test/java/com/monicalab/program/controller/ProgramViewControllerTest.java`, `src/test/java/com/monicalab/page/controller/PageViewControllerTest.java`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `frontend-tests/visual-regression.spec.js`, `docs/ARCHITECTURE.md`, `docs/FEATURES.md`
- 작업 내용: Board/Program/Page/Popup의 CKEditor 본문에 삽입된 `<a href>` 링크를, 외부 링크는 새 탭에서, 내부 링크는 같은 탭에서 열리도록 공개 화면 렌더링 시점에 처리한다.
  1. `common/util/ContentLinkRenderer.java`를 `HtmlSanitizer`와 동일한 순수 정적 유틸 스타일로 신설한다. `externalLinksOpenInNewTab(String html)`은 jsoup으로 `<a href>`를 순회해 `href`가 `http://`/`https://`/`//`(대소문자 무관)로 시작하면 `target="_blank" rel="noopener noreferrer"`를 부여하고, 그 외(상대 경로/`mailto:`/`#anchor`)는 그대로 둔다. `null` 입력은 `null`을 반환한다.
  2. 각 공개 `*ViewController`(`BoardViewController`, `ProgramViewController`, `PageViewController`, `HomeController`)에서 `ContentLinkRenderer`를 호출해 미리 변환한 HTML을 view 전용 model attribute로 전달한다: Board/Program/Page 상세는 `renderedContent`, Popup(복수 노출)은 `Map<Long, String> popupRenderedContents`. Thymeleaf 3.1 restricted expression evaluation 제약으로 템플릿에서 `T(...)` 정적 메서드를 직접 호출하지 않고, 항상 Controller에서 계산된 값만 `th:utext`로 출력한다.
  3. 4개 템플릿(`home/board/detail.html`, `home/program/detail.html`, `home/page/detail.html`, `home/index.html`의 popup 블록)의 `th:utext` 대상을 `board.content()`/`program.content()`/`page.content()`/`popup.content()`에서 위 model attribute로 교체한다.
  4. `HtmlSanitizer`(저장 시점 XSS 화이트리스트), `BoardService`/`ProgramService`/`PageService`/`PopupService`의 sanitize 호출, 관리자 API Response DTO, DB 저장 `content`, CKEditor 재편집 시 로드되는 원본 데이터는 변경하지 않는다. 기존 저장 데이터도 재저장/마이그레이션 없이 렌더링 시점에 자동 적용된다.
  5. 기존 `home/board/detail.html`의 `#attachment-link`, `home/program/detail.html`의 첨부파일 링크·`#apply-link`는 이미 `target="_blank"`이나 `rel="noopener noreferrer"`가 없다는 것이 확인되었으나, 이번 Task 범위에는 포함하지 않는다(범위 밖, 후속 fix Task 후보).
- DoD:
  - `ContentLinkRendererTest`: http/https/`//`(대소문자 무관 포함) 외부 링크에 `target="_blank" rel="noopener noreferrer"` 부여, 상대 경로/`mailto:`/`#anchor` 내부 링크는 무변경, `null` 입력 시 `null` 반환, 외부/내부 링크가 섞인 경우 독립적으로 분류, `<p>`/`<table>`/`<img src="/api/files/...">` 등 링크 이외 CKEditor HTML이 jsoup 재파싱 후에도 동일하게 보존, 변환 결과에 다시 적용해도 동일 결과(idempotent)임을 검증.
  - `HtmlSanitizerTest`에 상대 경로 `<a href>`가 sanitize 후에도 보존되는 테스트가 추가되어 있다(기존 커버리지 공백 보완, `HtmlSanitizer` 자체는 무변경).
  - `BoardViewControllerTest`/`ProgramViewControllerTest`/`PageViewControllerTest`/`HomeControllerTest`(Popup)에 각 도메인 상세/노출 화면에서 외부 링크가 `target="_blank" rel="noopener noreferrer"`로 렌더링됨을 확인하는 연결 테스트가 최소 1개씩 있다.
  - `frontend-tests/visual-regression.spec.js`에 Board 도메인 한정으로 정확히 2개의 신규 Playwright 테스트가 추가되어 있다: 외부 링크 클릭 시 `context.waitForEvent('page')`로 새 탭이 열림을 확인, 내부 링크 클릭 시 같은 탭에서 해당 게시글 상세로 정상 이동함을 확인. 다른 도메인(Program/Page/Popup)에는 중복 추가하지 않는다.
  - 관리자 API Response(JSON)와 CKEditor 재편집 시 로드되는 `content` 원본에는 `target`/`rel` 속성이 추가되지 않는다(변환이 저장 데이터/API 계약에 반영되지 않음을 확인).
  - `docs/ARCHITECTURE.md`(콘텐츠 링크 처리 정책 신설)/`docs/FEATURES.md`(링크 삽입 항목 1줄 보완) 갱신. `docs/PRD.md`/`docs/API.md`/`docs/ERD.md`는 API 계약·스키마 변경이 없어 무변경.
  - `./gradlew build` 성공, Java/Node/Playwright 전체 통과, Docker 8088 수동 확인.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.
  - 기존 attachment/apply-link의 `rel="noopener noreferrer"` 누락은 이번 Task 범위에 포함하지 않는다(범위 밖, 후속 fix Task 후보로 기록).

---

### P13-T21. 새 탭 링크 보안 속성 일관성 보완
- 의존성: P13-T20
- 산출물: `home/board/detail.html`, `home/program/detail.html`, `admin/file/list.html`, `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `src/test/java/com/monicalab/program/controller/ProgramViewControllerTest.java`, `src/test/js/admin/file-admin-view.test.js`, `docs/ARCHITECTURE.md`
- 작업 내용: P13-T20에서 범위 밖으로 기록한, `target="_blank"`를 사용하면서 `rel="noopener noreferrer"`가 누락된 위치를 전수 조사하여 보완한다.
  1. 프로젝트 전체(`target="_blank"` HTML 속성 + JS `.target = '_blank'` 프로퍼티 할당)를 조사한 결과 6곳 중 4곳에서 `rel` 누락을 확인했다: `home/board/detail.html`의 `#attachment-link`, `home/program/detail.html`의 첨부파일 링크(id 없음)와 `#apply-link`, `admin/file/list.html`의 JS 동적 생성 `nameLink`(관리자 파일 목록, 파일명 클릭 시 새 탭 다운로드). 나머지 2곳(`admin/board/form.html`, `admin/program/form.html`의 `#thumbnailPreviewLink`)은 P13-T18에서 이미 `rel="noopener noreferrer"`가 정상 적용되어 있어 무변경.
  2. 위 4곳 모두 `target="_blank"` 옆에 `rel="noopener noreferrer"`를 추가한다(정적 마크업 3곳, `admin/file/list.html`의 JS `nameLink` 1곳은 `nameLink.rel = 'noopener noreferrer';` 추가). 기존 `href` 값/속성 순서 외 다른 마크업은 변경하지 않는다.
  3. `HtmlSanitizer`, Service 계층, API Response DTO, DB 스키마, `ContentLinkRenderer`(CKEditor 본문 링크 처리, P13-T20)는 이번 Task와 무관하며 변경하지 않는다.
  4. Playwright 신규 테스트는 추가하지 않는다(정적 속성 변경이라 Java/Node 테스트로 계약을 충분히 검증할 수 있다는 판단). 기존 Playwright 전체는 회귀 확인 목적으로만 실행한다.
- DoD:
  - `#attachment-link`(Board 상세), 첨부파일 링크(Program 상세), `#apply-link`(Program 상세), `nameLink`(관리자 파일 목록) 4곳 모두 `target="_blank"`와 `rel="noopener noreferrer"`를 함께 가진다.
  - 위 4곳의 기존 `href` 값은 변경 전과 동일하다(회귀 없음).
  - `BoardViewControllerTest`/`ProgramViewControllerTest`에 각 링크의 `target`/`rel` 속성을 함께 검증하는 테스트가 있다.
  - `file-admin-view.test.js`에 `admin/file/list.html`의 `nameLink.target`/`nameLink.rel` 설정을 검증하는 테스트가 있다.
  - 이미 정상이었던 `#thumbnailPreviewLink`(admin board/program form)와 `ContentLinkRenderer` 대상 CKEditor 본문 링크는 무변경이며 기존 테스트가 그대로 통과한다.
  - `docs/ARCHITECTURE.md`에 "새 탭 링크는 항상 `rel="noopener noreferrer"`를 함께 사용한다"는 공통 보안 규칙이 짧게 추가되어 있다. `docs/FEATURES.md`/`docs/PRD.md`/`docs/API.md`/`docs/ERD.md`는 기능·API·스키마 변경이 없어 무변경.
  - `./gradlew build` 성공, Java/Node 전체 테스트 통과, 기존 Playwright 전체 스위트 회귀 통과(신규 Playwright 테스트는 추가하지 않음).
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

---

### P13-T22. Board/Program 수정 화면 기존 첨부파일 파일명 표시
- 의존성: P13-T21
- 산출물: `file/controller/AdminFileController.java`, `file/service/FileService.java`, `static/js/admin/admin-file-preview.js`, `admin/board/form.html`, `admin/program/form.html`, `docs/API.md`, `src/test/java/com/monicalab/file/FileIntegrationTest.java`, `src/test/js/admin/admin-file-preview.test.js`, `src/test/js/admin/board-admin-view.test.js`, `src/test/js/admin/program-admin-view.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: Board/Program 수정 화면에서 기존 `attachment`가 있어도 고정 문구("현재 등록된 첨부파일 다운로드")만 보여 관리자가 실제 어떤 파일이 연결되어 있는지 식별할 수 없던 문제를 보완한다.
  1. File 도메인에 `GET /api/admin/files/{id}` 단건 조회를 최소 추가한다. `FileService.get(id)`는 `download()`/`delete()`가 이미 쓰는 내부 `getOrThrow(id)`를 재사용해 `FileResponse.from(...)`으로 감싼다. 신규 DTO/Entity/Repository/Flyway 변경 없음. 존재하지 않으면 기존 `FILE_NOT_FOUND`(404) 그대로 재사용.
  2. `admin-file-preview.js`에 `extractFileIdFromUrl(url)`(`/api/files/{id}` 형식에서 id 파싱, 형식이 아니면 `null`)과 `loadAttachmentName(nameWrapEl, nameEl, url, adminFetchFn)`(id 파싱 성공 시 `GET /api/admin/files/{id}` 호출 후 `originalName`을 표시)을 추가한다. 기존 `renderLinkPreview`는 무변경이며, 파일명 조회는 이와 완전히 독립적으로 동작해 조회 실패가 링크의 hidden/href 상태에 영향을 주지 않는다.
  3. Board/Program 폼의 `#attachmentPreview` 마크업에 `#attachmentPreviewNameWrap`(기본 hidden)과 그 안의 `#attachmentPreviewName` span을 추가한다. `loadAttachmentName`이 원본 파일명을 성공적으로 얻었을 때만 `nameWrapEl.hidden = false`로 노출하고, 실패하거나 `originalName`이 없으면 `nameWrapEl`을 hidden 상태로 유지해 "현재 첨부파일: (열기/다운로드)"처럼 이름이 빈 채로 보이는 상태를 방지한다(별도 에러 문구/전역 에러 UI를 추가하지 않고 화면을 조회 이전 상태로 조용히 되돌리는 방식). `storedName`(UUID)은 어디에도 노출하지 않는다.
  4. `loadAttachmentName` 호출은 Board/Program 각 폼에서 (a) 편집 진입 populate 콜백의 `renderLinkPreview` 호출 직후, (b) 첨부파일 업로드 성공 콜백의 `renderLinkPreview` 갱신 직후, 총 2곳에 추가한다. 이 Promise는 populate의 `Promise.all([...])`에 합류시키지 않고 독립적으로 실행해 파일명 조회 실패가 제목/본문/공개여부 등 나머지 필드 populate를 막지 않도록 한다. `board-file-upload.js`/`program-file-upload.js`(및 Banner와 공유하는 업로드 응답 매핑)는 변경하지 않는다.
  5. `<input type="file">`에는 어떤 방식으로도 기존 서버 URL/파일명을 강제 주입하지 않는다. `Board`/`Program`의 Entity/DTO/Repository/DB/Flyway는 변경하지 않는다.
- DoD:
  - `GET /api/admin/files/{id}` 성공 시 `FileResponse`(`originalName` 포함) 200, 존재하지 않으면 `FILE_NOT_FOUND` 404. 기존 `GET /api/admin/files`(목록)/`POST`/`DELETE /{id}`는 무변경 회귀 통과.
  - Board/Program 수정 화면에서 기존 `attachment`가 있으면 실제 `originalName`과 열기/다운로드 링크가 함께 표시된다. 값이 없으면(신규 등록 포함) `#attachmentPreview` 블록 전체가 hidden이다.
  - 파일명 단건 조회가 실패(404/네트워크 오류)하거나 응답에 `originalName`이 없어도 `#attachmentPreviewNameWrap`이 hidden으로 유지되어 화면이 어색하게 보이지 않고, 첨부파일 링크(href)와 폼의 나머지 필드 populate·저장 기능은 정상 동작한다.
  - 새 첨부파일 업로드 성공 시 표시되는 파일명이 새 `originalName`으로 즉시 갱신된다. 새 파일을 선택하지 않고 저장하면 기존 `attachment` 값이 PUT payload에 그대로 유지된다(기존 hidden input 유지 로직 회귀 확인).
  - Board/Program이 `admin-file-preview.js`의 동일한 `extractFileIdFromUrl`/`loadAttachmentName`을 재사용하며 중복 로직이 없다.
  - `Board`/`Program`/`BoardResponse`/`ProgramResponse`/`BoardRequest`/`ProgramRequest`/Entity/Repository/DB/Flyway 무변경.
  - `docs/API.md`에 `GET /api/admin/files/{id}` 계약이 추가되어 있다. `docs/ARCHITECTURE.md`/`docs/FEATURES.md`/`docs/PRD.md`/`docs/ERD.md`는 무변경.
  - `./gradlew build` 성공, Java/Node 전체 테스트 통과, 기존 Playwright 전체 회귀 통과 + Board 대표 신규 케이스 1건(실제 업로드한 파일로 파일명이 렌더링됨을 확인) 통과. Program에는 동일 케이스를 중복 추가하지 않는다.
  - Banner 기존 이미지 미리보기, 공개 상세 이미지 표시 크기, 관리자 게시판 즉시 필터, 첨부파일 삭제 기능, 이미지 압축/리사이즈는 이번 Task 범위에 포함하지 않는다.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

---

### P13-T23. 관리자 CKEditor 이미지 정렬 노출 및 공개 반영
- 의존성: P13-T22
- 산출물: `common/util/HtmlSanitizer.java`, `src/test/java/com/monicalab/common/util/HtmlSanitizerTest.java`, `static/js/admin/ckeditor-config.js`(신규), `src/test/js/admin/ckeditor-config.test.js`(신규), `admin/board/form.html`, `admin/program/form.html`, `admin/page/form.html`, `admin/popup/form.html`, `home/board/detail.html`, `home/program/detail.html`, `home/page/detail.html`, `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 관리자 CKEditor 편집기 확장 가능성 조사(현재 CKEditor 5 41.4.2 classic CDN build를 실제 헤드리스 실행/jsoup 1.18.1 재현으로 직접 검증) 결과에 따라, 새 plugin/build/npm 도입 없이 이미 `ImageStyle` 플러그인에 등록되어 있는 `alignLeft`/`alignRight`/`alignCenter`를 관리자 화면에 노출하고, 저장 시 사라지던 이미지 정렬 정보가 실제로 왕복 보존되도록 한다.
  1. CDN URL(`https://cdn.ckeditor.com/ckeditor5/41.4.2/classic/ckeditor.js`)과 버전은 그대로 유지한다. 새 CKEditor plugin, npm 패키지, 번들러는 도입하지 않는다.
  2. 4개 폼이 공유하는 `static/js/admin/ckeditor-config.js`(신규)를 만들어 `image.toolbar`에 기존 `imageStyle:inline`/`imageStyle:block`/`imageStyle:side`를 유지한 채 `imageStyle:alignLeft`/`imageStyle:alignCenter`/`imageStyle:alignRight`를 추가한다(`image.styles` 재선언 불필요 — 헤드리스 실행으로 이미 기본 등록되어 있음을 확인). 4개 폼은 `ClassicEditor.create(el, AdminCkeditorConfig.EDITOR_CONFIG)`로 이 config를 전달하고, 업로드 어댑터(`ckeditor-upload-adapter.js`, `installUploadAdapterPlugin`) 계약은 변경하지 않는다.
  3. `HtmlSanitizer`에 `figure` 태그와 `figure[class]` 속성을 추가하고, class 값은 whitespace로 분리한 토큰 단위로 화이트리스트(`image`, `image-style-side`, `image-style-align-left`, `image-style-align-right`, `image-style-align-center` — CKEditor 41.4.2 `getData()` 실제 출력을 헤드리스로 직접 확인해 결정한 최소 목록)와 대조해 안전한 토큰만 남긴다. `img`에는 class를 허용하지 않는다(inline 스타일은 `<figure>` 없이 bare `<img>`로 표현되어 class 자체가 필요 없음을 실측 확인, `image-inline` 토큰은 `getData()`에 존재하지 않음). `style`/`width`/`height`/`figcaption`/font 관련 속성은 이번 범위에 포함하지 않는다. 기존 `<script>`/`iframe`/`on*`/`javascript:` 차단 정책은 변경하지 않는다.
  4. Board/Program/Page 상세의 CKEditor 본문 div에 공용 class `ckeditor-content`를 추가하고, `static/css/home.css`에 `.ckeditor-content`/`.popup-modal__body` 공용 규칙(`display: flow-root`로 float containment, `.image`/`.image img`/`.image-style-side`/`.image-style-align-left`/`.image-style-align-right`/`.image-style-align-center`)을 추가한다. `.image img`의 `max-width:100%; height:auto`는 `.ckeditor-content`에만 한정한다(`.popup-modal__body`는 이미 fit-content 순환 참조 방지용 고정 px 캡을 갖고 있어, 공용으로 걸면 specificity가 더 높은 새 규칙이 그 캡을 덮어써 P13-T11에서 고친 문제가 재발함을 CSS 검토로 확인했다). 인라인 `style` 기반 크기 정보는 이번 범위에 없어 `width`-vs-`max-width` specificity 충돌이나 `!important`는 필요하지 않다.
- DoD:
  - `alignLeft`/`alignRight`/`alignCenter` 버튼이 Board/Program/Page/Popup 4개 관리자 화면의 이미지 toolbar에 노출되며, 기존 `inline`/`block`/`side` 버튼은 제거되지 않는다.
  - `HtmlSanitizerTest`: 5개 허용 class 토큰 각각 보존, 허용되지 않은 토큰은 같은 속성 안에서도 개별 제거(허용 토큰은 유지), 모든 토큰이 허용되지 않으면 `class` 속성 자체 제거, `figure`에 대한 이벤트 핸들러/스크립트 삽입 시도는 여전히 차단, `<figure>` 없는 bare inline `<img>`는 sanitize 전후 동일(회귀 없음), 기존 XSS 차단 테스트 전체 무변경 통과.
  - `ckeditor-config.js`가 4개 폼에서 동일하게 재사용되고 config 중복 정의가 없다. 업로드 어댑터 계약 무변경.
  - Board 대표 Playwright 1건으로 다음 전체 흐름을 실제 검증한다: 관리자 편집기에서 정렬 버튼 노출 → 실제 정렬 적용 → 저장 → API 재조회로 얻은 id로 수정 화면 재진입 시 정렬이 CKEditor 안에서 그대로 복원됨 → 공개 상세 화면에서 동일 정렬(class)이 반영됨 → float 다음에 이어지는 하단 UI와 겹치지 않음 → 375/768/1024/1440에서 가로 overflow 없음. Program/Page/Popup에는 동일 케이스를 중복 추가하지 않는다(`HtmlSanitizer`/CSS가 공유되는 단일 지점이므로).
  - `ImageResize`/`Font*`/문단 `Alignment` plugin은 추가되지 않는다(코드 리뷰로 확인). CDN URL/버전 무변경.
  - 이미지 caption(`figcaption` 텍스트 leak)과 `img[alt]` 미보존은 발견사항으로만 기록하고 이번 DoD에 포함하지 않는다.
  - `./gradlew build` 성공, Java/Node 전체 테스트 통과, Playwright 전체 회귀 통과.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.
  - ImageResize, Font Size/Family/Color/Background Color, 문단 Alignment 확장은 P13-T24A 사전 조사(2026-08-30) 결과 보류하기로 결정했다. 최신 CKEditor 설치 방식으로 전환할 경우 라이선스 검토, Cloud 계정/비용 가능성 또는 자체호스팅 관리 등 현재 프로젝트에 불필요한 운영 복잡도가 추가되며, 해당 고급 편집 기능은 핵심 요구사항이 아니므로 추가 마이그레이션을 진행하지 않는다. 현재 CKEditor 5 41.4.2 classic CDN을 유지하고, 이 Task(P13-T23)에서 구현한 이미지 정렬/반응형 기능을 현재 편집 기능 범위의 최종 상태로 본다. 향후 요구사항이나 라이선스·운영 조건이 달라질 경우 재검토한다.

---

### P13-T24. Banner 관리자 수정 화면 기존 이미지 미리보기
- 의존성: P13-T23
- 산출물: `admin/banner/form.html`, `src/test/js/admin/banner-admin-view.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: P13-T18에서 Board/Program 수정 화면에 도입한 `AdminFilePreview.renderImagePreview` 패턴(기존 파일을 `<input type="file">`에 강제 주입하지 않고, 별도 `<img>`/링크 미리보기로 표시)을 Banner에도 동일하게 적용한다. P13-T18 DoD에 "Banner의 동일한 문제는 확인만 하고 고치지 않는다(후속 Task 후보로 기록)"로 남아 있던 항목을 해소한다.
  1. `admin/banner/form.html`의 이미지 입력 위에 `#imagePreview`(기본 `hidden`) 블록을 추가한다: `<img id="imagePreviewImage">` + `<a id="imagePreviewLink" target="_blank" rel="noopener noreferrer">새 창에서 보기</a>` — Board/Program의 `#thumbnailPreview` 블록과 동일한 DOM 구조.
  2. `static/js/admin/admin-file-preview.js`(기존 파일, 수정하지 않음)를 로드해 편집 진입 populate 콜백(`banner.image` 채우는 자리)에서 `AdminFilePreview.renderImagePreview(...)`를 호출한다.
  3. `#imageInput`의 `change` 성공 콜백(업로드 후 hidden input 갱신 자리)에서 동일하게 `renderImagePreview(...)`를 재호출해 미리보기를 즉시 갱신한다.
  4. 신규 등록 화면(`editingBannerId` 없음)은 populate 콜백 자체가 실행되지 않으므로 미리보기가 항상 `hidden` 상태로 유지된다(별도 분기 불필요, Board/Program과 동일한 원리).
  5. `Banner` Entity/DTO/Repository/Service/Controller, DB/Flyway, `admin-file-preview.js` 자체, `banner-file-upload.js`(업로드 계약)는 변경하지 않는다.
- DoD:
  - Banner 수정 화면 진입 시 기존 `image`가 있으면 `<img>` 미리보기와 "새 창에서 보기"(`target="_blank" rel="noopener noreferrer"`) 링크가 렌더링된다. 값이 없으면(신규 등록 포함) `#imagePreview`가 hidden이다.
  - `<input type="file">`에는 어떤 방식으로도 기존 서버 URL을 강제 주입하지 않는다(코드 리뷰로 확인).
  - 새 이미지를 업로드하면 미리보기가 즉시 새 URL로 갱신된다.
  - `banner-admin-view.test.js`: 미리보기 스크립트 로드, 신규 등록 화면 기본 hidden, 편집 진입 시 populate 콜백에서 렌더 호출, 업로드 성공 시 렌더 갱신 호출, "새 창에서 보기" 링크의 `target`/`rel` 속성을 정적 테스트로 검증. 기존 18개 테스트 무변경 통과(신규 5개 추가로 총 23개).
  - Playwright: 기존 이미지가 있는 배너 수정 진입 시 미리보기 visible + 실제 URL 일치, 신규 등록 화면에서 hidden, 새 이미지 업로드 시 미리보기 즉시 갱신을 신규 케이스로 확인한다.
  - `Banner` Entity/DTO/API/DB/Flyway 무변경. `admin-file-preview.js` 무변경(diff 0).
  - `./gradlew build` 성공, Java/Node 전체 무변경 통과(백엔드 변경 없음), Playwright 전체 회귀 통과.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.
  - 기존 첨부파일/이미지 삭제 기능, 게시판 목록(갤러리/강의후기) 썸네일 표시, CKEditor 관련 작업은 이번 Task 범위에 포함하지 않는다(각각 별도 후속 Task 후보로 남긴다).

---

### P13-T25. 관리자 CKEditor 이미지 정렬 dropdown 정리
- 의존성: P13-T24(Banner 관리자 수정 화면 기존 이미지 미리보기 — 계획 당시에는 P13-T24 → P13-T25 순으로 진행할 예정이었으나, P13-T24 작업이 지연되어 P13-T25가 먼저 병합되었다. P13-T24는 이후 별도 작업으로 진행한다.)
- 산출물: `static/js/admin/ckeditor-config.js`, `src/test/js/admin/ckeditor-config.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 사전 조사(정렬+크기 결합 style은 저장→재편집 round-trip에서 데이터 유실이 실측으로 재현되어 보류, 이미지 크기 preset/ImageResize도 기존대로 보류) 결과에 따라, **기존 6개 ImageStyle(inline/block/side/alignLeft/alignCenter/alignRight)의 semantics는 전혀 바꾸지 않고** balloon toolbar에 평면 나열된 6개 버튼을 "이미지 정렬" dropdown 1개로만 정리한다.
  1. `ckeditor-config.js`의 `image.toolbar`를 `{ name: 'imageStyle:dropdown', title: '이미지 정렬', items: [...6개], defaultItem: 'imageStyle:block' }` 형태로 재구성한다. CDN URL/버전/plugin/build/npm은 전혀 변경하지 않는다.
  2. `image.styles.options`에 6개 style을 `{ name, title }`만으로 재선언해 한글 라벨을 부여한다(`className`/`modelElements`/`icon`은 명시하지 않아 CKEditor 기본값을 그대로 재사용 — 헤드리스 실행으로 다운캐스트 결과가 P13-T23과 byte 단위로 동일함을 확인). 최종 한글 라벨: `inline`="글 안에 배치", `block`="기본", `side`="글 옆에 배치", `alignLeft`="왼쪽 정렬", `alignCenter`="가운데 정렬", `alignRight`="오른쪽 정렬". 원래 제시된 "기본(가운데)"(block)는 "기본"으로 조정했다 — `block`과 `alignCenter`가 현재 공개 CSS(`home.css`의 `.ckeditor-content .image`/`.image-style-align-center`)에서 완전히 동일하게 렌더링됨을 확인했고(`alignCenter`가 추가하는 `margin-left/right:auto`가 `block`의 기존 `margin:var(--space-2) auto`와 중복), 두 라벨 모두 "가운데"를 쓰면 서로 다른 옵션처럼 오해할 수 있어 `block`의 라벨에서만 뺐다.
  3. `HtmlSanitizer.java`/`HtmlSanitizerTest.java`, `static/css/home.css`, Board/Program/Page/Popup 4개 관리자 `form.html`, 3개 공개 `detail.html`은 전혀 변경하지 않는다(4개 폼은 이미 공용 `ckeditor-config.js`를 로드하므로 이 파일 하나만 고치면 자동 반영된다). 이미지 크기 기능, `ImageResize`는 추가하지 않는다.
- DoD:
  - 이미지 balloon toolbar에 정렬 관련 버튼이 dropdown 1개로만 나타나고(평면 버튼 6개가 최상위에 없음), 화살표를 열면 패널 안에 기존 6개 style이 위 한글 라벨로 전부 존재한다(`ckeditor-config.test.js` 정적 검증 + Playwright 실제 DOM 확인).
  - `image.styles.options`의 각 항목이 `name`/`title`만 갖고 `className`/`modelElements`를 명시하지 않는다(`ckeditor-config.test.js`).
  - P13-T23이 실제 만들었던 6개 style 각각의 저장 HTML(`<figure class="image">`, `image-style-side`, `image-style-align-left/center/right`, bare `<img>`)이 dropdown 도입 전후로 byte 단위 동일하다(헤드리스 실행으로 검증, 코드 변경 없음).
  - `HtmlSanitizer`가 P13-T23 시절 저장한 정렬 HTML을 그대로 처리하며 무변경이다(사전 조사에서 확인, 이 Task에서 sanitizer 코드 자체를 건드리지 않음).
  - Board 대표 Playwright: 이미지 선택 → dropdown 열기 → 패널에 6개 항목 한글 라벨 노출 확인 → "왼쪽 정렬" 선택 → 저장 → API로 얻은 id로 수정 화면 재진입 시 dropdown UI에서도 정렬이 그대로 복원됨 → 공개 화면 정렬 반영 → float containment/375·768·1024·1440 overflow 없음까지 하나의 흐름으로 확인한다(P13-T23 테스트를 dropdown 인터랙션에 맞게 갱신). 별도로 P13-T23 시절(dropdown 도입 이전)에 저장됐을 법한 HTML을 API로 직접 심어 새 dropdown UI에서도 정확히 같은 style로 복원되고 공개 화면도 무변경임을 확인하는 케이스를 추가한다. Program/Page/Popup에는 중복 추가하지 않는다.
  - 기존 반응형(375/768/1024/1440) Playwright 스위트 전체 무변경 통과.
  - 이미지 크기 preset, `ImageResize`, Font 계열, 문단 `Alignment` plugin은 이 Task에 포함되지 않는다(코드 리뷰로 확인, 계속 보류 상태 유지).
  - `./gradlew build` 성공, Java/Node 전체 테스트 통과, Playwright 전체 회귀 통과.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

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
| 공개 UI 디자인 적용 완료 | 공개 UI/UX 개선 | Phase13(P13-T0~T7) 통과, 기존 뷰 통합 테스트 + Playwright 반응형 유지 |

---

## 실행 순서 요약 (의존성 그래프 기준 위상정렬)

```
                           ┌→ Phase4 ─┐
                           ├→ Phase5 ─┤
Phase1 → Phase2 → Phase3 ─┼→ Phase6 ─┼→ Phase8 → Phase9 → Phase10 → Phase11 → Phase12 → Phase13
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