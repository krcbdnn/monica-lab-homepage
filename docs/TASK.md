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
- 정정(P14-T0): 위 "Sub Page 공통 Hero/Breadcrumb 적용"은 현재 코드에 구현되어 있지 않다(공개 목록/상세 template은 `<h2>` 제목으로 시작). Phase 14에서 자동 복원하거나 Task로 추가하지 않는다(Phase 14 공통 계약 A-17 참고).

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

### P13-T26. 관리자 Board/Program 기존 썸네일/첨부파일 제거(detach)
- 의존성: P13-T22(파일명 표시), P13-T25
- 산출물: `admin/board/form.html`, `admin/program/form.html`, `src/test/js/admin/board-admin-view.test.js`, `src/test/js/admin/program-admin-view.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: P13-T18/P13-T22에서 Board/Program 수정 화면에 도입한 기존 thumbnail/attachment 미리보기(열기·다운로드·파일명 표시·새 파일 교체)에 이어, 관리자가 기존 값을 명시적으로 "제거"할 수 있게 한다. 여기서 "제거"는 Board/Program이 가진 **URL 참조만 비우는 detach 동작**이며, File DB 레코드나 실제 디스크 파일은 전혀 삭제하지 않는다(`DELETE /api/admin/files/{id}`는 File 레코드+실제 파일을 영구 삭제하는 별개 기능이고, Board/Program·File 사이에 FK가 없어 하나의 File URL이 다른 콘텐츠에서 재사용 중일 수 있으므로 이번 Task 흐름에서는 절대 호출하지 않는다).
  1. `thumbnailPreview`/`attachmentPreview` 컨테이너 **내부**에 "제거" `<button type="button">`을 추가한다(각 컨테이너의 기존 `hidden` 토글 로직에 자연히 종속되어, 값이 없으면 버튼도 자동으로 숨겨진다 — 별도 가시성 상태 관리 불필요). 문구는 "제거"를 사용해 목록 화면의 영구 "삭제"(`btn btn-outline-danger`, `confirm` 동반)와 구분하고, 스타일은 더 약한 `btn btn-outline-secondary btn-sm`을 사용한다. 별도 `confirm`은 추가하지 않는다(저장 전까지는 로컬 상태일 뿐인 다른 필드 수정과 동일한 성격).
  2. 제거 버튼 클릭 핸들러는 해당 hidden input(`#thumbnail`/`#attachment`)을 빈 문자열로 설정하고, 기존 `AdminFilePreview.renderImagePreview(...)`/`renderLinkPreview(...)`/`loadAttachmentName(...)`를 빈 문자열 인자로 재호출한다 — 세 함수 모두 falsy 값에 대해 컨테이너를 hidden 처리하는 분기를 이미 갖고 있고(`loadAttachmentName('')`은 fetch 자체를 호출하지 않고 즉시 hidden 유지), 이 경로는 `admin-file-preview.test.js`가 이미 커버하고 있어 **`static/js/admin/admin-file-preview.js`는 변경하지 않는다**(diff 0).
  3. 클릭 자체는 서버 요청을 발생시키지 않는다 — 기존 "저장" 버튼을 눌러야 PUT payload에 반영되는 다른 모든 필드와 동일한 모델이다. 저장하지 않고 페이지를 이탈하면 서버 값은 그대로 유지된다.
  4. `Board`/`Program` Entity/DTO/Repository/Service/Controller/API/DB/Flyway는 변경하지 않는다 — `thumbnail`/`attachment`는 이미 nullable이고 `update()`가 항상 요청값으로 완전 치환하므로, PUT payload가 `null`이면 DB 값도 그대로 `null`이 된다(기존 계약 재확인, 신규 계약 아님). Banner는 이번 Task 범위에 포함하지 않는다.
- DoD:
  - Board/Program 수정 화면에서 기존 thumbnail/attachment가 있을 때만 "제거" 버튼이 보이고(값이 없으면 preview 컨테이너와 함께 자동으로 숨겨짐), 클릭 시 즉시 hidden input이 비워지고 해당 preview(버튼 포함)만 사라진다. 다른 쪽 필드(thumbnail↔attachment)에는 영향이 없다.
  - 제거 후 저장하면 PUT payload의 해당 필드가 `null`이고, 저장 후 수정 화면 재진입 시 해당 preview가 hidden으로 유지된다(Board는 상호 비영향 확인 포함, Program은 대표 round-trip 1건).
  - **기존 thumbnail/attachment가 있는 수정 화면에서 제거 버튼을 누르지 않고 그대로 저장하면 기존 값이 PUT payload에 그대로 유지된다**(P13-T18이 이미 검증하던 시나리오와 동일 — 제거 기능 추가로 인한 회귀가 없음을 기존 P13-T18 Playwright 테스트 무변경 재실행으로 확인).
  - 제거 후 새 파일 업로드 시 정상적으로 새 URL이 설정되고, 업로드 후 다시 제거하면 hidden 상태로 돌아간다(하나의 흐름으로 확인).
  - `static/js/admin/admin-file-preview.js`는 무변경(diff 0). Board/Program 두 폼이 완전히 동일한 패턴(동일 DOM 구조·동일 핸들러 로직)을 사용한다.
  - `DELETE /api/admin/files/{id}`가 이 흐름에서 호출되지 않는다 — `admin/board/form.html`/`admin/program/form.html`이 `DELETE`/`/api/admin/files` 문자열을 전혀 포함하지 않음을 정적 테스트로 확인한다(별도 network spy 불필요).
  - `Board`/`Program`/`BoardResponse`/`ProgramResponse`/`BoardRequest`/`ProgramRequest`/Entity/Repository/DB/Flyway 무변경.
  - `./gradlew build` 성공, Java 전체 테스트 무변경 통과(백엔드 변경 없음, 신규 Java 테스트 추가하지 않음), Node 전체 테스트 통과, Playwright 전체 회귀 통과(기존 P13-T18/P13-T22 테스트 포함).
  - 이번 Task를 위한 375/768/1024/1440 반응형 신규 테스트는 추가하지 않는다(P13-T18/T22도 관리자 폼에는 해당 패턴을 요구하지 않았다).
  - Banner의 이미지 제거, 물리 파일 삭제, 이미지 압축/리사이즈는 이번 Task 범위에 포함하지 않는다.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

---

### P13-T27. 공개 게시판 목록 갤러리/강의후기 썸네일 그리드
- 의존성: P13-T16, P13-T22
- 산출물: `home/board/list.html`, `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: `/boards?boardType=GALLERY`/`REVIEW`처럼 이미지 중심 게시판 타입을 텍스트 전용 목록으로 보여주던 문제를, 메인 페이지(`home/index.html`)의 `#latest-gallery`/`#latest-reviews`가 이미 쓰는 `.gallery-grid`/`.gallery-card*` 마크업·CSS를 그대로 재사용해 해소한다.
  1. `home/board/list.html`의 목록 컨테이너를 최상위에서 1회만 분기한다: `boardType`이 `GALLERY`/`REVIEW`면 `#board-grid`(`.gallery-grid`, 메인 페이지와 동일한 `.gallery-card`/`.gallery-card__link`/`.gallery-card__thumb`/`.gallery-card__thumb-placeholder`/`.gallery-card__title`)를 렌더링하고, 그 외(NOTICE/ARCHIVE/전체)는 기존 `#board-list` 텍스트 목록을 그대로 렌더링한다. item 단위 분기나 신규 fragment는 만들지 않는다(서버가 이미 `boardType`으로 필터링해 페이지 내 item 타입이 항상 동일하므로 컨테이너 1회 분기로 충분).
  2. thumbnail 표시는 메인 페이지의 단순 `!= null` 체크보다 안전한 `home/program/list.html`의 기존 패턴 `#strings.isEmpty(#strings.trim(board.thumbnail()))`을 채택해 null/빈 문자열/공백 문자열 모두 placeholder로 대체한다(깨진 `<img src="">` 방지).
  3. `Controller`/`Service`/`Entity`/`DTO`/`API`/`DB`/`Flyway`/`home.css`/`home/fragments/pagination.html`/필터 nav/검색 폼/`home/board/detail.html`/admin 화면/메인 페이지(`home/index.html`)는 전혀 변경하지 않는다. 상세 카드 링크는 기존 `@{/boards/{id}(id=${board.id()})}` 방식을 그대로 사용한다.
- DoD:
  - `boardType=GALLERY`/`REVIEW`에서 `#board-grid`(`.gallery-grid`/`.gallery-card` 구조)만 렌더링되고 `#board-list`는 렌더링되지 않는다. NOTICE/ARCHIVE/전체에서는 반대로 `#board-list`만 렌더링되고 `#board-grid`는 렌더링되지 않는다(`BoardViewControllerTest` MockMvc+Jsoup 검증, `ProgramViewControllerTest`의 기존 검증 패턴 재사용).
  - thumbnail이 null/빈 문자열/공백 문자열이면 `#board-grid`에서 `.gallery-card__thumb-placeholder`가 표시되고 `<img>`는 렌더링되지 않는다. 정상 URL이면 `<img src>`/`loading="lazy"`가 정확히 반영된다(`BoardViewControllerTest`).
  - Playwright: GALLERY/REVIEW 필터에서 실제 브라우저 렌더링으로 썸네일 카드 노출과 정확한 `src` 확인, 카드 클릭 시 상세 페이지로 정상 이동, 썸네일 없는 카드도 무너지지 않고 placeholder + 전체 클릭 가능, NOTICE/전체는 `#board-list` 유지, 공백 없는 긴 제목이 있는 GALLERY 카드에서 가로 스크롤 없음, 375px/1440px 대표 2개 viewport에서 GALLERY 그리드 overflow 없음을 확인한다(4개 viewport 전부 중복 실행하지 않음).
  - pagination/필터/`keyword` 유지 계약은 Controller와 `pagination.html` fragment를 변경하지 않았으므로 기존 P13-T14 Playwright/Java 테스트 무변경 재실행으로 회귀를 확인한다(신규 케이스 추가하지 않음).
  - "상세 → 목록 복귀 시 boardType/keyword/page 유지"는 현재 `home/board/detail.html`의 "목록으로" 링크가 파라미터 없이 `/boards`로 고정되어 있어 애초에 구현되어 있지 않은 것으로 재확인됐다(관련 테스트 없음). 이 Task는 `detail.html`을 변경하지 않으므로 기존 동작 그대로이며, 이 문제 자체는 이번 범위에 포함하지 않고 별도 후속 Task 후보로만 기록한다.
  - `home.css`는 변경하지 않는다(diff 0) — 기존 `.gallery-grid`/`.gallery-card*` 스타일만으로 반응형(auto-fill grid, aspect-ratio, line-clamp)이 충분함을 확인했다.
  - `Board`/`BoardResponse`/`BoardRequest`/Entity/Repository/DB/Flyway/API 무변경.
  - `./gradlew build` 성공, Java 전체 테스트 통과(신규 케이스 포함), Node 전체 테스트 통과(무변경), Playwright 전체 회귀 통과.
  - 메인 페이지 카드 재설계, NOTICE/ARCHIVE 디자인 변경, 전체 목록 혼합형 카드 도입, 상세 CKEditor 이미지 크기, pagination 구조 재설계는 이번 Task 범위에 포함하지 않는다.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

---

### P13-T28. 게시판 상세 → 목록 복귀 상태(boardType/keyword/page) 보존
- 의존성: P13-T27
- 산출물: `board/controller/BoardViewController.java`, `home/board/list.html`, `home/board/detail.html`, `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 상세 페이지 "목록으로" 링크가 항상 파라미터 없는 `/boards`로 고정되어 있어, 필터/검색/페이지 상태를 유지한 채 목록으로 돌아갈 수 없던 문제를 해소한다. 보존 대상은 정확히 `boardType`/`keyword`/`page`(canonical, `PaginationSupport`가 계산해 준 `boards.page` 0-based 실제 결과 페이지) 3개이며, `pageJump`(1회성 입력값)는 절대 보존하지 않는다.
  1. `home/board/list.html`의 `#board-grid`/`#board-list` 상세 링크 모두에 `boardType`/`keyword`/`page=${boards.page}` 쿼리 파라미터를 추가한다(item 단위 분기 없음, 두 목록 공통).
  2. `BoardViewController.detail()`에 `boardType`/`keyword`/`page`를 **원시 `String`**(`@RequestParam(required = false)`)으로 추가 수신해 모델에 그대로 전달한다. `boardService.getPublicById(id)` 상세 조회 로직에는 전혀 관여하지 않으며, 값 검증/정제(타입 변환, enum 매핑 등)를 하지 않는다 — 최종 해석은 기존 `/boards` 엔드포인트의 기존 계약이 그대로 담당한다.
  3. `home/board/detail.html`의 "목록으로" 링크를, 셋 다 없을 때만 순수 `/boards`로, 그 외에는 `@{/boards(boardType=..., keyword=..., page=...)}`로 렌더링하도록 명시적으로 분기한다. Thymeleaf `@{}`는 null 값을 파라미터 생략이 아니라 빈 값(`key=`)으로 렌더링한다는 것을 구현 중 실측으로 재확인했고(사전 조사 당시의 "자동 생략" 가정은 부정확했음), 이 때문에 "파라미터가 아예 없는 순수 `/boards`" 보장에는 명시적 분기가 필요했다.
  4. `Board` API/Entity/Service/Repository/DB/Flyway, `PaginationSupport`, `pagination.html` fragment, 필터 nav, 검색 폼, 상세 페이지 디자인/CSS, 관리자 화면, Program 도메인, 메인 화면, P13-T27 카드 디자인은 전혀 변경하지 않는다.
- DoD:
  - `#board-grid`/`#board-list` 상세 링크 모두 `boardType`/`keyword`/`page`를 쿼리 파라미터로 포함한다(P13-T27이 만든 href가 `/boards/{id}` 정확히 일치에서 `startsWith`로 완화된 것은 이 변경의 직접적·의도된 결과).
  - 상세 페이지에 전달된 `boardType`/`keyword`/`page`가 "목록으로" 링크에 그대로 반영된다. 파라미터가 전혀 없이 `/boards/{id}`에 직접 접근하면 "목록으로"는 정확히 `/boards`(쿼리 없음)다.
  - `keyword`가 없을 때 href에 리터럴 `"null"` 문자열이 섞이지 않고, 빈 값으로 안전하게 처리된다(깨진 URL 없음).
  - Java(`BoardViewControllerTest`, MockMvc+Jsoup)로 검증하며, href의 쿼리 파라미터 순서에 의존하지 않도록 `UriComponentsBuilder`로 파싱해 값 단위로 비교한다.
  - Playwright 대표 2건: GALLERY 목록→카드 클릭→상세→목록으로 클릭 시 GALLERY 필터 유지, 검색 목록→상세→목록으로 클릭 시 keyword 유지. page>0 복귀는 대량 fixture 없이 실제 pagination을 거쳐 검증하기 번거로워 Java 테스트(명시적 `page=2` 파라미터)로만 확인하고 Playwright 범위는 확장하지 않는다.
  - 기존 P13-T14(필터/pagination/keyword)·P13-T27(그리드/텍스트 목록 분기) 테스트 전체가 무변경(단, href 정확 일치 검증 3건은 이 Task의 의도된 변경에 맞춰 `startsWith`로 조정) 통과한다.
  - `Board` API/Entity/Service/Repository/DB/Flyway, `PaginationSupport`, `pagination.html`, 필터 nav, 검색 폼, 상세 디자인/CSS, 관리자 화면, Program 도메인, 메인 화면 무변경.
  - `./gradlew build` 성공, Java 전체 테스트 통과(신규 케이스 포함), Node 전체 테스트 통과(무변경), Playwright 전체 회귀 통과.
  - 사용자가 URL을 수동으로 조작한 잘못된 `boardType`/`page` 값에 대한 신규 validation/sanitizing은 추가하지 않는다(기존 `/boards` 계약 그대로 유지).
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

---

### P13-T29. 공개 게시글 상세 CKEditor inline 이미지 모바일 overflow 방지
- 의존성: P13-T23
- 산출물: `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 사전 조사 결과, CKEditor의 6개 ImageStyle 중 `inline`("글 안에 배치")만 `HtmlSanitizer`가 `<figure>` 래핑 없는 순수 `<img src="...">`로 저장한다는 것을 실측으로 확인했다(나머지 5개 block/side/alignLeft/alignCenter/alignRight는 모두 `<figure class="image ...">`로 감싸짐). 기존 `.ckeditor-content .image img { max-width:100%; ... }` 규칙은 `.image` figure 조상을 요구하므로 `inline` 이미지에는 적용되지 않아, 원본 해상도가 큰 이미지를 `inline`으로 삽입하면 모바일에서 overflow가 발생할 수 있었다.
  1. `home.css`의 `.ckeditor-content` 공용 영역(Board/Program/Page 상세가 공유)에 `.ckeditor-content img { max-width: 100%; height: auto; }`를 추가한다. 선택자 특이도가 기존 `.ckeditor-content .image img`보다 낮아 5개 figure-style에는 값이 동일해 시각적 차이가 없고(더 구체적인 선택자가 계속 우선), `inline` 하나에만 새로 적용된다.
  2. 기존 `.ckeditor-content .image img`, `.image-style-side`/`alignLeft`/`alignRight`/`alignCenter` 정렬·float 규칙은 전혀 수정하지 않는다.
  3. Popup(`.popup-modal__body`)은 범위에서 제외한다 — `.popup-modal__body img`가 이미 `.image` 조상 없이 전체 `img`에 적용되고 있어(fit-content 폭 계산용 고정 px 캡) 이 구멍이 애초에 없다.
  4. `HtmlSanitizer`, Java Controller/Service/DTO, 공개 detail template(Board/Program/Page/Popup), 관리자 CKEditor 설정(`ckeditor-config.js`)은 변경하지 않는다. 데스크톱 이미지 최대 px 폭 제한은 별도 디자인 결정이 필요한 후속 Task 후보로 남기고 이번 범위에 포함하지 않는다.
- DoD:
  - `.ckeditor-content img { max-width:100%; height:auto; }` 규칙이 추가되어 `inline` 이미지에도 폭 제약이 적용된다.
  - Playwright: Board 대표 1건 — 큰 intrinsic 크기(2400×1200)의 PNG를 canvas로 즉석 생성(별도 대용량 binary fixture 없음)해 CKEditor에서 `inline`("글 안에 배치")으로 저장한 뒤, 공개 상세에서 375/768/1440px 각각 페이지 전체 horizontal overflow가 없고, 저장된 이미지가 `<figure>` 래핑 없는 순수 `inline <img>`임을 재확인하며, 해당 `<img>`의 실제 렌더 폭이 `.ckeditor-content` 콘텐츠 폭을 넘지 않음을 직접 검증한다. Program/Page에는 `.ckeditor-content` CSS를 공유하므로 중복 케이스를 추가하지 않는다.
  - 기존 P13-T23의 image style/float containment/overflow(375/768/1024/1440) Playwright 테스트가 무변경 통과한다(figure-style 5종 회귀 없음).
  - `HtmlSanitizer`, Java Controller/Service/DTO, 공개 detail template, 관리자 CKEditor 설정 무변경.
  - `./gradlew build` 성공, Java 전체 테스트 통과(무변경 회귀, 백엔드 변경 없음), Node 전체 테스트 통과(무변경), Playwright 전체 회귀 통과.
  - Popup 이미지 정책, 데스크톱 최대 px 폭 제한, CKEditor custom build/Font/ImageResize, sanitizer의 width/height/style/alt 허용 확장, 관리자 editor UI, PowerPoint식 자유배치는 이번 Task 범위에 포함하지 않는다.
  - `docker-compose.local-test.yml`은 변경 없이 untracked 상태를 유지한다.

---

### P13-T30A. Menu 도메인 + 관리자 메뉴 CRUD + 초기 데이터 기반 구축
- 의존성: P13-T29
- 산출물: `menu/entity/Menu.java`, `menu/entity/MenuTargetType.java`, `menu/repository/MenuRepository.java`, `menu/dto/MenuRequest.java`, `menu/dto/MenuResponse.java`, `menu/dto/MenuVisibilityRequest.java`, `menu/dto/MenuOrderRequest.java`, `menu/service/MenuService.java`, `menu/controller/AdminMenuController.java`, `menu/controller/AdminMenuViewController.java`, `templates/admin/menu/list.html`, `templates/admin/menu/form.html`, `templates/admin/layout/sidebar.html`, `common/exception/ErrorCode.java`, `db/migration/V3__create_menu_table.sql`, `db/migration/V4__seed_initial_menu.sql`, `src/test/java/com/monicalab/menu/**`, `src/test/js/admin/menu-admin-view.test.js`, `src/test/java/com/monicalab/admin/controller/AdminViewControllerTest.java`
- 작업 내용: 현재 공개 헤더가 하드코딩된 문제를 해소하기 위한 동적 메뉴 관리 시스템의 1단계다. 이 Task는 Menu 도메인과 관리자 CRUD, 초기 시드 데이터만 구축하며 공개 헤더(`home/layout/*`, `home.css`)는 전혀 변경하지 않는다(동적 공개 렌더링은 후속 P13-T30B).
  1. `Menu` Entity는 ERD.md의 no-FK 원칙에 따라 `parentId`를 `@ManyToOne` 없는 plain `Long` 컬럼으로 둔다. 필드: `label`(VARCHAR(50), NOT NULL), `parentId`(BIGINT, NULL), `targetType`(VARCHAR(20), NOT NULL, `@Enumerated(STRING)`), `targetValue`(VARCHAR(255), NULL), `sortOrder`(INT, NOT NULL), `isVisible`(BOOLEAN, NOT NULL), `openInNewTab`(BOOLEAN, NOT NULL).
  2. `MenuTargetType`: `GROUP`, `HOME`, `PAGE`, `PROGRAM_LIST`, `BOARD_LIST`, `INTERNAL_URL`, `EXTERNAL_URL`. `GROUP`은 항상 최상위(`parentId == null`)이고 `targetValue`는 null이어야 한다. 부모가 될 수 있는 것은 `targetType == GROUP`인 메뉴뿐이며(그 외 링크형 타입은 부모가 될 수 없음), 이 규칙이 곧 최대 2-depth를 보장하므로 별도 depth 컬럼/카운터는 두지 않는다.
  3. `MenuService`가 `targetType`별 `targetValue` 검증을 수행한다: `GROUP`/`HOME`은 null 강제, `PAGE`/`PROGRAM_LIST`/`BOARD_LIST`는 각각 `PageType`/`ProgramType`/`BoardType` enum 값(PROGRAM_LIST/BOARD_LIST는 비우면 전체 의미로 null 허용), `INTERNAL_URL`은 `/`로 시작하고 `//`(프로토콜 상대 URL)를 거부하며 `java.net.URI` 파싱으로 host/scheme이 없는지 한 번 더 확인, `EXTERNAL_URL`은 `^https?://.+`만 허용(`javascript:`/`data:`/`//` 거부). `openInNewTab`은 `targetType`과 무관하게 관리자가 독립적으로 선택 가능하다(EXTERNAL_URL이라고 자동으로 true가 되지 않음).
  4. 관리자 목록(`getAdminList()`)은 부모 바로 뒤에 그 자식들이 오는 순서로 응답한다(단일 `ORDER BY`로 표현 불가능해 조회 후 애플리케이션에서 인터리빙). 정상 CRUD는 orphan 행(부모가 없거나 GROUP이 아닌 부모를 가리키는 행)을 만들 수 없지만, 방어적으로 그런 행이 존재해도 응답에서 누락되지 않고 끝에 포함되도록 두 번째 패스를 둔다.
  5. 삭제 및 GROUP→비GROUP 타입 변경은 자식이 존재하면 `MENU_HAS_CHILDREN`(409)로 금지한다. `sortOrder`는 Banner와 동일하게 관리자 입력 숫자 + "순서 변경" 버튼 방식이며 드래그앤드롭은 두지 않는다.
  6. Flyway는 `V3__create_menu_table.sql`(DDL)과 `V4__seed_initial_menu.sql`(시드)로 분리한다. 시드는 현재 공개 헤더(P13-T17 바로가기 메뉴)에 이미 존재하는 4개 링크(연구소 소개→PAGE/INTRODUCTION, 프로그램→PROGRAM_LIST, 강의 후기→BOARD_LIST/REVIEW, 게시판→BOARD_LIST)만 동일한 의미로 옮기며, 4개 모두 최상위(부모 없음)라 self-reference 문제가 없다. 아직 확정되지 않은 HOME/ABOUT/OUR PROGRAMS 등 신규 IA는 이번 시드에 포함하지 않는다. Page 도메인의 `ApplicationRunner` 방식(고정 리소스 재생성 방지)은 Menu에 적용하지 않는다 — Menu 행은 관리자가 자유롭게 삭제 가능해야 하므로 Flyway 시드가 더 적합하다.
  7. 관리자 API/화면은 Banner의 `Admin{Domain}Controller`/`Admin{Domain}ViewController` 분리 패턴을 그대로 따른다: `GET/POST /api/admin/menus`, `GET/PUT/DELETE /api/admin/menus/{id}`, `PATCH .../visibility`, `PATCH .../order`, 화면은 `GET /admin/menus`, `/admin/menus/new`, `/admin/menus/{id}/edit`. 사이드바(`admin/layout/sidebar.html`)에 "메뉴 관리"(`/admin/menus`) 항목을 파일 관리 다음에 추가한다. `form.html`의 상위 메뉴 `<select>`는 `targetType === 'GROUP' && !menu.parentId`(자기 자신 제외)만 후보로 표시한다.
  8. `ErrorCode`에 `MENU_NOT_FOUND`(404), `MENU_HAS_CHILDREN`(409)를 추가한다. `SecurityConfig`는 기존 `/admin/**`, `/api/admin/**` 블랭킷 규칙이 이미 적용되므로 변경하지 않는다.
- DoD:
  - `MenuService` 검증 전체(부모=GROUP 강제, GROUP 최상위/targetValue null 강제, 자기 자신 parent 금지, 존재하지 않거나 GROUP이 아닌 parent 거부, 타입별 targetValue 검증, INTERNAL_URL의 `//` 거부, EXTERNAL_URL의 `javascript:` 거부, orphan 행 비유실, 자식 존재 시 삭제/타입변경 409)이 `AdminMenuControllerTest`(MockMvc) 통합 테스트로 검증된다.
  - `AdminMenuViewControllerTest`로 미인증 리다이렉트, 인증 시 목록/등록/수정 화면 200 및 공통 레이아웃(`#admin-header`, `#admin-sidebar`) 포함을 검증한다.
  - `menu-admin-view.test.js`(Node 내장 테스트 러너)로 `list.html`/`form.html`이 공통 fetch 유틸을 사용하고, 드래그앤드롭/nudge 버튼이 없으며, 상위 메뉴 select가 GROUP-only로 필터링됨을 정적 검증한다.
  - 사이드바에 링크 추가로 `AdminViewControllerTest.dashboardRendersSidebarWithAllAdminDomainLinks`의 기대 목록에 `/admin/menus`가 추가된다(의도된 직접 영향).
  - `./gradlew build` 성공, Java 전체 테스트 통과, Node 전체 테스트 통과(무변경 기존 + 신규), 기존 Playwright 공개 페이지 회귀 전체 통과(공개 헤더/푸터/CSS 무변경이므로 신규 공개 케이스 없음).
  - `home/layout/*`, `home.css`, `SecurityConfig.java`, Program/Board/Page/Banner/Popup 도메인, `docker-compose.local-test.yml`(untracked 유지)은 변경하지 않는다.
  - 공개 헤더의 동적 렌더링, 공개 `GET /api/menus`, 2단계 드롭다운 UI, 신규 메뉴 IA(HOME/ABOUT/OUR PROGRAMS 등) 확정은 이번 Task 범위에 포함하지 않으며 P13-T30B 이후로 명시적으로 이연한다.

---

### P13-T30B. 공개 헤더 동적 메뉴 렌더링 + 데스크톱 dropdown + 모바일 submenu
- 의존성: P13-T30A
- 산출물: `menu/dto/HeaderMenuItem.java`, `menu/service/MenuService.java`, `menu/controller/HeaderMenuControllerAdvice.java`, `home/layout/header.html`, `home/layout/default.html`, `static/js/home/nav-submenu.js`, `static/css/home.css`, `src/test/java/com/monicalab/menu/controller/HeaderMenuControllerAdviceTest.java`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `src/test/java/com/monicalab/common/exception/GlobalExceptionHandlerTest.java`, `src/test/js/home/nav-submenu.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: P13-T30A가 만든 Menu 데이터를 공개 헤더에 연결한다. 최종 IA(HOME/ABOUT/OUR PROGRAMS 등)는 아직 확정되지 않았으므로 임의 생성하지 않고, 현재 V4 seed의 기존 4개 메뉴만으로 동적 렌더링 구조를 완성하는 것을 기본 방향으로 한다(신규 IA 연결은 P13-T30C).
  1. `MenuService.getPublicMenuTree()`(신규)가 요청당 `findAll()` 1회로 전체 Menu를 조회해 트리를 구성한다. `getAdminList()`는 재사용하지 않는다(계약이 다름 - 관리자는 숨김/orphan도 노출, 공개는 visible 트리만). 캐시 없음.
  2. 공개 visibility 계약: `visible=true`인 최상위 항목만 후보. `visible=false` GROUP의 자식은 자식 자신의 visible과 무관하게 전부 제외. GROUP의 자식 중 `visible=false`도 제외. visible 자식이 하나도 없는 GROUP은 GROUP 자체도 숨긴다.
  3. fail-closed: orphan(부모가 없거나 GROUP이 아닌 parentId를 가리키는) 행과, GROUP의 자식인데 그 자신도 `targetType=GROUP`인 비정상 행(children 구성 시 `targetType != GROUP` 조건으로 명시 제외)은 정상 트리에 노출되지 않는다. 복구/자동수정 로직은 두지 않는다.
  4. `menu/dto/HeaderMenuItem.java`(신규, `id`/`label`/`href`/`openInNewTab`/`children`)가 공개 전용 View 모델이다. Entity/`MenuTargetType`을 Thymeleaf에 노출하지 않으며, `href`가 `null`인 항목만 GROUP이라는 사실만으로 템플릿이 분기한다. targetType→href 계산(`GROUP→null`, `HOME→/`, `PAGE→/pages/{targetValue}`, `PROGRAM_LIST`/`BOARD_LIST`는 값 없으면 `/programs`·`/boards`, 있으면 `?programType=`·`?boardType=` 쿼리, `INTERNAL_URL`/`EXTERNAL_URL`은 검증된 값 그대로)은 `MenuService`가 전담하고 Thymeleaf는 targetType 분기를 하지 않는다.
  5. `menu/controller/HeaderMenuControllerAdvice.java`(신규)가 `@ControllerAdvice(assignableTypes = {HomeController, PageViewController, ProgramViewController, BoardViewController})`로 이 4개 공개 View Controller에만 `headerMenuItems` model attribute를 공급한다. 관리자/API Controller에는 영향 없음.
  6. `home/layout/header.html`의 `#quick-menu`를 `headerMenuItems` 기반 동적 렌더링으로 교체하되 `#quick-menu`/`#site-nav`/`aria-label="주요 메뉴"` 등 기존 id/semantic 구조는 유지한다. 일반 메뉴는 `<a>`, GROUP은 `<a href="#">`가 아닌 `<button type="button" aria-expanded aria-controls>`로 렌더링(submenu id는 `submenu-{menu.id}`로 안정적으로 생성). `openInNewTab=true`이면 `target="_blank" rel="noopener noreferrer"`를 함께 렌더링.
  7. `static/js/home/nav-submenu.js`(신규 파일, `nav-toggle.js`는 수정하지 않음)가 desktop hover(`mouseenter`/`mouseleave`를 `.has-submenu` 전체에 바인딩, `matchMedia(hover: hover)`로 터치 기기 제외)/focus(`focusin`/`focusout`)/click, 다른 GROUP 열면 기존 GROUP 닫힘, Escape(닫고 trigger로 focus 복귀), outside-click 닫힘을 구현한다. 실제 open 상태와 `aria-expanded`가 항상 일치하도록 `is-open` 클래스를 단일 source of truth로 사용하고(CSS `:hover`/`:focus-within`으로 독립적으로 열리는 경로는 두지 않음), `#nav-toggle` 클릭 이벤트를 함께 구독해 hamburger가 닫힐 때 열린 submenu도 초기화한다(custom event/전역 mutable state 없이 nav-toggle.js가 이미 갱신한 `aria-expanded` 값을 읽기만 함). `resolveOpenGroupId` 순수 함수만 Node 테스트 대상.
  8. `home.css`에 `#site-header{position:relative;z-index:10}`(popup의 `z-index:1000`보다 낮음), `.site-nav__item.has-submenu`, `.site-nav__trigger`, `.site-nav__submenu`, `.has-submenu.is-open .site-nav__submenu` 규칙을 추가. 모바일(`@media max-width:767.98px`)은 `.site-nav__submenu`의 position/box-shadow/border만 무력화해 accordion으로 표시(열림/닫힘 규칙은 공유, 중복 선언 없음). 768px breakpoint 체계는 변경하지 않는다(향후 최종 IA 확정 후 T30C에서 재검토).
  9. `HomeControllerTest`(P13-T30B로 인해 `#quick-menu`가 DB 기반이 되어 다른 테스트 클래스의 Menu 데이터 변경에 실행 순서가 영향받을 수 있으므로) `@BeforeEach`에서 Menu 4건을 V4 seed와 동일하게 직접 재구성하도록 수정한다. `GlobalExceptionHandlerTest`(`@WebMvcTest`)는 Spring Boot 표준 동작상 `controllers` 필터와 무관하게 모든 `@ControllerAdvice` 빈을 컨텍스트에 포함시키므로, 신규 `HeaderMenuControllerAdvice`의 `MenuService` 의존성을 `@MockitoBean`으로 대체해 컨텍스트 기동 실패를 해소한다(둘 다 P13-T30B의 의도된 직접 영향, 이 Task가 도입한 신규 기능 자체는 아님).
  10. Footer(`home/layout/footer.html`)는 이번 Task에서 변경하지 않는다. 신규 PageType/BoardType/ProgramType, 신규 Flyway migration, Bootstrap JS, 캐시, 3-depth, drag-and-drop 정렬, WAI-ARIA menubar 화살표 키보드 모델은 이번 Task 범위에 포함하지 않는다.
- DoD:
  - `HeaderMenuControllerAdviceTest`(Java): visible 메뉴만 노출, hidden leaf 제외, hidden GROUP의 visible child 전체 숨김, 모든 child hidden이면 GROUP 숨김, 정상 GROUP+leaf child 렌더링(button/aria-expanded/aria-controls/submenu), GROUP인 비정상 child 제외, orphan child 제외, non-GROUP parent의 child 제외, targetType별 href 7종, openInNewTab일 때만 target/rel, menu 0건에서도 200, `/`·`/pages/INTRODUCTION`·`/programs`·`/boards` 4곳 모두 `headerMenuItems` 적용, 관리자 View(`/admin/banners`)에는 미적용(model에 `headerMenuItems` 없음) — 전부 통과.
  - `HomeControllerTest`의 기존 "고정 4개 링크" 검증이 Menu 시드 기반으로도 href/텍스트/순서 완전 동일하게 회귀 통과(무변경 assertion).
  - Node: `nav-submenu.test.js`(`resolveOpenGroupId`) + 전체 회귀 통과.
  - Playwright: desktop 1440(hover 열림+`aria-expanded=true`, submenu로 pointer 이동해도 유지, 영역 밖 이탈 시 닫힘+`aria-expanded=false`, click toggle, 다른 GROUP 열면 기존 닫힘, Escape+trigger focus 복귀, child 링크 동작, keyboard Tab/Enter, overflow 없음), mobile 375(hamburger→GROUP tap→submenu expand/aria 동기화/child 접근, hamburger close 시 submenu reset, overflow 없음), 768/1024 기존 반응형 회귀 — 테스트용 GROUP/child는 admin API로 생성 후 정리(V4 seed·다른 3개 메뉴 무변경). 기존 seed 4개 관련 8개 assertion 무변경 통과. 전체 Playwright 회귀 통과.
  - `./gradlew build` 성공.
  - `docs/API.md`(공개 API 미신설), `home/layout/footer.html`, `SecurityConfig.java`, `V3__create_menu_table.sql`/`V4__seed_initial_menu.sql`, `docker-compose.local-test.yml`(untracked 유지) 무변경.

---

### P13-T30C. 최종 메뉴 IA 연결 + 전체메뉴(Mega Menu)
- 의존성: P13-T30B
- 산출물: `db/migration/V5__update_menu_ia.sql`, `home/layout/header.html`, `static/css/home.css`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `src/test/java/com/monicalab/menu/controller/HeaderMenuControllerAdviceTest.java`, `src/test/java/com/monicalab/support/MenuIaMigrationTest.java`(신규), `frontend-tests/visual-regression.spec.js`
- 작업 내용: P13-T30B가 만든 동적 메뉴 인프라에 실제 최종 IA를 연결한다. HOME/ABOUT/OUR PROGRAMS 등 과거 논의된 가안은 PRD.md/FEATURES.md 어디에도 문서화되지 않아("Blog" 포함) 채택하지 않고, 실제 문서화된 콘텐츠(PageType 4종/ProgramType 2종/BoardType 4종)만으로 최종 IA를 구성한다.
  1. 최종 IA: `HOME`(정적 `/` 링크, Menu DB row 아님) + `연구소 소개`(GROUP: 인사말/연구소 소개/연혁/오시는 길) + `프로그램`(GROUP: 수강 프로그램/특강) + `게시판`(GROUP: 공지사항/갤러리/자료실/강의 후기) + `전체메뉴`(mega menu trigger, Menu DB row 아님). Menu 테이블은 3 GROUP + 10 child = 13행.
  2. `V5__update_menu_ia.sql`(신규, V1~V4 무수정)이 V4가 심은 4개 flat 행을 내용 전체 일치(label+parent_id IS NULL+target_type+target_value)로 정밀 식별해 삭제하고(`DELETE FROM menu` 전체 삭제 아님), `LAST_INSERT_ID()` 세션 변수로 GROUP 생성 직후 그 id를 자식 INSERT에 사용해 13행을 재구성한다. 이 시점까지 Menu 도메인이 main 브랜치에 배포된 적이 없어(`git merge-base origin/main origin/develop`가 P12-T3 시점) 실제 관리자 커스터마이징이 존재할 수 없음을 확인하고 진행한 1회성 조치다. **이후로는 관리자가 CRUD로 수정한 메뉴 데이터를 향후 migration이 DELETE 후 재생성(destructive reset)하는 방식으로 다루지 않는다** — 필요한 구조 변경은 기존 행 UPDATE 또는 신중한 선택적 추가로 처리한다.
  3. `MenuService`/`HeaderMenuItem`/`HeaderMenuControllerAdvice`는 무수정. 전체메뉴(mega menu)는 개별 GROUP dropdown과 동일한 `headerMenuItems`를 `header.html`에서 한 번 더 순회해 렌더링하며(신규 Java 조회 없음), 최상위 LEAF/GROUP 분기·visibility·openInNewTab 정책이 개별 dropdown과 항상 동일하게 유지된다(향후 관리자가 최상위에 LEAF를 추가하거나 GROUP을 LEAF로 바꿔도 두 UI가 어긋나지 않음).
  4. `static/js/home/nav-submenu.js`는 무수정. `.site-nav__item.has-submenu`를 문서 전체에서 범위 제한 없이 스캔하는 기존 구조 덕분에 "전체메뉴" 트리거가 기존 hover/click/keyboard/Escape/outside-click 메커니즘과 단일 상태 머신을 그대로 공유한다(GROUP dropdown과 mega menu가 별도 상태를 갖지 않아 충돌 자체가 불가능).
  5. `home.css`에 `.site-nav__megamenu`(3컬럼 grid, `right:0;left:auto`로 뷰포트 밖 이탈 방지, `#site-nav` prefix로 specificity를 높여 `display:grid`가 기존 `display:block` 규칙보다 항상 우선) 추가. 모바일에서는 `[data-menu-id="all"]`을 `display:none`으로 숨겨 hamburger accordion과 중복 노출되지 않게 한다. 768px breakpoint 체계는 실측(767/768/769px) 결과 문제가 없어 변경하지 않는다.
  6. `MenuIaMigrationTest`(신규)는 `AbstractIntegrationTest`의 공유 컨테이너를 상속하지 않고 자체 `@ServiceConnection` MariaDB 컨테이너로 별도 Spring context를 띄워, 다른 테스트의 `menuRepository.deleteAll()`과 완전히 격리된 상태에서 JDBC로 V1~V5 clean migration 성공, 최종 13행, 최상위 GROUP 정확히 3개, GROUP별 child 수(4/2/4), parent_id/target_type/target_value/sort_order 정확성, 구 4건 소멸을 검증한다.
  7. `HomeControllerTest`/`HeaderMenuControllerAdviceTest`/`visual-regression.spec.js`의 seed 픽스처와 assertion을 13행 구조로 재작성한다. HOME과 전체메뉴가 항상 렌더링에 포함되므로 "Menu DB가 실제로 만든 항목만" 검증하려는 assertion은 전부 `data-menu-id` 기반 구조 selector로 HOME(`:first-child`)과 전체메뉴(`[data-menu-id="all"]`)를 명시적으로 제외해 스코프한다. "연구소 소개"가 GROUP명이자 자식명으로 동시에 쓰이므로 텍스트 기반 selector는 사용하지 않는다.
  8. Footer, `SecurityConfig`, 신규 PageType/BoardType/ProgramType, `/boards`·`/programs`·상세 URL/Controller/API는 변경하지 않는다.
- DoD:
  - `MenuIaMigrationTest` 8건 전부 통과(clean migration 성공, 13행, GROUP 3개, child 4/2/4, target 값/순서 정확, 구 4건 소멸).
  - `HeaderMenuControllerAdviceTest`/`HomeControllerTest` 전체 통과(HOME/전체메뉴를 제외한 스코프에서 검증).
  - Playwright: HOME 정적 이동, 3개 GROUP dropdown 콘텐츠(대표 1개는 hover+click+aria-expanded까지, 나머지 2개는 콘텐츠만), 전체메뉴(hover/Escape/outside-click/10개 링크 3컬럼/viewport 이탈 없음, 1024·1440 실측), 767/768/769px 경계에서 desktop/mobile off-by-one 없음, mobile 375에서 전체메뉴 미노출(hamburger accordion과 중복 없음), 기존 `/boards`·`/programs` 직접 접근 정상 — 전부 통과. 기존 P13-T30A/B Playwright 테스트(자체 임시 GROUP 사용) 전체 무회귀 통과.
  - Java/Node 전체 회귀 통과, `./gradlew build` 성공.
  - `docker-compose.local-test.yml`(untracked 유지), DB volume 보존.

---

### P13-T30D(A2). Header desktop/tablet nav 배치·타이포그래피 최적화
- 의존성: P13-T30C
- 산출물: `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: P13-T30C가 확정한 최종 IA(top-level 8개)는 그대로 두고, desktop/tablet에서 top-level nav의 배치(로고 대비 치우침)와 타이포그래피(font-size/gap)만 최적화한다. IA/기능/Menu DB/`header.html`/JS는 변경하지 않는다.
  1. desktop nav 시작 breakpoint를 기존 768px에서 900px로 상향한다. 실측 결과 최종 IA 8개는 기존 폰트(16px)/gap(20px) 기준으로 768px에서 물리적으로 한 줄에 들어가지 않고(필요 폭 대비 부족), 812px부터 성립하며 900px가 Windows 스크롤바 등 실측 오차를 흡수하고도 여유가 남는 지점이다. `home.css`의 `@media (min-width: 900px)`(A1 hover/open 강조 + A2 배치 규칙 통합)와 `@media (max-width: 899.98px)`(mobile accordion)가 이 값 하나만 공유해 hybrid 구간이 생기지 않는다.
  2. desktop 구간에서 `#site-nav{flex:1 1 auto}` + `#quick-menu{justify-content:center}`로 nav가 로고 오른쪽 잔여 폭을 차지하고 그 안에서 중앙 정렬되도록 해, 기존 `space-between`이 만들던 심한 우측 치우침(1440px에서 로고-nav 간격 실측 612px)을 로고 폭 기준 상수 오프셋 수준으로 줄인다. font-size는 16px(900px)~18px(1024px 이상)로, gap은 20px(900px)~28px(1440px)로 자연스럽게 커진다(둘 다 기존 값 아래로 축소되지 않음). `#quick-menu > li{display:flex;align-items:center}`로 A1이 막았던 trigger-submenu pointer dead-zone이 소수점 font-size에서 재발하지 않게 한다.
  3. `flex-wrap:wrap`은 기본 8개 IA를 배치하는 수단이 아니라, 관리자가 top-level Menu를 추가해 8개보다 많아졌을 때 overlap/overflow 대신 header 세로 높이 증가를 선택하는 degradation 전용 안전장치로만 사용한다.
- DoD:
  - 900/901/1024/1366/1440px에서 최종 IA 8개가 label 줄바꿈 없이 1줄로 배치, top-level item 겹침 없음, 로고-nav 겹침 없음, 전체메뉴 viewport 이탈 없음.
  - 375/767/768/899px에서 desktop nav가 노출되지 않고 hamburger/accordion 기존 동작 무회귀.
  - P13-T30D(A1)의 hover/open dead-zone 없음·mouse trajectory 유지·활성 강조(font-weight 700) 무회귀(900/1024/1440px 재확인).
  - top-level Menu가 8개를 초과하는 synthetic 상황에서도 겹침/overflow 없이 wrap degradation, 전체메뉴 계속 접근 가능.
  - 기존 Playwright 전체(P13-T1~T30C) 무회귀 통과, Java/Node 전체 회귀 통과, `./gradlew build` 성공.
  - `docker-compose.local-test.yml`(untracked 유지), DB volume 보존.

---

### P13-T30E(Task B). Admin Menu UI Polish
- 의존성: P13-T30D(A2)
- 산출물: `admin/menu/list.html`, `admin/menu/form.html`, `static/css/admin/admin.css`, `src/test/js/admin/menu-admin-view.test.js`
- 작업 내용: 기능은 그대로 두고 관리자 메뉴 목록/폼 화면의 가독성·입력 UX만 개선한다. `/api/admin/menus*` API 계약, Menu Entity/Service/DTO/Controller, 공개 Header(`getPublicMenuTree`/`header.html`/`home.css`), Flyway/Menu 데이터는 전혀 건드리지 않는다.
  1. `list.html`: GROUP/자식 계층을 `menu.parentId ? '— ' : ''` 텍스트 접두사 대신 `admin-menu-row--child` CSS class(들여쓰기+"└", `admin.css`)로 표시. `targetType`을 raw enum 대신 Bootstrap badge(`text-bg-*`)로, `targetValue`/`targetSubvalue`를 raw 값 대신 사람이 읽는 한글 라벨(매핑에 없는 값은 raw fallback)로 표시. GROUP도 다른 행과 동일하게 실제 `visible` 값을 배지로 보여준다(GROUP만 예외 처리하지 않음). GROUP의 대상은 "-", HOME의 대상은 "홈"으로 구분 표시.
  2. `form.html`: `targetType` select는 `value`(서버 전송 enum) 무변경, 표시 텍스트만 한글화. `targetValue` 자유 텍스트 input은 그대로 두고 `targetType`별 후보를 `<datalist>`로 제공(오타 감소 목적, 임의 값 입력 자체는 막지 않음). Parent select는 필터링 로직 무변경, 표시 텍스트에만 "(그룹)" 접미사 추가. `targetSubvalue` 조건부 표시/드래그앤드롭 없음/sort_order 숫자 입력+PATCH 방식은 기존 그대로 유지.
  3. `menu-admin-view.test.js`: 표현이 바뀐 지점(들여쓰기)만 국소적으로 갱신하고, 기존 계약 assertion(POST/PUT 분기, parentId 변환, targetSubvalue 조건부 표시/정규화, order PATCH payload, 7종 option value)은 그대로 유지. badge/target 라벨/fallback/datalist 신규 assertion 추가.
- DoD:
  - Menu CRUD API 계약·Entity/Service/Controller/DTO·Flyway·Menu 데이터·공개 Header 전부 무변경.
  - GROUP/LEAF 계층이 목록에서 즉시 식별 가능(들여쓰기+badge), raw enum 대신 한글 라벨 표시, 매핑에 없는 값은 raw fallback.
  - GROUP 포함 모든 행이 실제 공개/숨김 상태를 배지로 표시.
  - Form `targetValue` datalist 제공, 기존 parent GROUP-only 필터링·targetSubvalue 조건부 동작 무회귀.
  - `menu-admin-view.test.js` 전체 통과, Java 전체 회귀 통과, Playwright 기존(T1~T30D/A2) 전체 회귀 통과.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T31. Admin 목록 필터(boardType/programType) 즉시 적용
- 의존성: P13-T30E(Task B)
- 산출물: `admin/board/list.html`, `admin/program/list.html`, `src/test/js/admin/board-admin-view.test.js`, `src/test/js/admin/program-admin-view.test.js`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 게시판/프로그램 관리자 목록에서 `#searchBoardType`/`#searchProgramType` select를 검색 버튼 없이 변경 즉시 재조회하도록 순수 프론트 이벤트 배선만 추가한다. 조사 결과 이 패턴(필터 select + 검색 버튼)을 가진 admin 목록 화면은 게시판/프로그램 2개뿐이며(팝업/배너/페이지/파일/메뉴 관리는 필터 select 자체가 없음), API/Service/DTO는 이미 조합 필터링을 지원하므로 서버는 전혀 건드리지 않는다.
  1. 각 template의 기존 `submit` 핸들러(검색 버튼)는 무변경. `#searchBoardType`/`#searchProgramType`에 `change` 리스너를 추가해 `state.page = 0`, `state.boardType`(또는 `programType`)만 갱신 후 기존 `loadBoards()`/`loadPrograms()`를 그대로 재사용한다.
  2. `change` 핸들러는 `state.keyword`를 다시 읽지 않는다 - 검색어의 실행(커밋)은 여전히 기존 검색 버튼(submit)을 통해서만 이뤄진다. 이미 검색 버튼으로 확정된 keyword는 select 변경 후에도 유지되고, 아직 확정하지 않고 입력만 한 keyword는 select 변경으로 자동 실행되지 않는다(의도된 동작).
- DoD:
  - 게시판/프로그램 관리 모두 select 변경만으로(검색 버튼 클릭 없이) 목록이 즉시 갱신, page는 0으로 초기화.
  - 확정된 keyword는 select 변경 후에도 유지, 미확정 keyword 입력은 select 변경으로 실행되지 않음.
  - 기존 검색 버튼/pagination 동작 무회귀, Board/Program API 계약·Java 코드 무변경.
  - `board-admin-view.test.js`/`program-admin-view.test.js` 전체 통과, 신규 Playwright 통과, Java/Node/기존 Playwright 전체 회귀 통과.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T32. 공개 메인 배너 caption 제거(접근성 유지)
- 의존성: P13-T31
- 산출물: `home/index.html`, `static/css/home.css`, `HomeControllerTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 공개 메인 Hero의 시각적 배너 title caption을 미노출 처리한다. `Banner.title` 데이터/관리자 배너 목록·등록·수정 기능은 무변경이며, `img alt`/indicator `aria-label`을 통한 접근성 정보는 그대로 유지한다.
  1. `home/index.html`의 `<p class="hero__caption" th:text="${banner.title()}"></p>`를 제거한다(`display:none` 대신 template에서 출력 자체를 없앰). `img alt`, indicator `aria-label` 바인딩은 그대로 둔다.
  2. `home.css`의 `.hero__caption` 전용 규칙을 제거한다. 다른 Hero CSS(레이아웃/크기/반응형)는 무변경.
  3. `HomeControllerTest.java`에 `.hero__caption` 부재 + `img alt`/indicator `aria-label`이 `Banner.title` 값과 일치함을 확인하는 회귀 테스트를 추가한다.
- DoD:
  - 공개 Hero에서 `.hero__caption` DOM/CSS 모두 제거, `img alt`/indicator `aria-label`은 `Banner.title` 값 그대로 유지.
  - `Banner.title` 데이터·관리자 배너 기능·hero-carousel.js·Hero layout/반응형 무변경.
  - Java 신규 테스트 통과, Java 전체 build 통과, Node 전체 회귀 통과, Playwright 신규 통과 + 기존 전체 무회귀.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T33. 강의 후기 단일 게시판 IA 수정
- 의존성: P13-T32
- 산출물: `db/migration/V10__collapse_review_menu_to_single_leaf.sql`(신규), `src/test/java/com/monicalab/support/MenuIaMigrationTest.java`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 발주처 요구사항 재확인 결과 "강의 후기"는 하나의 게시판(BoardType.REVIEW)이며 공개 top-level navigation에는 "강의 후기" 하나만 존재해야 한다. V8(Task C)이 "강의 후기"를 GROUP(수강 후기/특강 후기 child 2개)으로 만든 것이 이 요구와 어긋남을 확인해 되돌린다. Board 도메인(Entity/DTO/Service/Controller/관리자 화면)과 공개 게시판 내부 subtype 필터(`home/board/list.html`의 `#board-type-filter`, `/boards?boardType=REVIEW`(`&programType=COURSE|SPECIAL`))는 처음부터 요구사항과 정확히 일치했으므로 전혀 건드리지 않는다.
  1. V10이 기존 "강의 후기" GROUP 행(id 하드코딩 없이 label+target_type+parent_id 조합으로 식별)의 자식 중 REVIEW+COURSE/REVIEW+SPECIAL 조합으로 정밀 식별되는 2행만 DELETE하고, 그 GROUP 행 자체는 같은 id/label/sort_order/is_visible/open_in_new_tab을 유지한 채 `target_type=BOARD_LIST, target_value=REVIEW, target_subvalue=NULL`로 UPDATE한다(V1~V9 무수정, DELETE는 광범위 조건 금지).
  2. `MenuService`/`HeaderMenuItem`/`header.html`/`nav-submenu.js`/`admin/menu/list.html`/`admin/menu/form.html`은 무수정 - 기존 top-level LEAF(BOARD_LIST) 렌더링 분기가 이미 이 케이스를 처리한다.
- DoD:
  - 공개 Header/Mobile accordion/Mega Menu에 "강의 후기" top-level LEAF 1개만 존재(`/boards?boardType=REVIEW`), 수강 후기/특강 후기 child menu 없음.
  - `/boards?boardType=REVIEW`(전체)·`&programType=COURSE`·`&programType=SPECIAL` 게시판 내부 필터 전부 무회귀.
  - Board 코드/Footer 무변경, V1~V9 무수정, V10 forward migration(V9→V10) 정상 적용.
  - `MenuIaMigrationTest`/`visual-regression.spec.js` 신규·수정 케이스 전부 통과, 기존 Board subtype 필터 테스트 무회귀.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T34. Footer 사업자 정보 반영
- 의존성: P13-T33
- 산출물: `home/layout/footer.html`, `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 발주처가 웹페이지 하단에 필수로 요청한 연구소/사업자 정보를 모든 공개 페이지 공통 Footer(`home/layout/default.html`이 유일하게 include하는 `home/layout/footer.html`)에 반영한다. Footer navigation(연구소 소개/프로그램/강의 후기/게시판 4개 링크의 label/href/순서), `www.monicaenglish.com` self-link 구조(target 없음), 연구소명 브랜드 텍스트는 이번 Task의 대상이 아니므로 전혀 변경하지 않는다. Header/Menu/Board/Program/Banner/Popup, DB migration은 이번 Task와 무관하므로 건드리지 않는다.
  1. `footer.html`의 기존 nav 뒤, 기존 domain 문단 앞에 `<address class="site-footer__business">`(주소+전화만 포함)와 그 뒤 별도 `<p class="site-footer__reg-no">`(사업자등록번호)를 신규 삽입한다. 주소 문자열은 발주처 확정값 `성남시 분당구 황새울로 200번길 28, 1104-07호`을 정규화/교정 없이 그대로 사용한다. 전화번호는 `tel:070-4655-7905` 링크로 표시한다. 사업자등록번호는 링크 없이 텍스트로만 표시한다.
  2. 기존 `.site-footer__copyright` 문구를 `&copy; 모니카영어교육연구소`에서 `&copy; 2026 모니카영어교육연구소. All rights reserved.`로 교체한다(JS/Thymeleaf 동적 연도 계산 도입 없이 확정 연도를 그대로 사용).
  3. `home.css`에 `.site-footer__business`(`font-style: normal`), `.site-footer__business p`(`margin: 0`), `.site-footer__phone`/`.site-footer__reg-no`(`white-space: nowrap`)만 최소 추가한다. 기존 `#site-footer`/`.site-footer__inner`/`.site-footer__nav`/`.site-footer__site` 규칙은 무수정.
- DoD:
  - 모든 공개 페이지 공통 Footer(`/`, `/boards`, `/boards/**`, `/programs`, `/programs/**`, `/pages/**`)에서 연구소명 "모니카영어교육연구소", 주소 "주소: 성남시 분당구 황새울로 200번길 28, 1104-07호", 전화 "전화: 070-4655-7905"(`tel:070-4655-7905`), 사업자등록번호 "사업자등록번호: 220-10-28936", `www.monicaenglish.com`, "© 2026 모니카영어교육연구소. All rights reserved."가 정확히 표시된다.
  - 기존 Footer navigation 4개 링크의 label/href/순서와 `www.monicaenglish.com` self-link 구조(target 없음)가 무변경이다.
  - Header/Menu/Board/Program/Banner/Popup 무변경, 신규 DB migration 없음.
  - `visual-regression.spec.js`의 P13-T34 신규 케이스 전부 통과, 기존 Footer 관련 테스트(P13-T17 등) 무회귀.
  - 375/768/1024/1440에서 사업자 정보 포함 Footer가 horizontal overflow 없이 정상 표시된다.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T35. Header/Footer 반응형 최종 QA
- 의존성: P13-T34
- 산출물: `static/js/home/nav-submenu.js`, `frontend-tests/visual-regression.spec.js`, `frontend-tests/public-console-errors.spec.js`(신규)
- 작업 내용: 새로운 Header/Footer 기능·IA를 설계하지 않고, 현재까지 구현된 공개 Header/Footer(반응형/메뉴 동작/접근성/overflow/keyboard interaction/모바일 사용성/공통 layout 적용/Footer 사업자 정보/기존 기능 회귀/console error)를 최종 QA해 실제로 재현된 결함만 최소 범위로 수정한다.
  1. **A1(실제 결함, Docker 8088 실브라우저로 재현 확인 후 수정)**: 키보드로 GROUP/전체메뉴를 연 뒤 Escape 없이 Tab만으로 그룹 밖으로 포커스가 이동해도 `nav-submenu.js`에 `focusout` 처리가 없어 submenu가 열린 채로 남아 이후 콘텐츠 위에 겹칠 수 있었다. `nav-submenu.js`에 groupEl 단위 `focusout` 리스너를 추가해 새 포커스 대상이 그룹 밖이면 자동으로 닫는다. `closeOpenGroup()` 실행은 `setTimeout(0)`으로 다음 tick에 미룬다 — 실측 결과 focusout 핸들러 안에서 즉시 DOM을 바꾸면 지금 포커스를 가져간 바로 그 tap/click(예: 모바일 hamburger 토글) 자체의 click 이벤트가 브라우저에 의해 취소되는 회귀가 있었다. `relatedTarget`이 없는 경우(일부 브라우저에서 포커스가 완전히 사라지는 경우) 대비, 다음 tick의 `document.activeElement`로 한 번 더 판단한다. 기존 `openGroup`/`closeOpenGroup`/`setGroupOpen` 함수와 상태 변수를 그대로 재사용한다. DOM/CSS/Menu DB/API/IA는 변경하지 않는다.
  2. **B1(테스트 공백)**: `frontend-tests/admin-console-errors.spec.js`(관리자 전용)와 별도로 `frontend-tests/public-console-errors.spec.js`를 신규 생성해, 동일한 `page.on('pageerror')` 패턴으로 대표 public route(`/`, `/boards`, `/programs`, `/pages/GREETING`)의 최초 진입과 Header GROUP/mega menu/모바일 hamburger 상호작용(nav-submenu.js/nav-toggle.js 실행 경로) 중 pageerror가 없는지 확인한다. `console.error` 수집까지는 확장하지 않는다(false-positive 위험, 이 프로젝트에 의도적 사용처 없음).
  3. **B3(테스트 공백)**: `visual-regression.spec.js`에 375px에서 hamburger accordion을 실제로 사용한 뒤 `goto` 없이 같은 page에서 1440px로 `setViewportSize`했을 때 stale mobile 상태 없이 desktop UI(hamburger 숨김, nav 노출, 다른 GROUP hover 가능, overflow 없음)가 정상 동작하는지 확인하는 테스트를 추가한다.
  4. **B4(테스트 공백)**: `visual-regression.spec.js`에 실제 production "전체메뉴"(mega menu) trigger 자체의 키보드 접근성(Tab 도달 → Enter → Tab으로 첫 링크 접근 → Escape로 닫힘/포커스 복귀)을 검증하는 테스트를 추가한다(기존 키보드 테스트는 합성 GROUP만 사용했음).
  5. 조사 결과 Footer(P13-T34)와 Header CSS/DOM/Menu 서버 렌더링 로직에는 실제 결함이 없어 무변경으로 확인됐다(`header.html`/`footer.html`/`default.html`/`home.css`/`nav-toggle.js`/Menu 관련 Java/Menu DB 전부 무수정).
- DoD:
  - 375/768/1024/1440에서 Header/Footer 모두 horizontal overflow 없이 정상 표시된다.
  - Mobile hamburger accordion(열기/닫기/GROUP 접근/재오픈 초기화)이 정상 동작한다.
  - Desktop GROUP dropdown/mega menu가 hover/click/keyboard 전 경로에서 정상 동작하고, `aria-expanded`가 실제 상태와 동기화된다.
  - **키보드로 연 GROUP/mega menu는 포커스가 그 밖으로 이동하면 자동으로 닫힌다(A1).**
  - **전체메뉴(mega menu) 자체가 keyboard(Tab/Enter/Tab/Escape)로 정상 접근 가능하다(B4).**
  - **375px hamburger accordion 사용 후 `goto` 없이 1440px로 리사이즈해도 stale mobile 상태 없이 desktop UI가 정상 동작한다(B3).**
  - Footer의 P13-T34 확정 정보(연구소명/주소/전화/사업자등록번호/도메인/copyright)가 무회귀로 유지된다.
  - 모든 공개 공통 layout(6개 템플릿, 4개 Controller)에서 Header/Footer가 공통으로 유지된다.
  - 대표 public route(`/`, `/boards`, `/programs`, `/pages/GREETING`) 및 Header GROUP/mega menu/모바일 hamburger 상호작용 중 console pageerror가 없다(B1, `public-console-errors.spec.js`).
  - 기존 Menu/Footer navigation label/href/IA가 전부 무회귀다.
  - `./gradlew build`, Node/Playwright 전체 테스트가 통과한다.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T36. Public Header/Menu Visual Polish
- 의존성: P13-T35
- 산출물: `static/css/home.css`, `frontend-tests/visual-regression.spec.js`
- 작업 내용: 기존 Header/Menu 기능 계약(IA/label/href/depth/900px 경계/keyboard/focusout/dynamic resize)을 전혀 바꾸지 않고, hover/focus-visible/open 상태의 시각적 가시성만 CSS로 개선한다(디자인안 B "Warm Premium Tint" 채택 - 기존 `--color-primary`/`--color-surface`/`--color-border`/`--radius` 토큰만 재사용, 새 색상 체계 없음). active/current-page 표시는 이번 Task에서 제외한다(pathname/query 매칭 정책은 후속 Task로 분리).
  1. `:root`에 `--color-primary-soft: rgba(29, 78, 216, 0.08)`(`--color-primary`에서 파생) 1개만 추가한다.
  2. Desktop(`@media (min-width:900px)`): top-level LEAF는 `:hover`/`:focus-visible`에서 `background:var(--color-primary-soft)`+`color:var(--color-primary)`만 적용하고 **font-weight는 바꾸지 않는다**(glyph 폭 변화로 인한 wrap 회귀 방지). GROUP trigger는 기존 `:hover`/`.is-open`에 `:focus-visible`을 추가해 동일한 tint+`font-weight:700`(기존 값 유지)을 적용한다. top-level 항목에는 실제 padding(`0.1875rem 0.125rem`, 음수 margin 사용 안 함 - hit area가 인접 항목으로 확장되는 것을 피하기 위해 승인된 방식)을 추가하고, 899/900/901/1024px 실측(Playwright)으로 wrap/overflow 회귀가 없는 값을 확정했다(0.25rem 시도 시 900/901px에서 실제 2줄 wrap 회귀가 실측되어 더 작은 값으로 축소).
  3. `.site-nav__submenu a`(dropdown + mega menu 공통, 패널 자체는 무변경)에 `:hover`/`:focus-visible` tint를 추가한다. 기존 `a.site-nav__megamenu-heading:hover{underline}` 규칙은 위 통합 tint로 대체되어 제거한다.
  4. Mobile(`@media (max-width:899.98px)`): top-level/submenu 항목의 touch target padding을 확대하고(wrap 제약이 없는 세로 accordion이라 실제 padding 사용), `.has-submenu.is-open .site-nav__trigger`에 데스크톱과 동일한 tint+`font-weight:700`을 추가해 펼친 GROUP trigger 자체가 자식 목록 노출 여부와 무관하게 독립적으로 강조되도록 한다.
  5. 신규 `transition`은 `background-color`/`color`뿐이며(transform/motion 없음), `prefers-reduced-motion: reduce`에서 정확히 이번에 transition을 추가한 3개 selector(`#quick-menu > li > a`, `#quick-menu > li > .site-nav__trigger`, `.site-nav__submenu a`)로만 범위를 제한해 비활성화한다.
  6. `header.html`/`footer.html`/`default.html`/`nav-toggle.js`/`nav-submenu.js`/Menu Java(`HeaderMenuControllerAdvice` 포함)/DB는 무수정. P13-T30D(A1)/P13-T35가 "GROUP 단순 focus는 강조 없음"/"모바일은 desktop 강조 무적용"을 전제로 작성했던 기존 테스트 3곳(`visual-regression.spec.js`)은 이번 Task가 그 전제를 의도적으로 뒤집는 요구사항이라 새 동작에 맞게 갱신했다(무회귀 예외가 아니라 요구사항 자체의 변경).
- DoD:
  - Desktop top-level LEAF `:hover`/`:focus-visible`가 tint 배경+primary 색상을 제공하고 font-weight는 무변경이다.
  - Desktop GROUP trigger `:hover`/`:focus-visible`/`.is-open`이 전부 동일한 tint+`font-weight:700`을 제공한다(키보드 focus가 hover와 동등한 가시성).
  - Submenu/mega menu 일반 링크에 `:hover`/`:focus-visible` tint가 적용되고 패널 자체(배경/border/radius/shadow)는 무변경이다.
  - Mobile에서 펼친 GROUP의 trigger 자체가 닫힘 상태와 배경/색상/굵기로 명확히 구분되고, top-level/submenu 항목의 touch target이 기존보다 실제로 확대된다(WCAG 일반 권장치가 아니라 이번 프로젝트의 회귀 기준값으로 검증).
  - 899.98/900px 경계, 375/768/899/900/901/1024/1440에서 horizontal overflow 없음, top-level 메뉴가 한 줄(wrap 없음)로 유지된다(padding 값을 실측으로 확정).
  - submenu dead-zone(trigger-submenu 간격 0), z-index/popup stacking 무회귀.
  - 신규 transition이 `prefers-reduced-motion: reduce`에서 비활성화되고, native focus outline은 제거되지 않는다.
  - P13-T30B/C/D, P13-T35 전체(갱신된 3개 assertion 포함) + `public-console-errors.spec.js`/`admin-console-errors.spec.js` + `./gradlew build`(455개) 전부 통과.
  - `header.html`/`footer.html`/`nav-toggle.js`/`nav-submenu.js`/Menu Java/DB/관리자 UI/Docker 구성 무변경.
  - active/current-page 상태는 구현하지 않는다(후속 Task로 분리).
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T37. Header/Menu Active(Current Page) 표시
- 의존성: P13-T36
- 산출물: `menu/dto/HeaderMenuItem.java`, `menu/service/MenuService.java`, `menu/controller/HeaderMenuControllerAdvice.java`, `menu/support/CurrentLocation.java`(신규), `menu/support/HeaderActiveResolver.java`(신규), `home/layout/header.html`, `static/css/home.css`, `frontend-tests/visual-regression.spec.js`, `src/test/java/com/monicalab/menu/support/HeaderActiveResolverTest.java`(신규), `src/test/java/com/monicalab/menu/controller/HeaderMenuControllerAdviceTest.java`
- 작업 내용: P13-T36이 의도적으로 후속 Task로 분리했던 "현재 페이지에 해당하는 Header 메뉴 표시"를 구현한다. 전체 query string equality가 아니라 각 targetType의 semantic parameter(BOARD_LIST의 boardType/programType, PROGRAM_LIST의 programType)만 비교하고 `page`/`size`/`keyword`/`pageJump` 같은 목록 상태 noise는 무시한다. Menu Entity/Repository/DB/Flyway, `MenuService.toHref` 등 기존 URL 생성 규칙, 관리자 Menu CRUD, `BoardViewController`/`ProgramViewController`, `footer.html`, `nav-toggle.js`/`nav-submenu.js`는 전혀 건드리지 않는다.
  1. `HeaderMenuItem`에 `targetType`(`MenuTargetType`) 필드를 추가한다(href 문자열의 생김새만으로 BOARD_LIST/PROGRAM_LIST/PAGE를 추론하면, 관리자가 이미 만들 수 있는 INTERNAL_URL이 `/boards`나 `/pages/INTRODUCTION` 같은 값을 그대로 가리킬 때 오판정될 수 있기 때문). `MenuService`의 `HeaderMenuItem` 생성 두 곳(GROUP/leaf)에 기존 `menu.getTargetType()` 값을 그대로 전달한다.
  2. `com.monicalab.menu.support` 패키지에 view-support 전용 클래스 2개를 신규 추가한다: `CurrentLocation`(request의 path/raw queryString만 담는 값 객체, 파싱 로직 없음), `HeaderActiveResolver`(`@Component`, targetType 기준 switch로 active/hasActiveChild/isHomeActive를 판정하는 유일한 장소 — Menu Repository/Service 미의존, 추가 DB 조회 없음).
  3. `HeaderMenuControllerAdvice`에 `currentLocation` `@ModelAttribute`를 추가해 request path/queryString만 model에 공급한다(URL 파싱·판정 로직 없음 — advice 실행 시점에는 Board/Program 상세 Controller가 아직 `board`/`program` model을 채우기 전이라는 Spring MVC 제약 때문에 판정 자체는 시도하지 않는다).
  4. `header.html`은 resolver 호출 결과(boolean)만 소비한다 — Board/Program 상세 페이지에서는 이미 조회된 `board.boardType()`/`board.programType()`/`program.programType()`을 resolver에 값으로 전달해(추가 DB 조회 없음) query 유무와 무관하게 정확한 active를 얻는다(Program 상세는 목록 링크 자체에 query가 전혀 없어 이 override가 필수).
  5. 정책: 실제 목적지 leaf만 `.is-active`+`aria-current="page"`, GROUP은 `.has-active-child`만(`.is-active`/`aria-current` 없음). 동일 목적지가 primary dropdown과 mega menu 양쪽에 있으면 두 사본 모두 `.is-active`+`aria-current="page"`를 일관되게 부여한다(닫힌 mega menu는 접근성 트리에서 제외되므로 중복 announcement 위험이 없고, mega menu는 애초에 동일 메뉴 트리의 대안적 완전한 뷰라는 설계 의도와 일치).
  6. CSS는 P13-T36 토큰(`--color-primary`/`--color-primary-soft`/`--radius`)만 재사용하고, active 표시는 `box-shadow: inset`(박스 모델 비영향)으로 accent line을 추가해 hover/focus/open(P13-T36, 배경만·일시적)과 형태로도 구분한다. top-level LEAF는 active 상태에서도 font-weight를 바꾸지 않는다(P13-T36 원칙 유지).
- DoD:
  - HOME/PAGE/BOARD_LIST(목록)/PROGRAM_LIST(목록) 전부 semantic parameter 기준으로 정확히 active 판정되고 noise query에 영향받지 않는다.
  - Board/Program 상세 페이지에서 query 유무와 무관하게(직접 URL 방문 포함) 엔티티 실제 타입 기준으로 올바른 메뉴가 active된다.
  - `boardType`/`programType` 없는 `/boards`/`/programs`에서는 어떤 Header leaf도 active가 아니다(대응 항목 없음, 정상).
  - GROUP은 `.has-active-child`로만 표시되고 `.is-active`/`aria-current`를 갖지 않는다.
  - primary nav·mega menu 양쪽에 `.is-active`+`aria-current="page"`가 일관되게 적용된다.
  - top-level LEAF는 active 상태에서도 font-weight가 바뀌지 않는다.
  - 899.98/900px 경계, 375/768/899/900/901/1024/1440 overflow 무회귀, top-level 한 줄 유지, dead-zone 무회귀.
  - Menu Entity/Repository/DB/Flyway, `MenuService.toHref`, 관리자 Menu CRUD, `BoardViewController`/`ProgramViewController`, `footer.html`, `nav-toggle.js`/`nav-submenu.js` 무변경.
  - `HeaderActiveResolverTest`(pure JUnit) + `HeaderMenuControllerAdviceTest` 갱신 + P13-T37 Playwright 신규 케이스 + 기존 P13-T30~T36 전체 무회귀.
  - `./gradlew build`, Playwright 전체(Node) 통과.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T38A. 메인 고정 콘텐츠 Domain/Admin

- 의존성: P13-T37
- 산출물: `db/migration/V11__create_home_pinned_content_table.sql`(신규), `pinned/entity/HomePinnedContent.java`(신규), `pinned/entity/HomeTargetType.java`(신규), `pinned/repository/HomePinnedContentRepository.java`(신규), `pinned/service/HomePinnedContentService.java`(신규), `pinned/dto/*.java`(신규), `pinned/controller/AdminHomePinnedContentController.java`(신규), `pinned/controller/AdminHomePinnedContentViewController.java`(신규), `templates/admin/homepinned/list.html`(신규), `admin/layout/sidebar.html`, `common/exception/ErrorCode.java`, `docs/PRD.md`, `docs/FEATURES.md`, `docs/ERD.md`, `docs/API.md`, `docs/ARCHITECTURE.md`, `frontend-tests/admin-console-errors.spec.js`
- 작업 내용: 관리자가 기존 Board/Program 콘텐츠 중에서 선택해 메인 화면 상단에 고정하는 기능("인스타그램 고정 게시물"과 유사한 개념, 신규 콘텐츠 작성 기능이 아님)의 도메인/관리자 기능만 구축한다. 공개 메인 화면(`home/index.html`, `HomeController`) 렌더링은 이 Task에 포함하지 않고 P13-T38B로 이연한다. `home` 패키지는 자체 Entity를 가질 수 없다는 기존 원칙(ARCHITECTURE.md)에 따라 `com.monicalab.pinned`를 Menu와 동일한 완전히 독립된 최상위 도메인 패키지로 신설한다.
  1. `HomePinnedContent` Entity: `targetType`(`HomeTargetType`: `BOARD`/`PROGRAM`), `targetId`, `sortOrder`, `isVisible`, `BaseEntity` 상속(`createdAt`/`updatedAt`). ERD.md no-FK 원칙에 따라 `targetId`는 `@ManyToOne`이 아닌 plain 컬럼이며, Board/Program Entity에는 이 기능을 위한 필드를 추가하지 않는다.
  2. `V11__create_home_pinned_content_table.sql`: `home_pinned_content` 테이블 신규 생성. `UNIQUE(target_type, target_id)` 제약으로 중복 고정을 DB 레벨에서 방지(서비스 계층 사전 검증과 2단계 방어). 기존 `board`/`program` 테이블은 ALTER하지 않는다.
  3. `HomePinnedContentService.create()`: 생성 시 `boardRepository.findByIdAndIsPublicTrue`/`programRepository.findByIdAndIsPublicTrue`(기존 메서드 재사용)로 대상이 존재하고 공개 상태인지 검증하고, `existsByTargetTypeAndTargetId`로 중복을 사전 검증한다. `visible` 미지정 시 기본값 `true`로 애플리케이션 레벨에서 결정한다(Menu/Banner의 기본값 `false`와 다름 - 고정 추가는 곧 노출을 의도하는 관리자 행위이므로). 동시 요청으로 DB `UNIQUE` 제약이 실제로 위반되는 race condition은 `create()` 범위에 한정해 `DataIntegrityViolationException`을 잡아 신규 `ErrorCode.HOME_PINNED_CONTENT_DUPLICATE`(409)로 변환한다(`GlobalExceptionHandler`에 범용 매핑을 추가하지 않음 - 다른 도메인의 무결성 오류까지 중복으로 오판정되는 것을 방지).
  4. `HomePinnedContentService.getAdminList()`: `HomePinnedContent` 전체를 `sortOrder ASC, id ASC`로 조회한 뒤 `targetType`별로 그룹핑해 `BoardRepository.findAllById`/`ProgramRepository.findAllById`(기존 `CrudRepository` 표준 메서드, 신규 Repository 메서드 추가 없음)로 배치 조회한다 - 핀 개수와 무관하게 pin 조회 1회 + 타입당 배치 조회 최대 1회(현재 최대 2)로 N+1을 방지한다. 원본 존재 여부와 `isPublic` 값으로 `PUBLIC`/`PRIVATE`/`DELETED`(원본 없음) 상태를 계산해 응답에 포함한다(DB persisted 컬럼 아님, 응답 조립 시점의 계산값).
  5. Admin API(`/api/admin/home-pinned-contents`): `GET`(목록), `POST`(생성, 대상 무효 시 `INVALID_INPUT_VALUE` 400, 중복 시 `HOME_PINNED_CONTENT_DUPLICATE` 409), `PATCH .../order`, `PATCH .../visibility`, `DELETE`. Menu의 `AdminMenuController`와 동일한 패턴(`ApiResponse`, `@Valid`). 전체 수정 `PUT`은 두지 않는다(대상 자체를 바꾸는 개념이 아니므로 해제 후 재고정이 자연스러움).
  6. Admin View(`GET /admin/home-pinned-contents`, `AdminHomePinnedContentViewController`): 별도 등록/수정 폼(`/new`, `/{id}/edit`) 없이 목록 화면(`admin/homepinned/list.html`) 하나에서 현재 고정 목록(타입/제목/순서/노출여부/원본상태/원본 이동 링크/관리) 조회와 신규 고정 추가(타입 선택 → keyword로 기존 `GET /api/admin/boards`/`GET /api/admin/programs` 검색 → 공개 콘텐츠만 선택 가능 → sortOrder 지정 → 추가)를 모두 처리한다. 신규 검색 API는 만들지 않는다. `admin/layout/default.html`에 Bootstrap JS 번들이 로드되어 있지 않으므로 Bootstrap modal 대신 순수 JS로 토글하는 inline 패널로 구현한다. `admin/layout/sidebar.html`에 "메인 고정 콘텐츠 관리" 링크를 추가한다.
  7. 원본 상태 표시: `sourceUrl`은 공개 상세 URL(`/boards/{id}`, `/programs/{id}`)이 아니라 관리자 수정 화면(`/admin/boards/{id}/edit`, `/admin/programs/{id}/edit`)을 가리킨다 - 공개 상세는 `findByIdAndIsPublicTrue` 기반이라 `PRIVATE` 상태에서는 404가 나서 관리자가 확인할 수 없기 때문(실제 코드 확인 결과에 따른 결정, `DELETED`면 `null`).
- DoD:
  - `BOARD`/`PROGRAM` 대상 고정 생성·조회·순서변경·노출변경·해제가 정상 동작한다.
  - 존재하지 않거나 비공개인 대상으로는 생성할 수 없다(`INVALID_INPUT_VALUE` 400).
  - 동일 `(targetType, targetId)` 중복 생성은 서비스 사전검증과 DB `UNIQUE` 제약 양쪽에서 방어되며 `HOME_PINNED_CONTENT_DUPLICATE`(409)를 반환한다(DB 제약 자체의 존재도 별도 테스트로 검증).
  - `visible` 미지정 시 `true`로 저장된다.
  - 고정 이후 원본이 비공개로 전환되거나 삭제되어도 고정 레코드는 유지되고, 관리자 목록에서 `PRIVATE`/`DELETED`로 정확히 표시되며 500/404가 발생하지 않는다.
  - 정렬은 항상 `sortOrder ASC, id ASC`.
  - 개수 하드 리밋이 어디에도 없다.
  - `home/controller/HomeController.java`, `home/index.html`, 공개 CSS, Board/Program Entity/Service/Controller/관리자 form, Menu/Banner/Popup 기능, Header/Footer/nav JS 무변경.
  - Java 신규 테스트(Repository UNIQUE 제약, Admin Controller, Admin ViewController) + 기존 전체 테스트 무회귀, `./gradlew build` 통과.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T38B. 메인 고정 콘텐츠 공개 Home 렌더링

- 의존성: P13-T38A
- 산출물: `pinned/repository/HomePinnedContentRepository.java`, `pinned/dto/HomePinnedContentPublicResponse.java`(신규), `pinned/service/HomePinnedContentService.java`, `board/repository/BoardRepository.java`, `program/repository/ProgramRepository.java`, `home/controller/HomeController.java`, `templates/home/index.html`, `src/test/java/com/monicalab/home/controller/HomeControllerTest.java`, `frontend-tests/visual-regression.spec.js`, `docs/PRD.md`, `docs/FEATURES.md`, `docs/ARCHITECTURE.md`, `docs/TASK.md`
- 작업 내용: P13-T38A가 구축한 `HomePinnedContent`/`HomePinnedContentService`를 이용해 공개 메인 화면 상단에 고정 콘텐츠("주요 소식")를 노출한다. 신규 공개 JSON API는 만들지 않는다(Menu의 `getPublicMenuTree()`와 동일하게 `HomeController`가 model attribute로만 노출).
  1. `HomePinnedContentRepository.findByIsVisibleTrue(Sort)`(신규, `BannerRepository.findByIsVisibleTrue(Sort)`와 동일 패턴), `BoardRepository.findAllByIdInAndIsPublicTrue(Collection<Long>)`/`ProgramRepository.findAllByIdInAndIsPublicTrue(Collection<Long>)`(신규, 기존 `findByIdAndIsPublicTrue(Long)` 단건 패턴의 배치 버전). 전부 QueryDSL 없이 단순 Spring Data 파생 쿼리다 - `CODING_RULES.md`의 "검색은 QueryDSL 사용" 규칙은 `*RepositoryCustom.search()`(키워드/조건 검색)에 해당하며, 이 배치 조회는 단순 predicate 조회라 대상이 아니다.
  2. `HomePinnedContentService.getPublicList()`(신규): `getAdminList()`와 동일한 구조로 pin 조회 1회 + `targetType`별 그룹핑 + BOARD/PROGRAM 배치 조회 각 0~1회로 처리한다(핀 개수와 무관하게 최대 3 query, N+1 없음, 루프 내부 repository 호출 없음). `isVisible=true`인 pin만 후보로 삼고, 배치 조회 결과 Map에 없는(원본이 비공개이거나 물리 삭제된) pin은 예외 없이 조용히 제외한다. `@Transactional(readOnly = true)`이며 DB를 변경하지 않는다. `getAdminList()`는 이 Task에서 리팩토링하지 않는다.
  3. `HomePinnedContentPublicResponse`(신규 record): `pinnedId`, `targetType`, `targetId`, `title`, `thumbnail`, `href`. `content` 전체는 포함하지 않는다. `href`는 서비스가 `targetType`에 따라 `/boards/{id}` 또는 `/programs/{id}`로 미리 조립해 담고, 템플릿은 분기 없이 그대로 출력한다.
  4. `HomeController`: `HomePinnedContentService.getPublicList()` 결과를 `pinnedContents` model attribute로 추가한다.
  5. `home/index.html`: `#popups` 바로 다음, `#latest-programs` 이전에 `<section id="home-pinned" class="section" th:if="${pinnedContents != null and !pinnedContents.isEmpty()}">`를 추가한다(제목 "주요 소식"). `pinnedContents`가 빈 리스트면 섹션 자체가 DOM에 존재하지 않는다(다른 섹션과 달리 `.empty-state` 문구를 두지 않음). 마크업은 신규 CSS 없이 기존 `ul.gallery-grid`/`li.gallery-card`/`a.gallery-card__link`/`.gallery-card__thumb(-placeholder)`/`.gallery-card__title`을 그대로 재사용한다. BOARD/PROGRAM 타입 뱃지는 두지 않는다(공개 화면에서는 "주요 소식"이라는 단일 개념으로만 표시).
  6. `HomeControllerTest`: 다른 테스트 클래스가 남긴 pin 데이터로부터 격리되도록 `@BeforeEach`에 `homePinnedContentRepository.deleteAll()`을 추가하고, visible/hidden, public/private/deleted BOARD·PROGRAM 혼합, sortOrder ASC + id ASC 동률 처리, 0건/전부 invalid 시 미렌더링, thumbnail/placeholder, DOM 순서(`#popups` → `#home-pinned` → `#latest-programs`) 케이스를 추가한다. 별도 `HomePinnedContentServiceTest`는 만들지 않는다(다른 도메인의 공개 조회 로직도 전부 `HomeControllerTest`에서 검증되는 기존 관례를 따름).
  7. `visual-regression.spec.js`: 기존 "강의 후기" 위치 검증과 동일한 패턴으로, "주요 소식" 섹션이 "최신 프로그램" 섹션보다 위에 위치하는지 확인하는 케이스 1건을 추가한다. 신규 JS를 추가하지 않으므로 `public-console-errors.spec.js`는 수정하지 않는다(기존 `/` 검증이 그대로 커버).
- DoD:
  - `isVisible=true`이고 원본(BOARD/PROGRAM)이 실제로 존재하며 `isPublic=true`인 pin만 공개 화면에 노출된다.
  - `isVisible=false`인 pin, 원본이 비공개이거나 물리 삭제된 pin은 공개 화면에서 조용히 제외되며, 하나의 잘못된 pin 때문에 나머지 정상 pin이나 홈 화면 전체가 깨지지 않는다.
  - 정렬은 항상 `sortOrder ASC, id ASC`이며 BOARD/PROGRAM이 섞여도 순서가 유지된다.
  - 노출 개수 제한이 어디에도 없다.
  - pin이 0건이거나 유효한 pin이 0건이면 `#home-pinned` 섹션 자체가 렌더링되지 않는다(빈 상태 문구 없음).
  - `#home-pinned`는 `#popups` 바로 다음, `#latest-programs` 이전에 위치한다.
  - BOARD 카드는 `/boards/{id}`, PROGRAM 카드는 `/programs/{id}`로 이동한다.
  - thumbnail이 없으면 기존 `.gallery-card__thumb-placeholder`가 표시된다.
  - 신규 DB migration, 신규 공개 JSON API, 새로운 `HomeTargetType` 값, drag-and-drop, 관리자 UI 대규모 변경이 없다.
  - `home/controller/HomeController.java` 외 Menu/Banner/Popup/Header/Footer/nav JS, T38A 관리자 기능(`AdminHomePinnedContentController`/`AdminHomePinnedContentViewController`/관리자 template) 무변경.
  - Java 신규/수정 테스트 + 기존 전체 테스트 무회귀, `./gradlew build` 통과.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T38C. 메인 고정 콘텐츠 후보 선택 UX 개선

- 의존성: P13-T38B
- 산출물: `templates/admin/homepinned/list.html`, `src/test/js/admin/homepinned-admin-view.test.js`(신규), `docs/TASK.md`, `docs/FEATURES.md`
- 작업 내용: T38A의 "새 고정 추가" 후보 검색 UX만 개선한다. 조사 결과 `GET /api/admin/boards`(`boardType`), `GET /api/admin/programs`(`programType`)는 이미 소분류 필터를 지원하고(QueryDSL `BoardRepositoryImpl`/`ProgramRepositoryImpl` 기존 구현), `admin/board/list.html`/`admin/program/list.html`에는 이미 select 변경 즉시 재조회 패턴(P13-T31)이 구현돼 있으므로, 이번 Task는 그 패턴을 `admin/homepinned/list.html`에 재사용하는 순수 프론트엔드 작업이다. Backend(Controller/Service/DTO/Repository/QueryDSL/Entity/migration)는 전혀 변경하지 않는다.
  1. `#searchTargetType` select 옆에 `#searchSubtype` select를 추가한다. `SUBTYPE_OPTIONS` 객체로 targetType별 옵션을 정의하고 `populateSubtypeOptions()`가 `searchState.targetType`에 맞춰 `#searchSubtype`의 `<option>`을 매번 재구성한다.
     - BOARD: 전체/`NOTICE`(공지사항)/`GALLERY`(갤러리)/`ARCHIVE`(자료실)/`REVIEW`(강의 후기)
     - PROGRAM: 전체/`COURSE`(정규 강좌)/`SPECIAL`(특강) — COURSE 라벨은 공개 Menu/문서의 "수강 프로그램"이 아니라 같은 관리자 CMS 화면인 `admin/program/list.html`/`admin/program/form.html`과 동일하게 "정규 강좌"를 쓴다(관리자 화면 간 용어 일관성 우선, 사용자 결정).
  2. `runSearch()`가 DOM의 `#searchKeyword` 값을 매번 직접 읽던 기존 방식을 제거하고, `searchState = {targetType, subtype, keyword}` committed-state 객체 기준으로 요청을 만들도록 리팩토링한다(`admin/board/list.html`/`admin/program/list.html`의 `state` 패턴과 동일한 설계).
  3. `#searchTargetType`의 `change` 리스너: `searchState.targetType` 갱신 + `searchState.subtype = ''`(초기화) + `populateSubtypeOptions()` + `runSearch()`. `searchState.keyword`는 다시 읽지 않는다.
  4. `#searchSubtype`의 `change` 리스너: `searchState.subtype` 갱신 + `runSearch()`. `searchState.keyword`/`searchState.targetType`은 건드리지 않는다.
  5. 기존 `searchForm`의 `submit`(검색 버튼/Enter) 핸들러만 `searchState.keyword`를 커밋한다(기존 계약 유지, `targetType`/`subtype`도 이 시점에 함께 재확인해 커밋).
  6. `buildSearchParams()`: `searchState.subtype`이 있을 때만 `searchState.targetType === 'BOARD'`면 `boardType=`, 아니면 `programType=`으로 정확히 하나의 파라미터만 실어 보낸다(신규 `subtype` 파라미터 없음, BOARD/PROGRAM 파라미터가 서로 섞이지 않음을 코드 구조로 보장). `size=20`은 기존과 동일하게 유지, pagination은 추가하지 않는다(현재 이 검색 패널에 page state 자체가 없음).
  7. `src/test/js/admin/homepinned-admin-view.test.js`(신규): `board-admin-view.test.js`/`program-admin-view.test.js`와 동일한 `node --test` 문자열 검증 스타일로 `searchState` 기본값, `SUBTYPE_OPTIONS` 매핑, `populateSubtypeOptions()`/`buildSearchParams()`/`runSearch()` 구조, 3개 이벤트 핸들러(targetType change/subtype change/submit)의 keyword 비접근·재구성·재조회 계약을 검증한다.
- DoD:
  - targetType 변경 시 검색 버튼 없이 즉시 해당 유형의 후보 목록이 갱신되고, subtype이 "전체"로 초기화된다.
  - subtype 변경 시 검색 버튼 없이 즉시 후보 목록이 갱신된다.
  - 검색 버튼/Enter로 확정하지 않은 keyword 입력은 targetType/subtype 변경으로 검색 조건에 반영되지 않는다. 마지막으로 확정된 keyword는 targetType/subtype 변경 후에도 유지된다.
  - BOARD 요청에는 `programType`이, PROGRAM 요청에는 `boardType`이 절대 포함되지 않는다.
  - subtype이 "전체"면 `boardType`/`programType` 파라미터 자체를 보내지 않는다.
  - 신규 backend API/파라미터, DB migration, ERD 변경이 없다. `AdminBoardController`/`AdminProgramController`/`BoardService`/`ProgramService`/Repository/QueryDSL/Entity/`HomePinnedContentService`/DTO 전부 무변경.
  - 고정 콘텐츠 생성/수정/삭제 계약, 중복 방지, `sortOrder`/`visible`, P13-T38B 공개 조회·UI는 무변경.
  - `homepinned-admin-view.test.js` 전체 통과, 기존 `board-admin-view.test.js`/`program-admin-view.test.js` 무회귀, Java 전체 테스트/`./gradlew build` 통과.
  - `docker-compose.local-test.yml`(untracked 유지).

---

### P13-T39. CKEditor 이미지 alt/figcaption sanitizer 보완

- 의존성: P13-T38C
- 산출물: `common/util/HtmlSanitizer.java`, `src/test/java/com/monicalab/common/util/HtmlSanitizerTest.java`, `docs/TASK.md`, `docs/FEATURES.md`
- 작업 내용: P13 전체 미완료 작업 전수조사에서 확인된 CKEditor sanitizer 후속 항목 2건을 마무리한다. P13-T23 DoD(`docs/TASK.md`)에 "이미지 caption(`figcaption` 텍스트 leak)과 `img[alt]` 미보존은 발견사항으로만 기록하고 이번 DoD에 포함하지 않는다"로 남아 있던 항목이 대상이다. 실제 로컬 develop 서버에 관리자로 로그인해 프로젝트가 로딩하는 바로 그 CDN 빌드(CKEditor 5 41.4.2 classic)로 `ClassicEditor.create(...)` 후 `editor.getData()`를 헤드리스로 직접 확인한 결과, `ImageTextAlternative`(대체 텍스트) 버튼은 `<img alt="...">`를, `ImageCaption`(캡션 넣기/빼기) 버튼은 `<figure><img>...<figcaption>텍스트</figcaption></figure>`를 실제로 생성하지만, 기존 `HtmlSanitizer`의 `Safelist`가 `img`에 `alt`를 허용하지 않고 `figcaption` 태그 자체를 허용 목록에 두지 않아 각각 속성이 소실되거나(alt) 태그만 사라지고 내부 텍스트가 `<figure>` 바로 아래 벌거벗은 텍스트로 leak되는(figcaption) 문제를 재현 확인했다.
  1. `SAFELIST`에 `img`의 허용 속성으로 `alt`를 추가하고, 허용 태그에 `figcaption`을 추가한다(속성은 추가하지 않음 — style/class/id/이벤트 핸들러 전부 기본 차단 유지). `figure`/`img`/`a`의 기존 허용 속성·프로토콜·상대경로 제한(`INTERNAL_IMAGE_SRC`)·`figure` class 토큰 화이트리스트(`ALLOWED_FIGURE_CLASS_TOKENS`)는 전혀 변경하지 않는다. `style` 속성은 어떤 태그에도 추가하지 않는다(Resize 관련 `width`/`style`/`image_resized`는 별도 P13-T40 범위).
  2. `figcaption`을 새로 허용하자 jsoup 기본 출력 설정이 이를 block 태그로 취급해 줄바꿈/들여쓰기를 끼워 넣는 부작용을 헤드리스 테스트로 발견했다. `Jsoup.clean()` 최초 호출과 두 커스텀 후처리 pass(`restrictRelativeImageSources`/`restrictFigureClasses`)의 직렬화 지점 3곳 전부에 `Document.OutputSettings().prettyPrint(false)`를 적용해, CKEditor가 실제로 만드는 compact 출력을 그대로 보존한다(다른 허용 태그의 기존 byte 단위 출력에는 영향 없음).
  3. `alt` 값은 jsoup의 attribute 직렬화(항상 `&`/`"` 이스케이프, HTML5 스펙상 `<`/`>` 이스케이프는 불필요)에 그대로 맡긴다 — 별도 이스케이프 로직을 추가하지 않는다. 빈 `alt=""`는 장식용 이미지를 나타내는 유효한 접근성 값이므로 속성 자체를 제거하거나 임의 텍스트로 채우지 않고 그대로 보존한다. alt를 자동 생성하는 로직은 추가하지 않는다.
  4. 이미지 Resize(`ImageResize`/`ImageResizeEditing`/`ImageResizeHandles`/`ImageResizeButtons`, `resizeOptions`/`resizeUnit`, 25/50/75/100 preset)는 이번 Task에 포함하지 않는다. CKEditor CDN URL/버전 변경, 자체 호스팅 전환, npm 설치도 하지 않는다 — 이는 별도 **P13-T40(CKEditor 이미지 Resize 및 자체 호스팅 전환)**에서 처리한다.
  5. 기존에 이미 저장되어 sanitizer를 통과하면서 alt/figcaption이 소실된 Page/Board/Program/Popup 콘텐츠는 이 Task가 자동으로 복구하지 않는다(DB migration/일괄 rewrite 없음) — 이후 관리자가 해당 콘텐츠를 다시 저장할 때부터 새 정책이 적용된다.
  6. 공개 화면 CSS는 변경하지 않는다 — `figcaption`은 별도 스타일 없이도 브라우저 기본 렌더링(plain block)으로 이미지 아래에 정상적으로 표시되며, 기존 `.ckeditor-content`/`.popup-modal__body`의 `.image`/float 규칙과 충돌하지 않음을 확인했다.
- DoD:
  - `HtmlSanitizerTest`: `img[alt]`(영문/한글/빈 문자열) 보존, `alt` 값의 특수문자(`&`,`<`,`>`)가 attribute 경계를 벗어나지 않고 하나의 값으로 유지됨(재파싱한 DOM 기준 검증), `alt` 값 자체가 `<script>...</script>` 형태여도 실제 `<script>` 요소가 되지 않고 `img` 하나로만 남음, attribute 탈출을 시도하는 `alt` 입력도 새 attribute(`onerror` 등)가 생성되지 않음, `figcaption` 태그+텍스트(영문/한글) 보존, `figcaption`의 `style`/`class`/`id`/이벤트 핸들러 전부 제거, `figcaption` 내부 `<script>`가 실행 가능한 형태로 보존되지 않음 — 전부 통과.
  - 기존 `img src` 허용/차단 정책(http/https/`/api/files/{id}`/프로토콜 상대/기타 상대경로/데이터 스킴), `figure` class 화이트리스트, `a href` 정책, `<script>`/이벤트 핸들러/`javascript:` 제거 등 기존 `HtmlSanitizerTest` 전체가 무회귀 통과한다.
  - Resize 관련 plugin/설정/CDN/버전/vendor 파일이 어디에도 추가되지 않는다(코드 리뷰로 확인).
  - DB migration, 기존 콘텐츠 일괄 수정, CKEditor JS config(`ckeditor-config.js`)/업로드 어댑터/`admin/{page,board,program,popup}/form.html`/공개 CSS(`home.css`) 무변경.
  - `./gradlew test`/`./gradlew build` 전체 통과.
  - `docker-compose.local-test.yml`(untracked 유지).
- 후속: **P13-T40(CKEditor 이미지 Resize 및 자체 호스팅 전환)**, **P2-2(Resize 미적용 이미지의 데스크톱 기본 최대 폭 결정)**로 이어진다(둘 다 이번 Task 범위 밖).

---

### P13-T41. Board/Program 대표이미지 역할 분리 및 상세 자동 출력 제거

- 의존성: P13-T39
- 산출물: `templates/home/board/detail.html`, `templates/home/program/detail.html`, `templates/admin/board/form.html`, `templates/admin/program/form.html`, `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `src/test/java/com/monicalab/program/controller/ProgramViewControllerTest.java`, `src/test/js/admin/board-admin-view.test.js`, `src/test/js/admin/program-admin-view.test.js`, `docs/TASK.md`, `docs/FEATURES.md`
- 작업 내용: "대표이미지(썸네일)와 CKEditor 본문 이미지의 역할이 겹쳐 보인다"는 전수조사 결과에 따라 두 역할을 명확히 분리한다. **대표 이미지 = 목록/홈/메인 카드/HomePinnedContent에서 콘텐츠를 대표하는 카드용 메타데이터**, **CKEditor 이미지 = 상세 본문의 실제 콘텐츠 이미지**로 정의하고, 공개 상세 화면에서는 대표 이미지를 CKEditor content와 별도로 자동 출력하지 않는다. 조사 결과 이 자동 출력은 P8 단계 초기 템플릿 스캐폴딩 당시의 부수적 UI 결정이었을 뿐(P5-T4/P6-T4A/P8-T3/P8-T4 어디에도 "상세에 썸네일을 표시하라"는 DoD가 없음), 명시적 요구사항이 아니었다. 실제 develop 데이터 조사(read-only)에서도 Board 4건이 대표이미지+본문이미지가 함께 표시되는 구조적 중복을 이미 겪고 있음을 확인했다.
  1. `home/board/detail.html`/`home/program/detail.html`에서 `<img th:if="${...thumbnail() != null}" ...>` 블록을 제거한다. `Board`/`Program` Entity/DTO/Repository/Service/API/DB/Flyway는 변경하지 않는다 — `thumbnail`은 계속 optional이며 값 자체는 그대로 저장·조회된다.
  2. BoardType(NOTICE/GALLERY/ARCHIVE/REVIEW), ProgramType(COURSE/SPECIAL) 어느 쪽도 예외를 두지 않는다. GALLERY/REVIEW/PROGRAM은 이미 목록·홈 카드에서 대표이미지가 항상 노출되므로 상세 자동 출력을 제거해도 필드가 무의미해지지 않고, NOTICE/ARCHIVE도 HomePinnedContent로 고정하면 언제든 대표이미지가 노출될 수 있어(`HomePinnedContentService`가 BoardType을 제한하지 않음) 완전히 죽은 필드가 되지 않는다.
  3. 다음 기존 사용처는 전혀 건드리지 않고 그대로 유지한다: `home/board/list.html`(GALLERY/REVIEW `#board-grid` 카드), `home/program/list.html`(`#program-list` 썸네일), `home/index.html`(`#latest-programs`/`#latest-reviews`/`#latest-gallery`), `HomePinnedContentService`/`HomePinnedContentPublicResponse`(P13-T38A/B/C 계약).
  4. `admin/board/form.html`: 라벨 "대표 이미지"는 그대로 두고, thumbnail 입력 아래에 `<div class="form-text">목록·메인 카드 등에 표시되는 이미지입니다. 상세 본문에는 자동으로 표시되지 않습니다.</div>`를 추가한다(기존 `admin/menu/form.html`의 `.form-text` 패턴 재사용, 신규 CSS 없음).
  5. `admin/program/form.html`: 기존 라벨 "썸네일 이미지"를 Board와 동일한 "대표 이미지"로 통일하고(미리보기 `alt` 텍스트도 "현재 등록된 대표 이미지"로 동일하게 통일), 동일한 `.form-text` 안내를 추가한다. `id="thumbnailInput"`/`name="thumbnail"` 등 기존 DOM id/JS 연동은 변경하지 않는다.
  6. CKEditor 설정(`ckeditor-config.js`)/업로드 어댑터/CDN URL·버전/P13-T39가 완료한 `alt`·`figcaption` sanitizer 계약은 전혀 건드리지 않는다.
  7. `BoardViewControllerTest`/`ProgramViewControllerTest`의 `detailAppliesLazyLoadingToThumbnailImage()`(상세에 thumbnail이 자동 렌더링됨을 전제로 하던 테스트)를 삭제하고, 다음 두 계약을 각각 검증하는 테스트로 교체한다: (a) 대표이미지가 있어도 상세 컨테이너 직계 자식으로 `<img>`가 렌더링되지 않고 CKEditor content는 정상 렌더링됨, (b) 대표이미지와 본문 이미지가 모두 있을 때 상세 화면에는 본문 이미지 1개만 존재함(대표이미지에 대한 별도 `<img>`가 추가되지 않음). 목록/홈 관련 기존 thumbnail 테스트(GALLERY/REVIEW 그리드, Program 목록, `HomeControllerTest`의 latest-* 등)는 전혀 수정하지 않는다.
  8. `board-admin-view.test.js`/`program-admin-view.test.js`에 라벨 "대표 이미지"와 안내 문구 존재를 검증하는 케이스를 추가한다(Program 쪽은 기존 "썸네일 이미지" 문자열이 더 이상 없음도 함께 확인).
- DoD:
  - Board(NOTICE/GALLERY/ARCHIVE/REVIEW 전 타입)·Program(COURSE/SPECIAL 전 타입) 공개 상세 화면에서 대표이미지가 더 이상 자동으로 렌더링되지 않는다. CKEditor content는 정상 렌더링된다.
  - 대표이미지+본문이미지가 모두 있는 콘텐츠도 상세 화면에는 본문 이미지만 표시된다(중복 노출 해소).
  - `Board`/`Program`의 Entity/DTO/API/DB/Flyway 무변경, `thumbnail` optional 정책 무변경, 목록/홈/HomePinnedContent의 대표이미지 노출(placeholder 포함)과 GALLERY/REVIEW/Program 카드 UI 전부 무회귀.
  - `AdminHomePinnedContentController`/`HomePinnedContentService`/`HomePinnedContentPublicResponse` 무변경, P13-T38A/B/C 계약 무회귀.
  - Board/Program 관리자 폼 모두 라벨이 "대표 이미지"로 통일되고 동일한 역할 안내 문구를 갖는다.
  - CKEditor CDN/버전/설정, P13-T39의 alt/figcaption sanitizer 계약 무변경.
  - 기존 개발 데이터(Board 12건 + Program 2건, thumbnail만 있고 본문 이미지가 없던 콘텐츠)는 자동으로 수정되지 않으며, 이후 상세 화면에서 이미지 없이 표시되는 것을 정상 동작으로 받아들인다(DB migration 없음).
  - `BoardViewControllerTest`/`ProgramViewControllerTest`/`board-admin-view.test.js`/`program-admin-view.test.js` 신규 케이스 통과, 기존 전체 Java/Node 테스트 무회귀, `./gradlew build` 통과.
  - `docker-compose.local-test.yml`(untracked 유지).
- 후속: **P13-T40(CKEditor 이미지 Resize)**, **P2-2(Resize 미적용 이미지의 데스크톱 기본 최대 폭 결정)**, 공개 홈페이지 디자인 고급화 Phase — 전부 이번 Task 범위 밖.

---

### P13-T40. CKEditor 이미지 Resize(25%/50%/75%/원본 preset) 자체 기능 추가

- 의존성: P13-T39, P13-T41
- 산출물: `static/js/admin/ckeditor-resize-plugin.js`(신규), `static/js/admin/ckeditor-config.js`, `templates/admin/{board,program,page,popup}/form.html`, `common/util/HtmlSanitizer.java`, `static/css/home.css`, `src/test/java/com/monicalab/common/util/HtmlSanitizerTest.java`, `src/test/js/admin/ckeditor-config.test.js`, `src/test/js/admin/ckeditor-resize-plugin.test.js`(신규), `docs/TASK.md`
- 작업 내용: "무료로 이미지 크기 조절 기능을 구현할 수 있는가"를 사전 조사한 결과(라이선스 비용 0원/사용량 제한 없음/소스공개 의무 없음 등 필수 조건), CKEditor 5 최신 버전 self-host(licenseKey:'GPL', "Powered by CKEditor" 배지 강제)나 Cloud CDN 무료 플랜(월 1,000 editor loads 제한, 상용 라이선스)은 채택하지 않고, **현재 CKEditor 5 41.4.2 predefined classic build를 그대로 유지**(CDN URL 무변경, npm/bundler 도입 없음, 공식 ImageResize plugin 미사용)한 채 프로젝트 자체 custom plugin으로 25%/50%/75%/원본 preset resize만 구현한다.
  1. `ckeditor-resize-plugin.js`: CKEditor의 `Plugin`/`Command`/`ButtonView` 베이스 클래스를 extends하지 않는(41.4.2 CDN predefined build가 이 클래스들을 전역 노출하지 않아 번들러 없이는 import 불가 - 헤드리스로 실측 확인) 순수 생성자 함수. `editor.model.schema.extend('imageBlock', {allowAttributes:['resizedWidth']})`로 block 이미지에만 attribute를 허용하고(imageInline은 미지원, 1차 범위), downcast에서 `<figure class="image image_resized" style="width:N%;">`로, upcast에서 style width(25/50/75%만 정규식 매칭)로 각각 변환한다. `resizedWidth`는 공식 `ImageResizeEditing`(GitHub v41.4.2 태그 원본 확인, 우리 build엔 없음)이 쓰는 것과 동일한 이름이지만 이는 이름 재사용일 뿐 향후 공식 plugin과의 완전한 호환을 보장하지 않는다(전환 시 별도 재검증 필요). Command architecture는 쓰지 않고 `setResize(editor, value)` 평범한 함수로 값(`'25'|'50'|'75'|null` 외 거부)을 검증 후 `editor.model.change`/`writer.setAttribute`/`removeAttribute`만으로 변경한다(undo/redo는 CKEditor의 기본 model 변경 이력에 자동 편입).
  2. UI는 CKEditor의 native balloon toolbar에 새 버튼을 넣지 않고(41.4.2에서 ButtonView 없이는 불가), 4개 admin 폼 각각에 정적 HTML 버튼 4개(`data-resize-value="25"/"50"/"75"/""`)를 배치하고 `bindResizeControls(editor, container)` 공용 함수 하나로 클릭 바인딩·disabled/active 상태 동기화(selection 변경 + `document.on('change:data')` 양쪽 구독, undo/redo 후에도 정확히 갱신됨을 실측 확인)를 전 폼 공통 처리한다. JS 로직은 4곳에서 전혀 복붙하지 않고 마크업만 반복한다.
  3. `HtmlSanitizer.java`: 기존 Safelist는 `figure`에 `style`을 전혀 허용하지 않는 원칙을 그대로 유지한다. 대신 raw HTML을 Safelist clean 이전에 별도로 파싱해 `<figure>`를 문서 순서대로 순회하며 style이 정확히 `width: (25|50|75)%;`(공백/세미콜론 변형만 허용) 전체 일치일 때만 그 값을 Java `List<String>`(인덱스=문서상 figure 순서)으로 보존하고, 기존 Safelist clean이 끝난 뒤 같은 순서로 다시 그려 넣는다("extract → clean → reinject", 위조 가능한 임시 attribute/class를 전혀 만들지 않음). `<figure>`는 Safelist에 항상 허용된 태그라 어떤 처리 단계에서도 제거·재정렬·중복되지 않으므로 이 인덱스 대응은 취약하지 않다(허용 안 된 형제/조상이 섞여도 순서가 안 깨짐을 회귀 테스트로 직접 증명). `ALLOWED_FIGURE_CLASS_TOKENS`에 `image_resized`를 추가한다.
  4. `home.css`: `.ckeditor-content`/`.popup-modal__body` 양쪽에 `.image.image_resized{max-width:100%}` + `.image.image_resized img{width:100%;height:auto}`를 추가한다. class 조합의 특이도가 기존 `image-style-side` 등 단일 클래스 규칙보다 높아, resize와 정렬 style이 동시에 걸려도 항상 지정한 %가 우선 적용됨을 실측(75% + image-style-side 조합에서 실제 렌더링 폭이 600px로 나와 50% cap에 갇히지 않음)으로 확인했다.
- DoD:
  - CKEditor CDN URL(`https://cdn.ckeditor.com/ckeditor5/41.4.2/classic/ckeditor.js`)이 4개 admin 폼 전부에서 무변경, npm/package.json/vendor 디렉터리 신규 없음, licenseKey/"Powered by CKEditor" 배지 없음.
  - 관리자가 Board/Program/Page/Popup 어디서든 본문 block 이미지를 선택하면 25%/50%/75%/원본 버튼이 활성화되고, 클릭 시 실제 편집 화면 크기가 즉시 바뀌며 현재 크기에 해당하는 버튼만 active 표시된다(실제 로컬 Docker+관리자 로그인 E2E로 확인).
  - undo/redo 시 크기와 버튼 active 상태가 모두 정확히 복원된다(실측 확인: 50%→75%→undo→50%→redo→75%).
  - 저장 시 `<figure class="image image_resized" style="width:50%;">`(canonical, 공백 없음) 형태로 DB에 저장되고, 공개 상세/팝업 화면에 해당 %로 정확히 렌더링되며 desktop(1280px)/mobile(375px) 양쪽에서 container overflow 없이 비율이 유지된다(실측: 두 뷰포트 모두 정확히 지정 %와 일치, horizontal scroll 없음).
  - 원본으로 되돌려 저장하면 최종 DB content에 `style`도 `image_resized` class도 남지 않는다(stale metadata 없음, 실제 재편집→원본 복원→재저장 라운드트립으로 확인).
  - `image-style-side`/`alignLeft`/`alignCenter`/`alignRight` 4개 정렬 style과 resize가 동시에 적용되고, 순서(정렬 후 리사이즈/리사이즈 후 정렬 변경) 무관하게 정상 동작한다(실측 확인).
  - caption(`figcaption`)/`alt`가 resize와 항상 함께 유지된다(P13-T39 계약 무회귀, 실측 확인).
  - `HtmlSanitizerTest` 전체(기존 33개 + 신규 resize 관련 35개, 총 68개) 통과, 악성 style(다른 property 혼입/범위 밖 값/소수/음수/`calc()`/`var()`/`expression()`/중복 선언 등) 전부 style 전체 폐기 확인.
  - `ckeditor-config.test.js`/`ckeditor-resize-plugin.test.js` 등 Node 테스트 전체(272개) 통과, `./gradlew build`/전체 Java 테스트(567개) 통과.
  - Board/Program/Page/Popup 4개 admin CKEditor 사용처 모두 콘솔 에러 없이 정상 초기화되고 기존 toolbar(Bold/Italic/Link/Table/이미지 업로드/캡션/정렬/alt)와 업로드 어댑터가 무회귀로 동작한다.
  - P13-T39(alt/figcaption sanitizer)·P13-T41(대표이미지/본문이미지 역할 분리) 무회귀.
  - DB/Entity/Repository/Service/Controller/API 계약 변경 없음.
  - `docker-compose.local-test.yml`(untracked 유지), 검증용 테스트 콘텐츠/업로드 파일은 검증 후 전량 삭제됨(운영 DB에 잔존 데이터 없음).
- 후속: **P2-2(Resize 미적용 이미지의 데스크톱 기본 최대 폭 결정)**, drag handle 방식의 자유 리사이즈(2차, 필요성 재검토 후 별도 Task), 공개 홈페이지 디자인 고급화 Phase — 전부 이번 Task 범위 밖.

### P13-T42. Playwright E2E 테스트 데이터 자동 cleanup

- 의존성: P11-T0(Playwright 도구 설치), P13-T40
- 산출물: `frontend-tests/support/resource-tracker.js`(신규), `frontend-tests/support/e2e-fixtures.js`(신규), `src/test/js/e2e/resource-tracker.test.js`(신규), `frontend-tests/visual-regression.spec.js`, `frontend-tests/admin-console-errors.spec.js`, `docs/TASK.md`. `public-console-errors.spec.js`/`playwright.config.js`/production Java 코드/DB schema/API는 변경하지 않는다.
- 작업 내용: Playwright E2E(수동 실행, 개발 서버와 DB를 대상으로 함)가 만든 Board/Program/Popup/Banner/Menu/HomePinnedContent/File이 종료 후 남아 개발 DB와 `data/uploads`에 누적되던 문제를 해결한다. 원인은 (1) `admin-console-errors.spec.js`가 Board를 만들고 삭제하지 않음, (2) spec 어디에서도 File을 삭제하지 않아 업로드 파일이 누적됨(실측: 테스트 파일 281건), (3) 일부 UI 생성 테스트가 "최신 목록 1건"을 자기 리소스로 가정하고 삭제함(병렬 실행/사용자의 동시 작업 시 다른 데이터를 지울 위험), (4) T37 `afterAll`이 로그인하지 않은 새 context로 DELETE해서 항상 403이었음, (5) 모든 DELETE의 응답 상태를 검사하지 않아 실패가 묻힘이었다. 설계는 **"테스트가 생성 응답에서 얻은 정확한 ID만 등록하고, 그 ID만 정상 관리자 API로 삭제"**이다. 제목/파일명/시각/최신 N개/ID 범위/orphan 전체로 대상을 찾는 로직은 두지 않는다.
  1. `resource-tracker.js`(Playwright 비의존 순수 로직): `createResourceTracker({ deleteResource, onTrack })` → `track(kind, id)`(kind는 `pinned/board/program/popup/banner/menu/file`만 허용, id는 양의 정수만, 중복 등록은 한 번만, id를 그대로 반환), `remove(kind, id)`(테스트 논리상 중간 삭제가 필요할 때만; 204/404면 등록 해제, 그 외 실패는 예외를 던지되 등록을 유지해 최종 cleanup이 다시 시도), `cleanup()`(pinned → board/program/popup/banner → menu → file 순서, 각 phase는 등록의 역순(LIFO)이라 Menu는 자식 → 부모 순; 204는 성공, 404는 idempotent 성공, 401/403/409/5xx/네트워크 오류/timeout은 실패; 자동 재시도 없음; 하나가 실패해도 나머지를 계속 시도하고 마지막에 kind/id/status를 모아 한 번에 throw), `ids(kind)`.
  2. `e2e-fixtures.js`(Playwright glue): 테스트 스코프 `tracker` fixture는 테스트 종료 후(성공/단언 실패/throw/timeout/beforeEach 실패 모두) `cleanup()`을 실행한다. cleanup은 테스트의 page/context와 독립된 `request.newContext`로 `POST /api/admin/login`(기존 `ADMIN_LOGIN_ID`/`ADMIN_PASSWORD`) 후 `XSRF-TOKEN` 쿠키를 `X-XSRF-TOKEN` 헤더로 보내며, 등록된 리소스가 있을 때만 lazy하게 로그인한다. 등록 시 report annotation(`tracked-resource`, `kind#id`)을 남겨 사후 exact-ID 검증과 수동 정리에 쓸 수 있게 한다(진단용; cleanup 성공 여부는 tracker 자체가 책임진다). `createTracker({ baseURL })`은 fixture를 쓸 수 없는 `beforeAll/afterAll`(T37)에서 같은 구현을 쓰기 위한 팩토리다.
  3. 생성 직후 등록: API 생성은 `tracker.track('board', await createBoard(...))`처럼 반환 즉시 등록하고(등록 전에 별도 단언을 두지 않음), 파일 업로드는 `observeCreate(page, tracker, 'file')`로 파일 선택/`setInputFiles` **전에** 관찰을 걸어 `page.waitForResponse`로 실제 POST 응답의 `data.id`를 등록한다. `observeCreate`는 요청을 가로채지 않고 관찰만 하며(`route.fetch`/`route.fulfill` 미사용), method/pathname/2xx/JSON/`data.id`(양의 정수)를 모두 검증하고 어긋나면 등록하지 않고 예외를 던진다.
  4. 이동을 동반하는 UI 생성(T23/T29 Board, Popup CKEditor Popup)은 폼 저장 직후 `location.href`로 이동해 응답 body가 폐기되므로 `waitForResponse`로는 ID를 읽을 수 없다(실측 재현). 이 3곳에 한해 `observeNavigatingCreate`가 이동 전에 **페이지 안에서** 원래 fetch의 응답 `clone()`으로 `data.id`를 읽어 호출마다 무작위 token이 붙은 `exposeFunction`으로 Node에 전달하고, Node 콜백이 즉시 `tracker.track()`한다. 원래 fetch는 원래 인자·`this`로 정확히 한 번 실행되고 호출자는 원래 promise/response를 그대로 받으며(요청/응답 변경, 재전송, 이동 차단 없음), 대상은 `POST`이면서 pathname이 정확히 `/api/admin/boards`(또는 `/popups`)인 요청뿐이다. 실패 시 최신 목록 조회 같은 fallback 없이 테스트가 실패한다.
  5. `visual-regression.spec.js`: `require('./support/e2e-fixtures')`로 교체하고 모든 생성 지점(create helper 호출 57곳, inline POST, 파일 업로드 8곳, UI 생성 3곳)을 등록한다. tracker가 책임지는 리소스의 기존 DELETE `afterEach`(19개)와 `delete*` helper, 인라인 삭제, T23/T29/Popup의 "최신 목록 1건" ID 탐색(`data.content[0].id`/`data[0].id`), T30B의 DELETE 전용 try/finally를 제거한다(정상 흐름에서 기존 cleanup 204 후 tracker 404가 반복되지 않도록). 서버 리소스와 무관한 Popup localStorage 정리는 유지하고, 테스트 논리상 필요한 `widthFor`의 중간 삭제는 `tracker.remove`로 유지한다. T37은 `createTracker`로 생성 직후 등록하고 `afterAll`에서 로그인된 독립 세션으로 `cleanup()`+`dispose()`한다. `admin-console-errors.spec.js`는 생성 응답 ID를 즉시 등록한다.
- DoD:
  - `admin-console-errors` 실행 후 생성 Board 잔여 0, `visual-regression`이 업로드한 File(DB row/public GET/물리 파일) 잔여 0이며, 테스트가 생성한 리소스는 exact ID로만 삭제된다(title/filename/timestamp/latest-N/ID range/orphan 기반 삭제 없음, DB 직접 DELETE·filesystem 직접 삭제·production cleanup endpoint 없음).
  - T23/T29/Popup의 "최신 목록 1건" ID 탐색이 제거되고, T37의 인증/lifecycle 문제가 해결된다.
  - 일반 단언 실패/throw/beforeEach 실패 후에도 tracked 리소스가 정리되고, cleanup 실패는 kind/id/status와 함께 조용히 무시되지 않고 보고되며 원래 테스트 실패를 가리지 않는다.
  - `resource-tracker.test.js`(tracker 순서/정책/집계/`remove`, 브라우저 측 fetch 관찰자의 의미 보존) 및 Node 전체 테스트 통과. `visual-regression`/`admin-console-errors`/`public-console-errors` 전체 통과, 실행 후 tracked exact ID와 실행 중 소비된 AUTO_INCREMENT 구간 전체가 존재하지 않고 DB 개수/물리 파일 수가 실행 전과 같다.
  - 기존 테스트 assertion의 의미 무변경, 기존 실제 콘텐츠 무영향, `docker-compose.local-test.yml` 미변경.
- 한계: `kill -9`, 머신/Docker 강제 종료처럼 teardown 자체가 실행되지 않는 경우와, 생성 요청 직후 등록 전에 프로세스가 중단되는 경우의 누수는 보장 범위 밖이다. 이를 위해 journal/stale-resource sweep 같은 자동 삭제는 두지 않는다(오삭제 위험).

---

## Phase 14. 공개 홈페이지 디자인 고급화

Phase 14는 **공개 홈페이지의 시각 디자인만** 개선한다. 기능/정보구조/URL/데이터 흐름/JS 동작은 Phase 13까지의 결정을 그대로 유지한다. 관리자 화면(관리자 CSS/템플릿/CKEditor 설정 포함)은 범위 밖이다. 이 Phase의 근거는 P14-T0 직전에 수행한 READ-ONLY 디자인/UX 감사(공개 template 12개, `home.css` 1197줄, JS 4개, Playwright `visual-regression.spec.js` 기준)다.

### P14 공통 계약 (P14-T1~T7 전체에 적용)

#### A. 확정 사항 (P14-T0에서 확정, 이후 Task가 임의로 바꾸지 않는다)

1. **디자인 방향**: "Editorial / Academic 기반의 Modern Education". 고급스러움은 장식의 양이 아니라 typography, spacing, alignment, visual hierarchy, 절제된 색, 일관된 component language로 만든다.
   - 피할 것: 과도한 gradient, glassmorphism, 과도한 shadow, 지나치게 둥근 card, pill 남용, animation 남용, SaaS dashboard/스타트업 landing page 스타일, dark+gold 위주의 luxury 스타일, 콘텐츠보다 장식이 먼저 보이는 디자인.
2. **Typography**: 단일 sans 계열. Serif 혼용은 이번 Phase에서 사용하지 않는다. weight는 가능한 한 400/600/700으로 제한한다. display/h1/h2/h3/body/meta 계층을 token화한다. 방향값(본문 1rem, 본문 line-height 약 1.7, heading line-height 약 1.3)은 목표이며 실제 값은 T1에서 header width 검증 후 확정한다.
3. **Color 방향**: Deep Navy + Warm Neutral(background=warm off-white 또는 white, surface=warm neutral, primary=deep navy, text=near-black, muted=충분한 contrast의 neutral, border=warm hairline). accent는 디자인상 필요성이 확인될 때만 제한적으로 추가한다. 감사 단계에서 제안된 hex 값은 **확정값이 아니다**.
4. **Shape / Surface**: 전체적으로 radius를 낮춘다. 단 `--radius` 하나의 값을 바꿔 popup/hero/pagination/dropdown 등을 한꺼번에 바꾸지 않는다(현재 `--radius`는 hero, popup, empty-state, dropdown, program-card, gallery thumb, pagination 등이 공유한다). T1/T2에서 사용처를 다시 조사하고 필요한 최소한의 component-specific radius token으로 분리한다. 일반 content card는 border+spacing 중심, shadow는 dropdown/popup처럼 elevation이 실제 필요한 요소에만, pill은 상태 표시처럼 의미가 있을 때만 쓴다.
5. **Layout**: 콘텐츠 순서, URL, Controller/data flow 유지. Bootstrap 5.3.3 CDN은 제거하지 않고 새 CSS framework도 추가하지 않는다. mobile 좌우 padding 약 1rem, detail 읽기 폭 약 760~820px, section vertical rhythm 강화를 목표로 한다. 기존 **900px navigation breakpoint는 P13 결정이므로 변경하지 않는다.** 새 breakpoint를 불필요하게 추가하지 않는다.
6. **Home**: 콘텐츠 순서(Hero/banner → 주요 소식 → 최신 프로그램 → 강의 후기 → 공지사항 → 갤러리)와 Controller/data flow를 유지한다. P13-T17에서 제거한 소개(greeting) 영역과 프로그램 shortcut CTA, 신규 브랜드 메시지 section/신청 CTA를 추가하지 않는다. Hero는 관리자 배너 중심 구조를 유지하고 배너 이미지 위에 overlay text를 추가하지 않는다. 배너가 없을 때의 empty state는 시각만 개선할 수 있다. section 사이에 white/warm surface를 제한적으로 써서 리듬을 만들 수 있으나 과도한 zebra stripe로 보이면 안 된다.
7. **Section heading**: P13-T12의 제목 링크 구조를 유지한다. "영문 eyebrow + 한글 제목 + 더보기" 패턴을 강제하지 않으며, P13-T12에서 제거한 "전체보기" 텍스트를 디자인 이유만으로 복원하지 않는다. 위계는 typography/spacing/hairline·accent/alignment 중 최소한의 요소로 만든다. 영문 eyebrow는 브랜드 콘텐츠상 자연스러운 경우가 아니면 쓰지 않는다.
8. **Image / Card**: 현재 콘텐츠 성격별 비율을 유지한다(Program 4:3, Gallery/Review/주요 소식 gallery 스타일 1:1). 전체를 한 비율로 통일하지 않는다. `object-fit`과 placeholder 동작을 유지한다. card는 과도한 shadow/radius 없이 border/spacing/typography 중심으로 하고, hover에서 큰 이동/scale animation을 쓰지 않는다.
9. **사용자 노출 enum**: 공개 UI에 직접 보이는 raw enum(예: `COURSE`, `OPEN`, `NOTICE`)은 Phase 14에서 사람이 읽을 수 있는 한글 표시명으로 정리한다.
   - **domain enum, DB 값, API/internal value는 변경하지 않는다. 표시명은 presentation layer에서만 적용한다.**
   - 기존 테스트가 raw enum 문자열을 고정하고 있으면 새 사용자 표시 계약에 맞게 기대값을 **갱신**할 수 있다. 테스트 삭제/assertion 약화는 금지한다.
   - 감사 시점 조사 결과: **raw enum을 직접 기대하는 테스트 2곳** — `BoardViewControllerTest:322`(`.board-list__type` 텍스트가 `"NOTICE"`), `ProgramViewControllerTest:217`(`#program-list .program-list__meta`가 `"COURSE"`, `"OPEN"` 포함). **기존 한글 표시 계약 선례 1곳** — `HomeControllerTest:262~265`(홈 `#latest-programs .program-card__status`가 "모집중/모집마감"을 표시하고 `OPEN/CLOSED`가 노출되지 않음을 검증; raw enum 의존 테스트가 아니라 이미 한글 표시를 검증하는 선례). Playwright에는 표시 텍스트로서의 enum 단언이 발견되지 않았으나 `.badge` locator 사용 3곳은 T4A/T4B 착수 전에 재확인한다.
10. **Detail / Static**: 읽기 경험을 개선한다(읽기 폭 약 760~820px, page title/metadata 위계, paragraph spacing, CKEditor content typography). semantic heading은 가능하면 h1을 쓰되 h2→h1 변경 전에 Java/Playwright test 의존성을 재확인한다(감사 시점에 `h1`/`h2` selector 의존은 발견되지 않음). CKEditor의 image resize, image alignment, alt, figcaption, overflow protection, link policy, 대표 이미지/본문 이미지 분리(P13-T41)를 훼손하지 않는다. 새 typography CSS는 `.ckeditor-content`의 텍스트 요소에 한정하며 기존 image 관련 CSS(`home.css` 하단 `.ckeditor-content .image*` 블록)를 광범위 selector로 덮어쓰지 않는다.
11. **Accessibility**: 기존 focus-visible/aria-current/keyboard navigation/Escape close/focusout close/popup focus trap/prefers-reduced-motion을 유지한다. Phase 14에 skip link, heading hierarchy 개선, text contrast 검증, interactive target 크기 점검, mobile keyboard/focus QA를 포함한다. 색만으로 상태를 전달하지 않는다.
12. **Footer**: 현재 사업자 정보와 링크 내용은 그대로 유지하고 정보 계층만 개선한다(brand/navigation과 business information 그룹, desktop 2단 가능, mobile 1단). 사업자 정보가 축약/숨김되면 안 된다.
13. **CSS 구조**: **Phase 14 동안 `home.css`를 분할하지 않는다**(디자인 변경과 CSS architecture refactoring을 동시에 하지 않아 회귀 원인을 분리한다). T1에서 구조가 구현을 심각하게 방해함이 확인된 경우에만 분할 필요 이유/예상 파일 구조/template 영향/회귀 위험을 보고하고 **별도 승인**을 받는다(승인 없이 분할 금지). Phase 완료 후 필요하면 별도 CSS refactoring Task를 만든다. `!important`를 추가하지 않고 기존 specificity를 먼저 이해한 뒤 수정한다.
14. **Bootstrap**: Bootstrap 5.3.3 CDN은 유지한다(제거/대체는 범위 밖). 공개 화면에서 눈에 띄는 `list-group`, `badge`, `btn`, `form-control` 기본 스타일은 필요한 범위에서 프로젝트 디자인에 맞게 override할 수 있다(공개 pagination은 Bootstrap이 아니라 커스텀 `.pagination-bar`다). DOM/기능 계약을 깨지 않는다.
15. **DOM 안정성 원칙**: "디자인을 위해 안정된 DOM을 불필요하게 변경하지 않는다." 기존 selector의 제거/rename은 금지하고, DOM 재구성이 꼭 필요하면 기존 selector를 보존한 wrapper 추가 방식부터 검토한다. 기존 테스트를 디자인에 맞추려고 약화하지 않으며, 테스트가 깨지면 "테스트가 낡은 것인지 vs 실제 회귀인지"를 먼저 판단한다.
16. **범위 제한**: 공개 홈페이지 전용. 관리자 화면 디자인과 관리자 CKEditor 설정은 변경하지 않는다. 새 npm dependency를 추가하지 않는다. 외부 유료 디자인 시스템/유료 asset을 사용하지 않는다.
17. **정정 노트(P13-T4)**: P13-T4에 기록된 "Sub Page 공통 Hero/Breadcrumb 적용"은 **현재 코드(`home/*/list.html`, `detail.html`)에 구현되어 있지 않다**(각 페이지는 `<h2>` 제목으로 시작한다). Phase 14에서 이를 자동으로 복원하지 않고, P14 Task로 추가하지 않으며, 디자인 개선을 이유로 새 breadcrumb/hero를 만들지 않는다. 향후 별도 요구가 생겼을 때만 새 Task로 검토한다.

#### B. 이후 Task에서 결정할 사항 (P14-T0에서 확정하지 않음)

| 항목 | 결정 시점 | 비고 |
|---|---|---|
| 폰트 제공 방식 | P14-T1 계획 | Pretendard는 **우선 후보**일 뿐 사용 방식은 확정하지 않는다. T1에서 원문 기준으로 license(웹 사용, 재배포 조건, self-host 시 notice, CDN 조건, 저장소 포함 시 license 처리)를 확인한 뒤 A. self-host / B. CDN / C. system font 유지를 라이선스·운영 안정성·외부 의존성·초기 로딩·caching·repository 영향·900px navigation 폭 영향 기준으로 비교하고 **하나를 제안하여 승인**받는다. 라이선스/운영상 문제가 있으면 시스템 한글 sans stack이 fallback이어야 한다. |
| color 실제 값 | P14-T1 | WCAG contrast를 계산해 AA 기준을 만족하는 값으로 확정 |
| typography 실제 크기/line-height | P14-T1 | header 폭(900~1440px 한 줄 유지) 검증 후 확정 |
| container max-width | P14-T1 | 1200px/1320px를 T0에서 확정하지 않는다. 실제 렌더링(1440/1024/900/899/768/375)에서 header와 본문의 좌우 정렬선, whitespace, navigation 한 줄 유지, card/grid 밀도, 본문 가독성, horizontal overflow를 기준으로 결정한다. 일반 content와 reading content에 서로 다른 max-width를 쓸 수 있다. |
| component-specific radius token 분리 범위 | P14-T1/T2 | `--radius` 사용처 재조사 후 |
| Home heading(h1) 구조 | P14-T3 계획 | 실제 Home DOM/시각 기준으로 조사 후 제안·승인. 숨겨진 SEO 전용 h1을 억지로 추가하지 않고, Hero/banner 데이터 구조를 바꾸지 않으며, 새 브랜드 메시지를 자동 추가하지 않는다. |
| Home section 배경 wrapper 추가 | P14-T3 (조건부) → **P14-T3A에서 실행** | T3는 DOM 무변경으로 유보(아래 P14-T3 참고). T3A가 승인받아 5개 콘텐츠 section에 내부 `<div class="container">` wrapper를 추가하고 full-width surface를 적용했다(아래 P14-T3A 참고). |
| 공통 list foundation 위치(T4A vs T4B) | P14-T4A 계획 | 실제 코드 조사 후 더 안전한 쪽에 배치 |
| `home.css` 분할 | 기본 "하지 않음" | 위 A-13 조건 충족 시에만 별도 승인 |

### P14 보존 계약 (Phase 14 전체에서 보호할 DOM/JS/Test 계약)

아래 id/class/data 속성은 JS 또는 Playwright/Java 테스트가 의존한다. **임의로 제거/rename하지 않는다.**

| 영역 | 보존 대상 |
|---|---|
| Header/nav | `#site-header`, `#nav-toggle`, `#site-nav`, `.is-open`, `#quick-menu`, `.site-nav__item.has-submenu`, `.site-nav__trigger`, `.site-nav__submenu`, `data-menu-id`(`all` 포함), `#megamenu`, `.site-nav__megamenu*`, `.site-header__brand`, `.is-active`/`.has-active-child`/`aria-current`, `aria-expanded` |
| Hero | `#banners`, `#hero-viewport`, `#hero-controls`, `.hero__slide`(`hidden` 전환), `.hero__indicator`(`.is-active`), `#hero-prev`, `#hero-next`, `#hero-play-pause` |
| Popup | `#popups`, `#popup-overlay`, `.popup-modal`, `data-popup-id`, `.popup-modal__header`, `.popup-modal__title`, `.popup-modal__close`, `.popup-modal__hide-today`, `.popup-modal__body`. **위치(top/left)와 z-index는 `popup-modal.js`가 inline style로 지정하므로 CSS에서 고정하지 않는다.** |
| Home 섹션 | `#index-content`(layout fragment selector), `#home-pinned`, `#latest-programs`, `#latest-reviews`, `#latest-notices`, `#latest-gallery`, `.section-title__link` |
| 목록 | `#board-grid`, `#board-list`, `#program-list`, `#board-type-filter`, `#program-type-filter`, `.filter-nav__link`(`.is-active`), `.board-list__*`, `.program-list__*`, `.gallery-grid`/`.gallery-card*`, `.program-card*` |
| Pagination | `#pagination`, `#prev-page`, `#next-page`, `#page-jump-input`, `#page-jump-submit`, `.pagination-bar*`(`.is-active`) |
| Detail | `.ckeditor-content`, `#attachment-link`, `#apply-link`, "목록으로" 링크(텍스트 기준 selector 사용) |
| Footer | `#site-footer`, `.site-footer__brand`, `.site-footer__nav`, `.site-footer__address`, `.site-footer__phone`, `.site-footer__reg-no`, `.site-footer__site`, `.site-footer__copyright` |

추가로 보호할 검증 계약: 900px 경계(899.98/900) 및 top-level 메뉴 한 줄 유지(900~1440), header 터치 영역 하한(32px), 1024px header font-size 상한(18px), GROUP/LEAF `font-weight` 단언(`toHaveCSS('font-weight')` 16곳), 모든 viewport에서 horizontal overflow 없음, CKEditor image resize/alignment/caption/overflow 규칙, `HomeControllerTest`/`ProgramViewControllerTest`/`BoardViewControllerTest`/`PageViewControllerTest`(A-9의 2곳 갱신 대상 제외) 무변경 통과.

### P14 실제 화면 검증 계약

T1부터는 코드/CSS 검토만으로 디자인 완료를 판단하지 않는다. 현재 프로젝트의 실행 방법(Docker 8088 등)으로 **실제 브라우저 렌더링**을 확인한다.

- 최소 viewport: **1440, 1024, 900, 899, 768, 375** (필요 시 390/430 추가).
- 각 Task 확인 항목: horizontal overflow, navigation wrapping, text clipping, image distortion, section spacing, card alignment, focus state, hover state, mobile stacking, typography hierarchy.
- 기존 Playwright/visual regression infrastructure를 최대한 활용한다. 디자인 변경을 이유로 기존 테스트를 무조건 수정하지 않는다(테스트 갱신은 "낡은 계약"으로 판단된 경우에 한하며 assertion 삭제/약화는 금지).

### Phase 14 Task 구조

`P14-T0 → P14-T1 → P14-T2 → P14-T2A → P14-T2B`, `P14-T0 → P14-T1 → P14-T2 → P14-T3 → P14-T3A → P14-T3B → P14-T3C`, `P14-T1 → P14-T4A → P14-T4B`, `P14-T1,T4B → P14-T5`, `P14-T2B,T3C → P14-T6A/T6B → P14-T7`. 모든 Task의 공통 DoD: `./gradlew build` 통과, Playwright 기존 테스트 무회귀(정당한 계약 갱신 제외), 관리자 화면/DB/Java domain 변경 없음, `docker-compose.local-test.yml`(untracked 유지).

### P14-T0. 공개 홈페이지 디자인 계약 확정
- 의존성: P13-T42, Phase 14 디자인 감사(READ-ONLY) 승인
- 산출물: `docs/TASK.md`(본 Phase 14), `docs/ARCHITECTURE.md`(Frontend 절)
- 작업 내용: 위 Phase 14 공통 계약/보존 계약/검증 계약/Task 구조를 문서로 확정한다. 코드/template/CSS/JS/Java/DB/테스트 변경 없음. `docs/PRD.md`/`docs/FEATURES.md`는 기능 요구사항 변경이 없으므로 수정하지 않는다.
- 변경 금지: Java, template, CSS, JS, tests, DB/Flyway, build/Docker 파일, `docker-compose.local-test.yml`, PRD, FEATURES.
- 위험: 낮음(문서 전용). 계약 문구가 P13 결정과 충돌하지 않도록 해야 한다.
- 검증: `git diff --stat`으로 변경 파일이 `docs/TASK.md`, `docs/ARCHITECTURE.md` 2개뿐임을 확인.
- DoD: 변경 범위가 두 문서로만 한정, Phase 14 확정/미확정 사항 구분, 보존 계약과 raw enum 조사 결과(직접 의존 2곳 + 한글 표시 선례 1곳)와 P13-T4 정정 노트가 기록됨, `./gradlew build`/`test` 결과에 영향 없음.

### P14-T1. Design Foundation
- 의존성: P14-T0
- 산출물(예상): `static/css/home.css`(`:root` token, base typography/surface, container), `home/layout/default.html`(폰트 제공 방식이 요구하는 경우에 한해), 공개 6개 template의 inline `style="margin-top: 40px;"` 정리(클래스/CSS로 대체), (self-host 선택 시) 폰트 파일과 license notice
- 작업 내용: (1) 폰트 제공 방식 A/B/C 비교 후 제안·승인(license 원문 확인 포함, 새 npm dependency 금지), (2) color token을 Deep Navy + Warm Neutral로 재정의하고 WCAG AA contrast 계산으로 값 확정(`#site-header`/`#site-footer`의 하드코딩 `#dee2e6`/`#495057`을 token으로), (3) typography token(display/h1/h2/h3/body/meta, weight 400/600/700)과 line-height, (4) spacing/section rhythm token, (5) container/reading width를 실제 렌더링으로 확정, (6) `--radius` 사용처 재조사 후 component-specific radius token 분리 범위 결정, (7) base surface.
- 변경 금지: header/footer/hero/card 등 component별 규칙 재설계(T2 이후), JS, Java, DOM id/class, 900px breakpoint, `home.css` 분할(별도 승인 없이 금지).
- 위험: 폰트 metric 변화로 900~1440px에서 top-level 메뉴가 wrap될 수 있음(P13-T30D/T35/T36/T37 회귀), `font-weight` 단언 16곳, container 폭 변경이 header와의 정렬을 깨뜨릴 수 있음, `--radius` 공유로 인한 popup/hero/pagination 연쇄 변화.
- 검증: 6개 viewport 실제 렌더링, P13-T30B/C/D·T35·T36·T37 Playwright, `public-console-errors.spec.js`, `./gradlew build`.
- DoD: token 체계가 확정되어 문서화됨, 폰트 방식 승인 및 반영, 모든 대비가 AA 기준을 만족, 900~1440px top-level 메뉴 한 줄 유지, 6개 viewport horizontal overflow 없음, 기존 테스트 무변경 통과, `!important` 미추가.
- 구현 결과(P14-T1):
  - **Font**: Pretendard Variable, jsDelivr CDN, 버전 고정 `v1.3.9`(jsDelivr 패키지 목록의 최신 버전), dynamic subset, 공식 CSS의 `font-display: swap`. URL: `https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css`(HTTP 200 확인, `font-family: 'Pretendard Variable'`). `home/layout/default.html`에 `<link>` 1줄만 추가했고 font binary/npm dependency는 추가하지 않았다. fallback: `"Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", "Noto Sans KR", system-ui, sans-serif`(`--font-sans`). 라이선스: SIL OFL 1.1(CDN 사용에 별도 고지 의무 없음).
  - **Color token**: `--color-bg #ffffff`, `--color-surface #f7f5f0`, `--color-text #1c1f24`, `--color-text-muted #5b6470`, `--color-primary #1f3a5f`, `--color-primary-hover #162b48`(신규), `--color-primary-soft rgba(31,58,95,.08)`, `--color-primary-contrast #ffffff`, `--color-border #e3ded3`. accent 미추가. header/footer의 하드코딩 `#dee2e6`/`#495057`은 token 참조로 교체.
  - **WCAG 대비(계산값)**: text/bg 16.52, muted/bg 6.00, text/surface 15.17, muted/surface 5.50, primary/bg 11.48(= link/bg), primary/surface 10.54, primary-contrast/primary 11.48, primary-contrast/primary-hover 14.25, primary/soft(합성) 9.97 — 모두 AA 4.5:1 이상. `--color-border`는 장식용 hairline(1.34:1)이라 텍스트 기준 대상이 아니며, 입력 경계 대비는 T4A/T6A에서 다룬다.
  - **Typography**: body 1rem/1.7/400(`--lh-body`), heading line-height 1.3(`--lh-heading`), h1 2rem/700(767.98px 이하 1.75rem), h2 1.5rem/700, h3 1.125rem/600, meta 0.875rem(`--fs-meta`, `.notice-list__date`에 값 동일 적용). display token 없음, 신규 `clamp()` 없음, nav의 기존 responsive font-size 계약 보존. 전역 h1~h6 규칙으로 목록/상세 페이지 제목(h2)이 기존 유동 크기(최대 2rem)/500에서 1.5rem/700으로 바뀌었다.
  - **Bootstrap 전역 연결**: `--bs-body-font-family`, `--bs-body-line-height`, `--bs-body-color(-rgb)`, `--bs-body-bg`, `--bs-link-color(-rgb)`, `--bs-link-hover-color(-rgb)`만 연결. `-rgb` 값은 hex token과 같은 색을 별도로 적었으므로 primary/text를 바꿀 때 함께 갱신해야 한다. btn/badge/form-control/list-group 등 component 변수는 변경하지 않았다.
  - **Header geometry 보호**: `#site-header { line-height: 1.5 }`(기존 Bootstrap 값). 변경 전후 header 높이가 900~1024px 구간 55/58px, 899px 이하 53px로 동일하다.
  - **Container**: 후보 C(공통 `--container-max: 1200px`, header inner와 `.container`가 같은 max-width와 좌우 gutter 1rem)를 실제 렌더링으로 검증한 뒤 최종 채택했다(DOM 구조 변경 없음). 근거: 변경 전 header 브랜드 좌측 vs 본문 좌측이 1440/1280/1200/1024/900px에서 각각 4/66/26/28/86px 어긋났으나 변경 후 모든 viewport(1440~375)에서 0px로 일치. 900~1199px 구간에서는 본문이 뷰포트 폭(gutter 1rem)을 그대로 쓰며 Program/Gallery grid가 과밀/과소하지 않고(예: 900px Gallery 카드 161px, Program 273px), 1440px에서 Program 274px/Gallery 181px로 카드 폭 회귀 상한(320/240) 이내다. 대안(1320/Bootstrap 단계형 유지)은 채택하지 않았다. header inner의 `@media (min-width: 900px)` 안 `max-width`가 `1320px`에서 `var(--container-max)`로 바뀌었다.
  - **inline style 제거**: 6개 template(`index`, `program/list`, `program/detail`, `board/list`, `board/detail`, `page/detail`)의 `style="margin-top: 40px;"`를 삭제하고 `#site-main { padding-top: var(--space-4) }`(동일한 40px)로 대체. 변경 전후 본문 시작 y좌표가 모든 viewport에서 동일하다(예: 1440px 98px, 899px 이하 93px).
  - **Radius/Shadow**: `--radius`(0.5rem) 값과 14개 사용처, 모든 `box-shadow`(dropdown, popup, inset active line) 무변경. `--radius-sm`은 만들지 않았다. `!important` 미추가, `home.css` 미분할.
  - **실제 브라우저 QA**: Docker(nginx 8088, app 재빌드) 대상 Playwright(chromium) 측정/스크린샷으로 1440/1280/1200/1024/901/900/899/768/375 × Home/Program 목록/Board 목록/Gallery 목록/연구소 소개(Page)/Program 상세/Board 상세를 확인했다. 전 조합 horizontal overflow 없음, 900/901/1024/1280/1440에서 top-level nav 한 줄(900px에서 nav 오른쪽 여유 12px → 55px로 증가: Pretendard가 약간 좁음), 899px 이하 hamburger 정상.
  - **테스트**: `./gradlew test` BUILD SUCCESSFUL, `./gradlew build` BUILD SUCCESSFUL. Playwright 전체(`PLAYWRIGHT_BASE_URL=http://localhost:8088`, 관리자 환경변수 설정) **237 passed / 0 skipped**(관리자 환경변수 없이 변경 전 실행 시 91 passed / 146 skipped였으므로 skip이 실제로 실행됨을 확인). P13-T30B/C/D·T35·T36·T37, Program/Gallery 카드 폭, 긴 제목 오버플로우, Popup 전 항목 포함. 기존 테스트 변경 없음.
  - **T2 이후로 이관된 항목**: Footer 내부 간격/정보 위계(body line-height 1.7 상속으로 여백이 커 보임)와 Header 세부 시각(T2), 공지 목록 bullet(기존 현상)/카드/섹션 rhythm/Hero(T3), raw enum(`COURSE`/`OPEN`/`NOTICE`)과 Bootstrap `.btn-primary`·`.badge.bg-info`·`.form-control` 색(T4A/T4B/T5), 입력 경계 대비와 focus 색(T4A/T6A), detail 읽기 폭 800px 후보(T5에서 token으로 도입, T1 CSS에는 미추가), skip link(T6A).

### P14-T2. Header / Footer Visual Refinement
- 의존성: P14-T1
- 산출물(예상): `static/css/home.css`(header/footer 구간), 필요 시 `home/layout/footer.html`(그룹 wrapper 추가)
- 작업 내용: Header brand/여백/hairline/dropdown·mega menu 패널 시각 정리(active/hover/focus 패턴 유지), Footer를 brand/navigation과 business information 두 그룹으로 정리(desktop 2단 가능, mobile 1단), 사업자 정보 전부 유지. component-specific radius/shadow 정리.
- 변경 금지: `header.html`, `nav-toggle.js`, `nav-submenu.js`, Menu Java/DB, 메뉴 IA/label/href, footer 링크·사업자 정보 내용, 900px breakpoint.
- 위험: dropdown dead-zone/z-index/popup stacking, `aria-current`/active 표시 회귀, `font-weight` 단언, footer 클래스 selector(`.site-footer__*`) 보존.
- 검증: 6개 viewport에서 hover/focus/keyboard 전 경로, P13-T30B/C/D·T34·T35·T36·T37 Playwright.
- DoD: header/footer 시각 개선 확인, 메뉴 keyboard/Escape/focusout/mega menu 무회귀, top-level 한 줄 유지, footer 사업자 정보 무축약, 기존 테스트 무변경 통과.
- 구현 결과(P14-T2, 최종): 디자인은 **Editorial Rule + Hybrid 상태 표현**을 채택했다. 변경 파일은 `static/css/home.css`, `frontend-tests/visual-regression.spec.js`(P13-T30D A2 assertion 2건 교체, 아래 참고), 본 문서 3개이며 `header.html`/`footer.html`/`default.html`/JS/Java/DB/Admin은 무변경(CSS만으로 해결).
  - **Header frame**: `#site-header`에 상단 `3px solid var(--color-primary)` rule + 기존 하단 1px hairline, inner `min-height: 4rem`(64px, 고정 height가 아니라 top-level이 wrap돼도 함께 커짐) → desktop 총 68px(900~1440 전 폭 동일, 변경 전 58/55px). mobile은 기존 53px + rule 3px = 56px. gradient/shadow/glass 없음, 새 accent 없음.
  - **Sticky Header(추가 요구)**: `#site-header`를 `position: sticky; top: 0`으로 고정한다(`position: fixed` 미사용 - normal flow를 유지해 main에 padding을 더하지 않고 scroll 시작 시 layout jump가 없다). `z-index: 10` 유지(Popup 1000보다 낮음, 상승 없음), 스크롤 중 본문이 비치지 않도록 `background: var(--color-bg)`를 명시(불투명, blur/투명도/`backdrop-filter`/shadow 없음 - top navy rule + bottom hairline만으로 본문과 충분히 구분됨을 확인). Header 높이는 변경 전과 동일(desktop 68px, mobile 56px)하고 `#site-main` 시작 위치는 header 높이(68/56px) 그대로다. dropdown/mega(absolute)의 containing block은 `.site-nav__item.has-submenu`라 sticky 전환의 영향이 없고, 스크롤된 상태(scrollY 800)에서 1440/1024/900px 모두 dropdown/mega가 trigger 하단에 붙어(dead-zone 0) viewport 안에서 본문보다 위에 열리며 mega는 우측 정렬(1440px 1304px, 1024px 1008px, 900px 884px)·containment를 유지한다. hover 궤적/대각선 bridge/Escape/click/바깥 click도 스크롤 상태에서 정상이다. 실측: Home/Board 목록/Board 상세/Program·Board 상세 등 스크롤 가능한 페이지에서 scrollY 0/100/500/중간/바닥 모두 header 상단 0px, 1440/1024/900/899/768/375px 모두 overflow 없음(sticky는 parent(body) content box 안에서만 유지되므로 body padding이 아니라 `#site-main` min-height로 긴 페이지를 재현).
  - **Sticky 부수 조치 2건(실제 문제가 확인되어 최소로 추가)**: (1) `html { scroll-padding-top: 5rem }` - sticky header 때문에 keyboard focus 이동(Shift+Tab 등)으로 스크롤된 요소가 header 뒤에 가려졌다(scroll-padding 없이 1440px 5건, 375px 10건 실측 → 적용 후 0건). 사이트에 hash anchor(`href="#..."`)는 없다. (2) mobile 열린 nav `max-height: calc(100vh - 4rem)`(+`100dvh`)와 `overflow-y: auto`, `overscroll-behavior: contain` - sticky header가 viewport보다 길어지는 경우(가로 모드 667x375, 320x480에서 GROUP까지 열면 header 하단 약 509px > viewport)에 아래쪽 메뉴에 접근할 수 없던 문제를 nav 내부 스크롤로 해결. 375x667/375x568/768x1024/899x700에서는 스크롤바 없이 전체가 보인다(열림 315px, GROUP 열림 447px).
  - **Brand**: desktop 1.375rem/700/`--color-primary`/letter-spacing -0.015em/nowrap(폭 187px), mobile 1.125rem/700(폭 156px). 한글 워드마크만 사용(공식 영문명이 없어 영문 descriptor/로고/아이콘 없음).
  - **Composition**: brand = container 좌측 정렬선, nav = container 우측 정렬선(`--container-max` 1200px 계약 유지, header만 넓히지 않음). 1440px에서 brand 좌측 136px = 본문 좌측 136px, nav 마지막 셀 우측 1304px = 본문 우측 1304px(0px 차이), brand~nav 간격 231px. `.site-header__inner`는 `align-items: stretch`, brand만 `align-self: center`, `#site-nav`/`#quick-menu`는 flex로 우측 정렬(`flex-wrap: wrap`은 합성 10/14개 wrap 보호를 위해 유지, `column-gap: 0`).
  - **Nav 셀**: top-level LEAF/GROUP trigger가 64px 높이 전체를 쓰는 셀이며 항목 간 rhythm은 gap이 아니라 셀 `padding-inline: clamp(0.625rem, 2vw - 0.5rem, 1rem)`(900px 10px, 1200px 이상 16px). nav font-size clamp(16→18px) 계약과 900px breakpoint 무변경, 새 breakpoint 없음.
  - **상태(Hybrid)**: normal = 배경 없음. hover/focus-visible = 셀 전체(radius 0)의 `--color-primary-soft` tint + navy 글자(LEAF는 weight 불변), GROUP hover/open = tint + 700(기존 계약), current(`.is-active`/`.has-active-child`) = 배경 없이 navy 글자 + 셀 하단(= header 하단 기준선)에 닿는 `inset 0 -3px 0` 3px line, current 셀에 hover/focus가 가면 tint 추가. normal < hover(tint) < current(기준선), GROUP open(tint+700)은 current(line)와 표현이 다르다.
  - **focus-visible**: `outline: 2px solid var(--color-primary)`. top-level 셀은 인접 셀/header 경계에 가려지지 않도록 `outline-offset: -3px`(셀 안쪽), dropdown/mega 링크는 `-2px`, 그 외 header 요소는 `2px`. outline 제거 없음.
  - **전체메뉴**: 왼쪽 1px hairline(`--color-border`) + `margin-left: 0.5rem`만으로 구분(CTA/pill/아이콘 없음).
  - **Dropdown/Mega**: 배경 `--color-bg`, 보더 `--color-border`, `--radius-sm`, shadow `0 0.25rem 0.75rem rgba(0,0,0,.08)`, `top:100%`/`margin:0`/폭/열 수 무변경. nav 셀이 header 하단까지 늘어나 panel이 header 하단선에 붙는다(trigger 하단 = panel 상단, dead-zone 0, 1440/1280/1200/1024/901/900 실측). Mega는 우측 끝 셀에서 펼쳐져 mega 우측 = trigger 우측 = container 우측(1440px 1304px, 900px 884px), 행 간격 1.25rem, GROUP heading(p)과 단독 link heading(a)을 같은 box로 통일. **Mega 대각선 hover bridge**: `#megamenu::before`(투명, panel 윗선 위 1rem, panel이 열렸을 때만 존재, layout/boundingBox 무영향)로, trigger 중앙에서 panel 좌측 하단 링크로 대각선 이동할 때 이웃 셀 위 빈 띠를 지나 mouseleave로 닫히던 실제 UX 결함(P13-T30C trajectory 테스트가 재현)을 수정했다. 열린 동안 이웃 셀 하단 16px 띠는 panel의 hover/click 영역이 되며 이웃 셀 글자 영역(중앙)의 hit는 그대로다.
  - **`--radius-sm: 0.25rem`**: dropdown/mega 패널, mobile submenu 링크, nav-toggle에서 사용(desktop/mobile top-level 셀은 radius 0). `--radius`(0.5rem)와 Popup/Hero/Card/Pagination 등 T2 밖 radius/shadow는 무변경. 하드코딩 `#fff`/`#ced4da`는 token(`--color-bg`/`--color-border`)으로 교체.
  - **Mobile(≤899.98px)**: `.site-header__inner`에 `flex-wrap: wrap`(P13부터 있던 결함 수정: 변경 전 375px brand 3줄·768px 2줄, nav가 brand 옆으로 밀림 → 375/768/899px 모두 brand 1줄, nav는 다음 행). 열린 nav는 gutter(1rem, T1 container gutter와 동일)만큼 음수 margin으로 넓힌 전폭 `--color-surface` 패널(`width: calc(100% + 2rem)`) + 상단 hairline, 항목 44px 행(텍스트 세로 중앙, 변경 전 35/40/40px) 사이 hairline, GROUP 오른쪽 CSS chevron(`::after`, `.is-open`과 동기, transition 없음), 열린 GROUP은 tint + 700 유지(T36 계약), submenu는 투명 배경 + 부모 대비 16px 들여쓰기, nav-toggle 40px(`nowrap`, token 색/border/`--radius-sm`). 열림 nav 높이 315px(GROUP 열림 447px).
  - **900px gate(실측)**: brand 1.375rem(187px) 유지, brand~nav 간격 81px, nav 8개 한 줄(폭 600px, 전체메뉴 separator 포함), 셀 padding 합 20px(=기존 gap 20px과 같은 항목 간 실제 간격), font-size 16px, mega/dropdown containment 정상(mega 우측 884px), overflow 없음. nav font/gap 축소나 brand 축소 없이 통과.
  - **Footer F2 유지**: 이번 재디자인에서 Footer는 변경하지 않았다(desktop 236.4px, mobile 314px 동일).
  - **P13-T30D(A2) assertion 2건 교체(테스트 파일 `frontend-tests/visual-regression.spec.js`)**: 둘 다 과거 구현 방식("nav를 잔여 영역 중앙 정렬", "column-gap으로 항목 간격")에 묶여 새 확정 계약과 충돌했고(실제 회귀 아님), 결과 기반 검증으로 교체했다. 교체로 인한 test case 수 변화는 없다(237개 유지, sticky 회귀 test 6개가 이후 추가되어 총 243개). (1) `nav 우측 치우침이 개선되고 logo/nav 시각적 균형`(1024/1440px 2건): nav 중심의 viewport 중심 오프셋 `<150`/`>0`과 좌우 여백 차이 `<120` 검증을 삭제하고, brand 좌측 = header inner 좌측 정렬선(±1px), nav 우측 끝 = 우측 정렬선(±1px), brand와 nav bounding box 비겹침, brand~nav 간격 ≥ 48px로 교체(원래 의도인 "nav가 로고에 붙거나 한쪽으로 극단적으로 쏠리지 않음"을 정렬선 직접 검증으로 보존, 오히려 더 엄격). (2) `900px: font-size/gap 축소 금지`: `#quick-menu` column-gap ≥ 20px를 셀 좌우 padding 합(computed style) ≥ 20px로 교체하고 font-size ≥ 16px assertion은 그대로 유지(gap을 다시 넣으면 900px에서 nav가 넘친다). behavior/accessibility/hover/click/keyboard/focus/aria/dead-zone/mega containment/Popup 등 다른 assertion은 변경하지 않았고 skip/삭제도 없다.
  - **Popup 회귀**: header z-index 10, Popup z-index 1000 유지, Popup CSS 무변경. 개발 DB에 활성 Popup이 없어 실제 Popup class 구조를 주입한 합성 검증으로 Popup이 header와 열린 dropdown 위에 표시됨(Popup radius 8px, shadow `0 16px 48px .3` 무변경, dropdown radius 4px)을 확인했다. 기존 Playwright Popup describe는 전부 통과.
  - **Footer 높이 before/after(참고, T2 기록 유지)**: ≥900px 359.3→236.4px, 899·768px 359.3→314px, 375px 386.5→314px.
  - **실제 browser QA**(Docker nginx 8088, chromium): Header 1440/1280/1200/1024/901/900/899/768/375, Footer 1440/1024/900/899/768/375, Home·Program 목록·Board 목록·Gallery·연구소 소개·Program 상세·Board 상세 = 63개 조합 전부 horizontal overflow 없음, header/본문 좌측 정렬 0px. 상태 확인: normal/LEAF hover/GROUP hover·open/current/submenu/keyboard focus-visible/mega open/mobile nav open/mobile GROUP open. Mega 상호작용: hover open, 직선·대각선(steps 10/30)·항목 간 이동 유지, 바깥으로 이동 시 close, trigger click 토글, 바깥 click close, keyboard(Enter open, Tab으로 panel 진입, Escape close 후 trigger 복귀) 정상, 열린 상태에서 header 높이/scrollWidth 무변화.
  - **Sticky 회귀 test(`frontend-tests/visual-regression.spec.js`, `P14-T2: sticky Header` describe, 6개 추가)**: (1) 1440/375px: computed `position: sticky`, `z-index: 10`, 불투명 배경, `#site-main` 시작 = header 높이(layout 무이동), scrollY 100/500/중간/바닥에서 header 상단 0·높이 불변·overflow 없음(실제 scroll 결과 검증), (2) 900/1440px: 스크롤(800px) 후 GROUP dropdown과 전체메뉴가 dead-zone 없이 viewport 안에서 본문 위로 열리고 mega 우측이 trigger에 정렬, (3) 모바일 667x375: 스크롤 후 nav/GROUP을 열어도 header 상단 0, header가 viewport를 넘지 않고 열린 nav가 스크롤되어 마지막 항목에 도달, (4) 1440px keyboard focus 이동 시 focus 요소가 sticky header 뒤에 가려지지 않음(`scroll-padding-top`). 구현을 되돌린 변형(sticky→relative, scroll-padding 0, max-height 제거)에서 6개 중 3개가 실패함을 확인했고, 나머지(dropdown/mega) test에는 이후 "스크롤된 상태에서 header 상단 0" assertion을 추가해 sticky 회귀에도 실패하도록 보강했다.
  - **Popup + sticky**: 개발 DB에 활성 Popup이 없어 실제 Popup class 구조를 주입한 합성 검증으로, 스크롤된 상태에서도 Popup(z-index 1000)이 sticky header(10)와 열린 dropdown/mega 위에 표시됨을 확인했다(Popup CSS 무변경, 기존 Popup Playwright 전부 통과).
  - **테스트**: `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit 567 tests, failures 0, errors 0, skipped 0). Playwright 전체(`PLAYWRIGHT_BASE_URL=http://localhost:8088`, 관리자 환경변수 설정) **243 executed / 243 passed / 0 failed / 0 skipped**(기존 237 + sticky 6; P13-T30B/C/D·T34·T35·T36·T37, Popup, public-console-errors 포함).
  - **T3 이후로 남은 항목**: Home 섹션 rhythm/Hero/카드(공지 bullet 포함) → T3, Program·Board 목록의 raw enum과 Bootstrap component(`.btn-primary`, `.badge.bg-info`, `.form-control`) → T4A/T4B, detail 읽기 폭 → T5, 입력 경계 대비/skip link/접근성 최종 정리 → T6A, 사이트 전체 responsive polish → T6B.

### P14-T2A. Public Navigation IA Refinement
- 의존성: P14-T2
- 산출물: `db/migration/V12__reinstate_review_and_news_menu_groups.sql`(신규), `home/board/list.html`, `src/test/java/com/monicalab/support/MenuIaMigrationTest.java`, `src/test/java/com/monicalab/board/controller/BoardViewControllerTest.java`, `frontend-tests/visual-regression.spec.js`, 본 문서
- 결정 이력(중요, 과거 결정을 삭제/왜곡하지 않음): P13-T33(V10)은 당시 발주처 요구사항("강의 후기는 하나의 게시판이며 공개 navigation에는 top-level LEAF 하나만 존재")에 따라 V8이 만든 "강의 후기" GROUP(수강 후기/특강 후기 child)을 단일 LEAF로 되돌렸다. **P13-T33 자체는 그 시점 요구사항 기준으로 올바른 작업이었다.** 이후 발주처 요구사항이 다시 최신으로 변경되어, 이번 P14-T2A가 V10 이후 상태 위에 V12로 그 결정을 다시 GROUP 구조로 전환한다. V1~V10은 무수정.
- 작업 내용:
  1. **목표 공개 IA**: `HOME` / `연구소 소개`(GROUP, 무변경) / `수강 신청`(GROUP, label 그대로 유지 - "프로그램"으로 되돌리지 않음) / `소식·자료`(GROUP, 신규: 공지사항/갤러리/자료실, "전체" 없음) / `강의 후기`(GROUP, 기존 LEAF를 재전환: 전체/수강 후기/특강 후기) / `전체메뉴`(정적, 무변경).
  2. **V12 migration**: forward-only, DELETE 없음(V8/V9 원칙 계승, V10과 다름), id 하드코딩 없음, 기존 row는 label+target_type+target_value(+target_subvalue/parent_id) 정밀 일치 조건의 UPDATE만, 신규 row는 `NOT EXISTS` guarded INSERT. (a) "소식·자료" GROUP guarded INSERT(sort_order 2). (b) 기존 top-level LEAF 3개(공지사항/갤러리/자료실, 각각 baseline 조건과 정확히 일치할 때만)를 `parent_id`/child `sort_order`(0/1/2)만 갱신해 자식으로 이동(id/label/is_visible 등 무변경). (c) 기존 "강의 후기" top-level LEAF(V10이 만든 정확한 모양 - label/BOARD_LIST/REVIEW/target_subvalue IS NULL/parent_id IS NULL - 일치 시에만) 같은 id를 재사용해 GROUP으로 전환(target_type=GROUP, target_value/target_subvalue=NULL, top-level sort_order 2→3). 대상을 찾지 못하면(관리자가 이미 수정) 이어지는 자식 INSERT도 자연히 스킵된다 - 다른 REVIEW row를 추측해서 대신 전환하지 않는다. (d) 그 GROUP의 자식 3개(전체: REVIEW/subvalue NULL/sort 0, 수강 후기: COURSE/sort 1, 특강 후기: SPECIAL/sort 2)를 guarded INSERT. "전체"의 href는 기존 `/boards?boardType=REVIEW`(programType 없음)와 완전히 동일해 REVIEW+programType=NULL(미분류) 기존 후기도 계속 포함하는 현재 semantics를 그대로 유지한다 - "COURSE+SPECIAL만"이라는 새 query semantics를 만들지 않았고, 기존 미분류 후기 데이터를 재분류하는 migration도 하지 않았다.
  3. **Board list context 분리(`home/board/list.html` 한 파일, 새 route/Controller 없음)**: `BoardViewController`가 이미 model에 싣는 `boardType`/`programType`만으로 Thymeleaf `th:with`/`th:if`를 사용해 title/filter-nav를 3가지로 분기한다(Java 변경 없음).
     - legacy(`boardType` 없음): 제목 "게시판", 필터 7개(전체/공지사항/갤러리/자료실/강의 후기/수강 후기/특강 후기) 그대로(기존 URL 호환).
     - 소식·자료(`boardType`=NOTICE/GALLERY/ARCHIVE): 제목 "소식·자료", 필터 3개(공지사항/갤러리/자료실), "전체" 없음.
     - 강의 후기(`boardType`=REVIEW): 제목 "강의 후기", 필터 3개(전체/수강 후기/특강 후기), 공지/갤러리/자료실 없음. "전체"의 href/active 조건은 legacy의 기존 "강의 후기" 링크와 완전히 동일(REVIEW+programType 없음)하며 **라벨만 context에 따라 다르다**(legacy=필터를 선택하는 맥락이라 "강의 후기", 강의 후기 context 내부=이미 그 안에 있는 맥락이라 "전체" - 확정된 UX 결정, href/query semantics는 무변경).
     keyword/pagination/"목록으로" 복귀 상태(`boards.page`)는 세 context 모두 기존 계약 그대로 보존된다.
  4. **Header active/current**: `HeaderActiveResolver`/`MenuService`/`HeaderMenuControllerAdvice` 등 **Java main code 변경 없음**(기존 범용 GROUP/BOARD_LIST 매칭 로직이 새 4-GROUP 구조를 그대로 처리).
  5. **CSS/JS 변경 없음**: 실측 결과(900px gate 포함) top-level 6개가 기존 8개보다 여유가 크고(900px brand~nav 간격, nav 폭 모두 축소), mega 4컬럼(3+1 배치)도 containment/overflow 문제가 없어 CSS를 변경하지 않았다.
- 변경 금지: "수강 신청"→"프로그램" rename, footer "게시판" 링크/IA, 신규 route/Controller(`/news`, `/reviews` 등), REVIEW+programType 없음(미분류) 데이터 강제 재분류, Home(`HomeController`/`home/index.html`/`latestReviews`)과 P14-T3 범위, 새 menu depth(3단), 관리자 Board CRUD 재설계, CKEditor, `header.html`/`footer.html`/`nav-toggle.js`/`nav-submenu.js`, `static/css/home.css`.
- 위험: 운영 DB가 V11 baseline과 다르면(관리자가 이미 Menu를 수정) V12가 조용히 스킵되어 목표 IA가 완성되지 않음(배포 전 pre-flight 확인 필요, V8과 동일한 원칙) / `MenuIaMigrationTest`의 row-identity·개수 단언 다수 갱신 / Playwright의 IA 하드코딩(top-level 배열, mega 컬럼/헤딩, admin menu 목록 "대상" 컬럼 표시, "8개"/"6개" 매직넘버, REVIEW 필터 라벨) 전수 갱신 필요.
- 검증: `MenuIaMigrationTest`, `BoardViewControllerTest`(신규 context 케이스 포함), `HeaderActiveResolverTest`/`HeaderMenuControllerAdviceTest`(무변경 통과), Playwright 전체(P13-T30B/C/D·T35·T36·T37·T30E, Popup, public-console-errors 포함), 실제 Docker 8088 Browser QA(Header 1440/1200/1024/901/900/899/768/375, Mega, 모바일 GROUP open/close, Board 7개 context, 검색/상세→목록 복귀, console error).
- DoD: 목표 IA가 실제 DB에 구현되고(16행), Board list 3개 context의 title/filter/active가 정확하며 기존 URL/query/pagination/복귀 상태가 무회귀, Header active/current가 4-GROUP 구조에서 정확, Java main/CSS/JS/Admin/Home/footer 무변경, 기존 테스트 약화 없이 IA 관련 assertion만 새 계약으로 갱신, `./gradlew build` 및 Playwright 전체 통과.
- 구현 결과(최종):
  - **Migration**: `V12__reinstate_review_and_news_menu_groups.sql`. 실제 개발 DB(운영 아님, `monica-lab-homepage-db-1`)에 적용해 **12행 → 16행**을 확인했다: 기존 id(연구소 소개/수강 신청 GROUP, 그 자식들, "강의 후기" - GROUP으로 전환되며 같은 id 재사용)는 전부 유지, 신규 4행("소식·자료" GROUP + 강의 후기의 전체/수강 후기/특강 후기)만 새 id로 추가됐다. 공지사항/갤러리/자료실은 같은 id로 `parent_id`만 "소식·자료"로 이동(sort_order 0/1/2로 재배치). DELETE 문 0개.
  - **최종 DB 구조(실측)**: `연구소 소개(0)/수강 신청(1)/소식·자료(2)/강의 후기(3)` top-level GROUP 4개, 소식·자료 자식 `공지사항(0)/갤러리(1)/자료실(2)`, 강의 후기 자식 `전체(0, subvalue NULL)/수강 후기(1, COURSE)/특강 후기(2, SPECIAL)`.
  - **Board list 실렌더링 확인(Docker 8088)**: legacy(`/boards`) 제목 "게시판"·필터 7개·"전체" active. 소식·자료 3종(NOTICE/GALLERY/ARCHIVE) 제목 "소식·자료"·필터 3개(공지사항/갤러리/자료실)·각 boardType active, "전체"·강의 후기 계열 없음. 강의 후기 3종(REVIEW 무/COURSE/SPECIAL) 제목 "강의 후기"·필터 3개(전체/수강 후기/특강 후기)·정확히 active, 공지/갤러리/자료실 없음.
  - **Header/Mega 실렌더링**: 1440/1200/1024/901/900px 모두 top-level 6개(HOME/연구소 소개/수강 신청/소식·자료/강의 후기/전체메뉴) 한 줄·overflow 없음, 899/768/375는 hamburger 정상 전환. Mega 4컬럼(연구소 소개/수강 신청/소식·자료/강의 후기) containment 정상. 소식·자료/강의 후기 GROUP 모두 hover 열림/Escape 닫힘/click 토글/바깥 click 닫힘 정상. 모바일 accordion에서 두 GROUP 모두 정확한 자식 노출.
  - **검색/복귀**: `?boardType=REVIEW&keyword=후기` 검색어 유지 확인, GALLERY 카드 클릭 → 상세 → "목록으로"(`?boardType=GALLERY&programType=&keyword=&page=0`) 복귀까지 왕복 확인.
  - **console error**: 위 QA 전 경로에서 0건(Playwright `public-console-errors.spec.js`도 포함해 통과).
  - **테스트 갱신 상세**: `MenuIaMigrationTest`(행 수 12→16, id-range 신규 4개, top-level 4-GROUP, 강의 후기/소식·자료 children 검증으로 전면 재작성, V12 DELETE 없음 신규 테스트, V12 forward migration 성공 신규 테스트), `BoardViewControllerTest`(기존 2개 active 테스트는 context 무관하게 그대로 통과 - 무변경, context별 filter 노출 신규 테스트 4개 추가), `frontend-tests/visual-regression.spec.js`(mega 6→4컬럼, top-level 8→6 배열·매직넘버, "top-level LEAF" 대표 예시를 공지사항→HOME으로 교체 2건[is-active 배경 회귀 방지 위해 비활성 페이지에서 검증], T37 active describe의 `topLeaf` 헬퍼를 GROUP-child 조회용 `childLeaf`로 교체 및 관련 단언 전면 갱신, admin menu 목록 "대상" 컬럼 기대값 갱신[BOARD_LIST+REVIEW formatter가 row 자신의 label이 아니라 BoardType 한글 라벨을 보여준다는 기존 동작을 재확인], REVIEW 필터 활성 라벨 "강의 후기"→"전체", "강의 후기→공지사항 전환" 테스트는 legacy 화면 기준으로 재작성해 원래 의도인 stale programType 제거 검증을 보존). 삭제하거나 약화한 assertion은 없다.
  - **테스트 결과**: `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit 567 tests, failures 0, errors 0, skipped 0). Playwright 전체(`PLAYWRIGHT_BASE_URL=http://localhost:8088`, 관리자 환경변수 설정) **243 executed / 243 passed / 0 failed / 0 skipped**.
  - **Docker**: 로컬 검증을 위해 `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build app`로 `app` 서비스만 재빌드/재생성했다(V12를 기존 `db-1` DB에 Flyway가 forward 적용, DB 볼륨/데이터 삭제나 초기화 없음). `db`/`nginx`/`mariadb-local` 컨테이너와 `docker-compose.local-test.yml`(untracked)은 건드리지 않았다.

### P14-T2B. Footer IA Sync
- 의존성: P14-T2A
- 산출물: `home/layout/footer.html`, `frontend-tests/visual-regression.spec.js`, 본 문서.
- 작업 내용: P14-T2A가 Header top-level IA를 `연구소 소개/수강 신청/소식·자료/강의 후기`로 바꾼 뒤에도 Footer(`.site-footer__nav`)가 이전 IA(`연구소 소개/프로그램/강의 후기/게시판`)로 남아 있던 불일치를 동기화한다. Footer는 DB/menu 기반이 아닌 기존 hard-coded 4-link flat 구조를 그대로 유지하고, label/href/순서만 최신 Header IA와 맞춘다. 최종: `연구소 소개`(`/pages/INTRODUCTION`, 무변경) → `수강 신청`(`/programs?programType=COURSE`, 신규) → `소식·자료`(`/boards?boardType=NOTICE`, 신규) → `강의 후기`(`/boards?boardType=REVIEW`, 무변경). "수강 신청"/"소식·자료"의 href는 "연구소 소개"(GROUP과 동일 라벨의 자식 href 재사용)/"강의 후기"(GROUP의 첫 자식 "전체"와 이미 동일 href)가 이미 따르던 것과 같은 원칙("GROUP과 동일 라벨의 자식이 있으면 그 href, 없으면 sort_order가 가장 앞선 자식의 href")을 4개 링크 전부에 일관되게 적용해 결정했다(수강 신청 GROUP의 동일 라벨 자식 `/programs?programType=COURSE`, 소식·자료 GROUP의 첫 자식인 공지사항 `/boards?boardType=NOTICE`).
- 변경 금지: 하위 메뉴/사이트맵형 Footer 확장, DB/menu 기반 동적 Footer 전환, 기존 Footer DOM class/시각 디자인(`static/css/home.css` 무변경), Java main/DTO/Service/Controller, DB/Flyway, JS, Header/menu 구현(`header.html`/`nav-toggle.js`/`nav-submenu.js`), P14-T2 Footer visual hierarchy(2단 grid-area 배치 등).
- 위험: `frontend-tests/visual-regression.spec.js`의 4-링크 label/href 하드락 assertion(P13-T34, 당시 "이 Task 대상 아님" 무변경 확인용) 갱신 필요, Thymeleaf `@{}` URL 빌더의 실제 렌더 결과가 기대와 다를 가능성.
- 검증: 관련 Playwright(Footer/연구소 소개/강의 후기/P13-T30C Header IA/P13-T35/P13-T37) 및 전체, `./gradlew build`, Node 전체(무관, JS 무변경 확인용), 실제 Docker 8088 렌더 확인.
- DoD: Footer label이 현재 Header top-level IA와 정확히 일치, Footer 링크 목적지가 확정값과 일치, 4-link flat 구조 유지, 기존 Footer visual design/layout 무회귀, desktop/mobile 무회귀, Header IA 무회귀, 관련 Playwright 통과, 전체 테스트 통과, `docker-compose.local-test.yml` 미포함, scope 외 파일 변경 없음.
- 구현 결과(P14-T2B, 최종): `home/layout/footer.html`의 `.site-footer__nav` 4개 `<a>`를 label/href/순서 전부 갱신했다. "연구소 소개"는 무변경(plain `href`), "강의 후기"는 무변경(기존 `th:href="@{/boards(boardType='REVIEW')}"` 그대로), 새로 추가된 "수강 신청"(`th:href="@{/programs(programType='COURSE')}"`)과 "소식·자료"(`th:href="@{/boards(boardType='NOTICE')}"`)는 "강의 후기"가 이미 쓰던 것과 동일한 Thymeleaf `@{}` URL 빌더 패턴을 재사용해 수동 문자열 조합 실수를 피했다. 실제 렌더 결과(Docker 8088)를 직접 확인해 `/programs?programType=COURSE`, `/boards?boardType=NOTICE`로 기대와 정확히 일치함을 확인했다. CSS/DOM class/Java/JS/DB 변경 없음("게시판" 링크 제거 후에도 여전히 4개 링크 유지).
  - **테스트 갱신**: `frontend-tests/visual-regression.spec.js`의 `기존 Footer navigation 4개 링크의 label/href/순서가 무변경이다`(P13-T34, `['연구소 소개','프로그램','강의 후기','게시판']`/`['/pages/INTRODUCTION','/programs','/boards?boardType=REVIEW','/boards']` 하드락)를 `Footer navigation 4개 링크가 최신 Header IA와 동일한 label/href/순서로 표시된다`(`['연구소 소개','수강 신청','소식·자료','강의 후기']`/`['/pages/INTRODUCTION','/programs?programType=COURSE','/boards?boardType=NOTICE','/boards?boardType=REVIEW']`)로 정당하게 갱신했다. 이 테스트는 애초에 "P13-T34는 Footer를 안 건드렸다"는 무변경 확인용이었고 값 자체를 영구 계약으로 성역화한 것이 아니었다. 무변경으로 남긴 다른 Footer 관련 assertion("강의 후기"/"연구소 소개" href 개별 검증, 사업자 정보, domain self-link, overflow)은 전부 그대로 통과.
  - **테스트 결과**: `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit **578개**, 무변경, failures/errors/skipped 0). Node `--test` **355개** 전부 pass(무관). Playwright 전체(`PLAYWRIGHT_BASE_URL=http://localhost:8088`) **243 executed / 243 passed / 0 failed / 0 skipped**(Footer/Header IA 관련 35개 타겟 실행에서도 전부 pass 선확인).
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build --force-recreate app`으로 `app` 서비스만 재빌드. `db`/`nginx`/`mariadb-local`과 `docker-compose.local-test.yml`(untracked)은 무변경, DB 데이터 변경 없음.

### P14-T3. Home Visual Redesign
- 의존성: P14-T1, P14-T2
- 산출물(예상): `static/css/home.css`(hero/section/card/notice 구간), `home/index.html`(**class 추가 수준에 한정**)
- 작업 내용: 홈 섹션 rhythm과 section heading 위계, hero(radius/컨트롤 절제, 배너 0건 empty state 시각 개선), 주요 소식/프로그램/후기/공지/갤러리 카드·목록 시각 통일(Program 4:3, Gallery 계열 1:1 유지). **첫 구현에서는 DOM 구조를 변경하지 않는다**: 현재 `#index-content.container`를 유지하고 CSS와 기존 DOM만으로 white/warm surface rhythm을 만들며, full-bleed background를 위한 wrapper를 처음부터 추가하지 않는다. 실제 렌더링에서 container 내부 surface가 명백히 답답/부자연스럽다고 판단될 때만 (현재 방식의 문제, wrapper 추가 시 변경 DOM, 기존 selector 영향, Playwright 영향)을 보고하고 별도 승인을 받는다. Home heading(h1) 구조는 T3 계획 단계에서 조사 후 제안·승인한다.
- 변경 금지: 섹션 순서, `HomeController`/data flow, banner/popup JS 및 DOM 계약, greeting/shortcut CTA/브랜드 메시지/overlay text 추가, "전체보기" 복원, 영문 eyebrow 강제.
- 위험: `#index-content` fragment selector, 캐러셀/popup 회귀(`hero-carousel.js`, `popup-modal.js`), 카드 폭 회귀(auto-fill grid), P13-T12/T38B 단언.
- 검증: 6개 viewport 렌더링, Hero 캐러셀/공개 Popup/메인 카드 폭/P13-T12/P13-T38B Playwright, `HomeControllerTest`.
- DoD: 홈 6개 영역 시각 통일과 위계 확보, 기존 id/class 무변경, DOM 구조 무변경(승인된 경우 제외), 기존 테스트 무변경 통과.
- 구현 결과(P14-T3, 최종): **Option B — Editorial / Content Hierarchy**를 채택했다. 변경 파일은 `static/css/home.css` **1개뿐**이다(`home/index.html`은 무수정 - 기존 id(`#home-pinned`/`#latest-programs`/`#latest-reviews`/`#latest-gallery`)만으로 스코프가 충분해 class 추가조차 필요 없었다). Java/JS/DB/Admin/Menu IA/Header/Footer/`home/board/list.html`은 전부 무변경.
  - **H1**: 조사 단계에서 사용자와 "현행 유지(h1 미추가)"로 확정. `#index-content`에 `<h1>`을 추가하지 않았고 heading 구조 관련 DOM 변경도 없다(별도 Accessibility Polish 범위로 이관).
  - **Grid gap**: 항목 수가 컬럼 수보다 적을 때 우측에 죽은 공간이 남던 문제(1440px 2건 기준 실측 748px)를, `auto-fit`(빈 트랙 제거) + **고정 px 폭**(범위가 아닌 단일 값) + `justify-content:center`로 해결했다. 최종값은 `.program-cards`(768px 이상 225px, 768px 미만은 기존 `auto-fill(minmax(220px,1fr))` 그대로 유지), `#home-pinned .gallery-grid`(200px), `#latest-reviews`/`#latest-gallery .gallery-grid`(160px, 기존 minmax 하한과 동일). `minmax(하한, 상한px)`(범위) 방식도 시도했으나, **CSS Grid 스펙상 auto-fit 컬럼 개수 계산이 상한이 고정 길이일 때 하한이 아니라 상한을 기준으로 삼는다**는 사실을 실측으로 발견해(375px에서 컬럼 수가 2→1로 줄어드는 회귀 재현) 폐기하고, 고정 폭(하한=상한)으로 교체했다(계산 공식이 기존과 동일해 컬럼 수가 보존됨이 원리적으로 보장됨). Program은 375~430px(원래도 1컬럼)에서 auto-fit으로 바꾸면 카드가 오히려 220px 섬처럼 작아지는 부작용이 실측으로 확인되어, 768px 미만은 기존 auto-fill을 그대로 두는 반응형 예외를 뒀다. 1440/1200/1024/901/900/899/768/430/390/375 × 1~20건(0건 포함) 전수 비파괴 실측(DOM clone/remove, `page.evaluate`)으로 강의 후기/갤러리/Program의 기존 컬럼 수(2/2/2/4/5/5/5/5/6/6, 1/1/1/3/3/3/3/4/4/4)가 정확히 보존되고 horizontal overflow 0임을 확인했다.
  - **Pinned 강조**: `#home-pinned`에 `padding`+`background:var(--color-surface)`+`border`+`border-radius`(기존 `.section`의 padding을 특이도로 대체, 중복 적용 없음, 음수 margin 없음 - 좁은 viewport overflow 위험 회피)로 "패널"처럼 보이게 하고, `#home-pinned .gallery-card__link`에 테두리(hover/focus-visible 시 primary색)를 추가했다. 강의 후기/갤러리보다 한 단계 큰 고정 카드 폭(200px vs 160px)도 강조 수단으로 썼다. featured 첫 카드, 이미지 비율 변경, 과도한 shadow, 새 component, carousel화, 개수 제한은 전부 사용하지 않았다. 공유 `.gallery-card`/`.gallery-grid` **기본 규칙 자체는 무수정**이며 전부 `#home-pinned ...` id 스코프로만 추가했다(Board GALLERY/REVIEW 목록에 영향 없음 - 아래 회귀 확인 참고).
  - **Surface rhythm**: `#home-pinned` 한 곳에만 적용(zebra stripe 없음, T0 "제한적 warm surface" 계약 그대로).
  - **Hero**: `.hero__slide`/`.hero__empty`의 `border-radius`를 `--radius`(0.5rem)에서 `--radius-sm`(0.25rem)으로 낮췄고(크기/비율 무관, 순수 코너 절제), `.hero__nav`/`.hero__play-pause`/`.hero__indicator`에 hover/focus-visible 배경 강조(0.5→0.65 alpha)를 추가했다. **크기/비율 계약은 전혀 건드리지 않았다** - CLS 개선을 위한 `aspect-ratio` 예약은 시도하지 않고 후속 과제로 남겼다(사용자 지시대로, hero 높이가 의미 있게 바뀔 수 있어 이번 Task에서 적용하지 않음). 배너 0건 empty state는 radius만 변경, 크기/텍스트/기능은 무변경.
  - **Program CTA 강조**: `.program-card`에 hover/focus-within 시 `border-color: var(--color-primary)`, 제목 `font-weight` 600→700. 이미지 비율(4:3), 배지 문구/색, raw `COURSE`/`SPECIAL` 표시는 무변경(T4A/T4B 범위).
  - **Reviews/Gallery**: 새 component 없이 기존 `.gallery-card` 그대로 재사용, section별로 고정 카드 폭만 다르게 줘 Pinned와 구분(200 vs 160). 카드 자체 시각(border 등)은 추가하지 않았다(Pinned만 강조).
  - **Notices**: 카드/grid로 바꾸지 않고 기존 compact list 유지. `.notice-list__link`에 hover/focus-visible 시 제목 색(primary)+밑줄(색만으로 상태 전달하지 않도록)을 추가했다. 2줄 clamp/구조 무변경.
  - **실측(비파괴 DOM/CSS mock, Docker 8088)**: 재빌드 전후 hero 높이가 모든 viewport(193/201/224/414/480×6)에서 **완전히 동일**함을 확인(hero 크기 계약 무회귀). 1440/1200/1024/901/900/899/768/430/390/375 전부 `overflowX=0`, console error 0. `#home-pinned`/`#latest-programs`/`#latest-reviews`/`#latest-gallery`/`#latest-notices` grid 컬럼 수가 기존과 정확히 일치. `/boards?boardType=GALLERY`/`REVIEW`/`REVIEW&programType=COURSE`에서 `#board-grid` 카드 폭(181px@1440, 164px@375)·컬럼 수(6/2)가 **변경 전과 동일**함을 확인해 공유 class 무회귀를 검증했다.
  - **테스트**: `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit **573개**, failures 0, errors 0, skipped 0). Node(`node --test`) **355개** 전부 pass. Playwright 전체(`admin-console-errors`/`public-console-errors`/`visual-regression`, `PLAYWRIGHT_BASE_URL=http://localhost:8088`, 관리자 환경변수 설정) **243 executed / 243 passed / 0 failed / 0 skipped**. 기존 "메인 카드 폭 회귀 검증"(`PROGRAM_CARD_MAX_WIDTH=320`/`GALLERY_CARD_MAX_WIDTH=240`) assertion은 **갱신 없이 그대로 통과**했다(새 고정폭 225/200/160이 기존 상한 이내).
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build app`으로 `app` 서비스만 2회 재빌드(1차: 초기 구현 검증, 2차: auto-fit 컬럼 수 회귀 발견 후 최종 grid 전략 수정 반영). `db`/`nginx`/`mariadb-local`과 `docker-compose.local-test.yml`(untracked)은 무변경, DB 데이터 삭제/초기화 없음.

### P14-T3A. Home Section Visual Rhythm / Banner Empty-State Refinement
- 의존성: P14-T3
- 산출물(예상): `home/index.html`(section마다 내부 `<div class="container">` 삽입, `#banners` 조건부 렌더링), `static/css/home.css`(section별 tonal surface, Hero positioning 이동), `HomeControllerTest`(banner 0건 계약 갱신), 본 문서.
- 작업 내용: T3가 조건부로 유보해 둔 "Home section 배경 wrapper 추가"(T0 이후 결정 표, T3 계획의 "container 내부 surface가 명백히 답답/부자연스럽다고 판단될 때 별도 승인" 조건)를 실행한다. 5개 콘텐츠 section(주요 소식/최신 프로그램/강의 후기/공지사항/갤러리)의 배경이 viewport 좌우 끝까지 이어지는 full-width surface를 갖도록 하고, 활성 배너가 0건이면 Hero section 자체를 렌더링하지 않는다.
- 변경 금지: 섹션 순서/`HomeController`/data flow, filter-nav/pagination(Program/Board), Program/Board T4A/T4B 카드·grid 자체, Header/Footer DOM/CSS, "전체보기" 텍스트 복원, global h1/skip link/landmark(P14-T6A 범위), `.container`/`.gallery-grid`/`.gallery-card`/`.program-card`/`.notice-list` 전역 규칙 값.
- 위험: `#index-content > section` DOM 순서 테스트, Hero 캐러셀 컨트롤 절대 위치 기준점(`position:relative`) 이동, 배너 0건 기존 계약(`heroShowsEmptyStateWhenNoBannersExist`)과의 충돌, section 배경이 zebra stripe처럼 과해 보일 위험.
- 검증: `HomeControllerTest`(갱신 포함), 대표 4개 viewport(1440/900/899/390) Browser QA, Playwright 전체, Program/Board regression smoke.
- DoD: 배너 0건 Hero 완전 미렌더, 5개 section의 restrained tonal surface, Pinned outer surface 통합, 기존 T3 card/grid 100% 보존, Java/JS/backend/DB 변경 없음, 기존 테스트 무변경 통과(정당한 banner 계약 갱신 제외).
- 구현 결과(P14-T3A, 최종): 사용자 결정에 따라 **적용 범위(5개 콘텐츠 section 전부) / Restrained Tonal 3단(`--color-bg`/`--color-surface`/`--color-primary-soft`, 새 hex 없음) / Pinned 통합(inner panel 제거)**을 그대로 구현했다. `home/index.html`은 `#index-content`에서 `class="container"`를 제거하고(fragment selector `~{::#index-content}`는 ID 기반이라 class 변경과 무관, 안전 확인) 5개 콘텐츠 section 각각의 heading/card/list를 새 `<div class="container">`로 감쌌다(기존 id/class/th: 데이터 바인딩/route/href 전부 무변경). 전역 `.container` 규칙 값은 무수정 — 더 많은 위치에서 재사용만 했다.
  - **Banner 0건**: 조사 단계 초안(`.hero.is-empty{display:none}`로 빈 `<section>`을 DOM에는 남기는 방식)을 사용자가 명시적으로 반려하고, `<section id="banners">` 자체에 `th:if="${!#lists.isEmpty(banners)}"`를 걸어 **DOM에서 완전히 제거**하는 방식으로 구현했다. 기존 `.hero__empty`("등록된 배너가 없습니다.") 마크업과 그 CSS 규칙을 도달 불가능해진 죽은 코드로 판단해 함께 삭제했다. `hero-carousel.js`는 `#hero-viewport`/`#hero-controls` 부재 시 이미 안전하게 early return하므로(조사 단계에서 코드로 직접 확인) **JS는 무수정**이다.
  - **Hero 폭 보존**: Hero는 이번 full-width surface 대상에서 제외했다(배경 없음). `#index-content`가 더 이상 `.container`가 아니게 되면서 `#banners` 내부에 새 `.container`를 하나 둬 기존과 정확히 같은 폭을 유지했다(실측: 1440px에서 내부 컨테이너 box가 정확히 기존과 동일한 120~1320px). `.hero`가 갖던 `position:relative`(캐러셀 nav/indicator의 절대 위치 기준점)를 `#banners .container`로 옮겼다 - outer/inner 폭이 항상 같으므로 결과 좌표는 이전과 동일함을 실측으로 확인했다.
  - **Surface 배치**: `#home-pinned`는 T3의 inner panel(border+radius+자체 padding)을 제거하고 `background: var(--color-primary-soft)`로 outer section 자체를 강조 톤으로 통합했다(사용자 결정 D). `#latest-reviews`/`#latest-gallery`는 "이미지 중심 소셜 프루프성 콘텐츠"로 묶어 `--color-surface`를 배정했다. `#latest-programs`/`#latest-notices`는 "핵심 상품/공식 공지"로 판단해 배경을 추가하지 않고 기본 흰색(`--color-bg`)을 유지했다 - 5개 section을 기계적으로 3색 순환시키지 않고 콘텐츠 성격 단위로만 배정했다(결과적으로 tint-white-tint-white-tint 순서가 되지만, Pinned의 톤(primary-soft, 차가운 남색 계열)과 Reviews/Gallery의 톤(surface, 따뜻한 베이지 계열)이 서로 다른 색상 계열이라 "3색 반복"으로 보이지 않는다 - 실측 스크린샷으로 harsh하지 않음을 확인했다). vertical padding은 5개 section 모두 기존 `.section{padding:var(--space-4) 0}`를 그대로 재사용해 중복 padding이 없다.
  - **Pinned 카드/다른 4개 section**: card/grid(200px/225px/160px/160px), hover/focus-visible, aspect-ratio, object-fit, thumbnail/placeholder는 전부 무변경(T3/T4A/T4B 계약 그대로).
  - **"전체보기" 텍스트**: 추가하지 않았다. 4개 section(주요 소식 제외)은 이미 P13-T12부터 heading 자체가 `.section-title__link`로 목적지 링크였고, "전체보기" 텍스트 재도입은 P14-T0/T3의 명시적 변경 금지 항목이자 기존 Playwright 계약(`body`에 "전체보기" 텍스트 부재)과 충돌하므로 손대지 않았다.
  - **DOM 순서 테스트 갱신**: `homePinnedSectionIsRenderedImmediatelyAfterPopupsAndBeforeLatestPrograms`(배너 0건 fixture)의 기대 목록에서 `"banners"`를 제거했다. 신규 `heroIsNotRenderedWhenNoBannersExist`(`#banners`/`.hero__empty` 부재, "등록된 배너가 없습니다" 텍스트 부재 검증)가 `heroShowsEmptyStateWhenNoBannersExist`를 대체했고, 신규 `heroSectionIsFirstInTopLevelOrderWhenAtLeastOneBannerExists`(배너 1건 fixture)가 배너 존재 시 `#banners`가 여전히 최상단 section임을 검증한다. 기존 `homeRendersFinalMenuIaWithHomeGroupDropdownsAndMegaMenu`의 `#banners` 존재 단언도 같은 이유로 부재 단언으로 갱신했다(Menu IA 자체 검증과는 무관한 부수 단언). 배너 1건/N건의 기존 캐러셀/컨트롤 계약 테스트는 전부 무변경으로 통과.
  - **실측(Docker 8088, Browser QA)**: 대표 4개 viewport(1440/900/899/390) 전부 `overflow=0`, `console error=0`. Banner 0/1/N(관리자 API로 생성 후 즉시 cleanup, DB 잔존 없음 확인) 상태 각각 정상 - 0건은 `#banners` 완전 부재, 1건은 controls 없음/폭 기존과 동일, 3건은 carousel 클릭까지 정상(관측된 "console error"는 QA용 가짜 이미지 경로의 404뿐, 기존 Playwright Hero 테스트와 동일한 합성 데이터 관례). Pinned도 관리자 API로 `visible=false`→QA→`visible=true`(원래 데이터로 완전 복원, DB 영구 변경 없음) 왕복으로 "Pinned 없음" 상태를 확인 - `#popups`(시각적으로 빈 wrapper) 다음에 바로 `#latest-programs`(흰 배경)가 이어져 어색함 없음. 각 section 배경이 실제로 `left=0 ~ right=viewport width`까지 이어지고 내부 `.container`는 1440px에서 120~1320px(기존과 동일)로 정렬됨을 실측. Pinned 카드 200px, Program 카드 desktop 225px/모바일 auto-fill(T3 계약 그대로), Review/Gallery 카드 160px 전 viewport 동일. `.section-title__link` focus-visible(밑줄) 정상. 전체 페이지 스크린샷(1440/390) 육안 검토로 과도한 zebra stripe 없음과 Pinned가 자연스럽게 강조됨을 확인했다.
  - **Program/Board regression smoke**: `/programs`(썸네일 112×80, 한글 배지), `/boards?boardType=GALLERY`(카드 160px) 전부 T4A/T4B 값 그대로, overflow 0, console error 0.
  - **테스트**: `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit **577개**, failures 0, errors 0, skipped 0 - 기존 576 + 신규 1). Node(`node --test`) **355개** 전부 pass(무관, JS 무변경). Playwright 전체(`admin-console-errors`/`public-console-errors`/`visual-regression`, `PLAYWRIGHT_BASE_URL=http://localhost:8088`) **243 executed / 243 passed / 0 failed / 0 skipped**.
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build app`으로 `app` 서비스만 재빌드(최초 `--build`가 이미지는 새로 만들었지만 컨테이너를 교체하지 않는 것을 발견해 `--force-recreate`로 재실행). `db`/`nginx`/`mariadb-local`과 `docker-compose.local-test.yml`(untracked)은 무변경. Banner QA는 정상 관리자 API로 생성 후 즉시 DELETE, Pinned QA는 기존 항목의 `visible` 플래그를 껐다가 정확히 원상복구 - DB 영구 변경/데이터 삭제 없음.

### P14-T3B. Home Card/Thumbnail Content Differentiation
- 의존성: P14-T3A
- 산출물(예상): `home/index.html`(Program `programType` label 매핑), `static/css/home.css`(Pinned/Review/Gallery card 차별화), `HomeControllerTest`(신규 label 검증), 본 문서.
- 작업 내용: 조사(READ-ONLY) 결과 Pinned/Review/Gallery가 `.gallery-card`를 완전히 동일하게 공유해(Board GALLERY/REVIEW `#board-grid`까지 공유) "이미지+제목 카드"로 반복되어 보이던 문제를, T0 A-8("콘텐츠 성격별 비율 유지, 전체 통일 금지")과 T3/T3A/T4A/T4B 계약을 깨지 않는 범위에서 콘텐츠별로 차별화한다.
- 변경 금지: 전역 `.gallery-grid`/`.gallery-card`/`.gallery-card__link`/`.gallery-card__thumb`/`.program-card`/`.program-cards` 값, `#board-grid`(T4B)/`#program-list`(T4A) 규칙, T3A section surface, "전체보기" 복원, 새 breakpoint, card width/grid 컬럼 계산, masonry/carousel, Pinned featured-첫카드/BOARD·PROGRAM 배지, Java main/DTO/Service/Repository/DB/Flyway/JS.
- 위험: `.gallery-card` 공유 규칙을 잘못 건드려 Board 회귀, object-fit 변경이 A-8 원칙과 문자 그대로 충돌(Review 한정 명시적 예외 승인).
- 검증: `HomeControllerTest`(신규 포함), Playwright 전체, Board `#board-grid`/Program `#program-list` regression smoke, 4개 대표 viewport Browser QA.
- DoD: Pinned/Program/Review/Gallery가 시각적으로 구분되되 공통 design system(typography/color/radius/focus) 안에서 통일감 유지, 전역 selector 무수정, 기존 테스트 무변경 통과(정당한 신규 테스트 제외), Java/JS/DB 변경 없음.
- 구현 결과(P14-T3B, 최종): 사용자 확정 계약대로 4개 영역을 전부 **Home section ID 스코프**(`#home-pinned`/`#latest-programs`/`#latest-reviews`/`#latest-gallery`)로만 구현했다. 전역 `.gallery-grid`/`.gallery-card`/`.gallery-card__link`/`.gallery-card__thumb`/`.program-card`/`.program-cards`는 diff 0(순수 추가만, 기존 규칙 한 줄도 수정하지 않음).
  - **Pinned**: `#home-pinned .gallery-card__title`에 `font-weight:700`/`font-size:1rem`만 추가(CSS 1규칙, DOM 무변경, 2-line clamp/200px 폭/1:1 비율/기존 T3 hover-focus-border 전부 무변경). featured 첫 카드, BOARD/PROGRAM 배지는 추가하지 않았다(사용자 확정).
  - **Program**: `home/index.html`의 `.program-card__type` 삼항을 `${program.programType().name() == 'COURSE'} ? 'COURSE' : 'SPECIAL CLASS'`로 변경(사용자가 이번 Task에서 확정한 영문 UI display label, T4A와 동일한 presentation-layer 원칙). domain enum/DB/API/DTO 무변경. 4:3 비율/object-fit:cover/status 배지/border는 전부 무변경.
  - **Review**: `#latest-reviews .gallery-card__thumb img { object-fit: contain; }` 한 줄로 원본 이미지 전체가 보이게 했다(wrapper의 1:1 aspect-ratio는 무변경이라 grid 폭/컬럼 계산에 영향 없음). `.gallery-card__thumb`가 이미 갖고 있던 `background:var(--color-surface)`가 letterbox 여백을 자연스러운 액자처럼 보이게 해 별도 배경 선언이 불필요했다. A-8의 "object-fit 유지" 문구와 문자 그대로 충돌함을 조사 단계에서 확인했고, `#latest-reviews` 한정 명시적 예외로 사용자 승인을 받았다(Gallery/Board/Pinned의 object-fit은 무변경).
  - **Gallery**: `#latest-gallery .gallery-card__thumb { aspect-ratio: 4/3; }`로 Home Gallery만 정사각→landscape 전환(Board `#board-grid`는 전역 규칙 무수정이라 영향 없음, 실측으로 재확인). object-fit은 cover 유지.
  - **Review/Gallery hover·focus-visible**: `#home-pinned`/`#board-grid`가 이미 쓰던 것과 동일한 시각 언어(테두리 1px + hover/focus-visible 시 `--color-primary`)를 `#latest-reviews`/`#latest-gallery .gallery-card__link`에 이식했다(두 section 모두 기존에는 카드 레벨 hover/focus-visible이 전혀 없었음 - 조사 단계에서 확인된 기존 공백을 이번에 메웠다). title에 `padding:0 var(--space-1) var(--space-1)`(Pinned와 동일 값)도 함께 추가해 테두리 안쪽에서 제목이 눌리지 않게 했다.
  - **Notice**: 완전 무변경.
  - **테스트**: `HomeControllerTest`에 `latestProgramsShowsProgramTypeAsExplicitEnglishDisplayLabelInsteadOfRawEnumName` 신규 추가("SPECIAL CLASS"가 "SPECIAL"을 부분 문자열로 포함해 `doesNotContain`으로는 raw 노출을 증명할 수 없으므로 `containsExactlyInAnyOrder`로 두 배지 전체 텍스트를 정확히 검증). 기존 테스트 전부 무변경 통과. `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit **578개**, 기존 577 + 신규 1, failures/errors/skipped 0). Node **355개** 전부 pass(JS 무변경). Playwright 전체 **243 executed / 243 passed / 0 failed / 0 skipped**.
  - **실측(Docker 8088, Browser QA, 4개 대표 viewport 1440/900/899/390)**: 전부 `overflow=0`, `console error=0`. Pinned title `font-weight:700`/`16px` 확인. Program `programType1`이 "SPECIAL CLASS"로 렌더링 확인. Review thumb `object-fit:contain` 확인(158×158, border 포함), Gallery thumb 158×119(비율 1.33≈4:3) 확인. Review/Gallery 카드 focus-visible 시 `border-color: rgb(31,58,95)`(`--color-primary`) 확인(0.15s transition 정착 대기 후 측정). **Board `#board-grid`(GALLERY/REVIEW 필터 둘 다) 실측 결과 1:1/cover/무테두리로 완전히 무변경** - Home 변경이 전혀 전파되지 않음을 확인. Program List(`#program-list`) 112×80 썸네일/한글 배지도 무변경. Pinned 0/1/2/5+ 상태를 관리자 API(visibility 토글 + 임시 pin 생성 후 즉시 삭제, 최종 원본 데이터로 완전 복원 확인)로 비파괴 실측해 전부 정렬 유지·overflow 0·console error 0 확인. Review/Gallery 두 section 모두 기존 placeholder(썸네일 없음) 박스가 새 비율(1:1/4:3)을 그대로 반영해 정상 동작함을 확인. 전체 페이지 스크린샷(1440) 육안 검토로 Gallery가 landscape, Review가 square+테두리로 시각적으로 명확히 구분됨을 확인.
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build --force-recreate app`으로 `app` 서비스만 재빌드(T3A에서 학습한 대로 처음부터 `--force-recreate` 사용). `db`/`nginx`/`mariadb-local`과 `docker-compose.local-test.yml`(untracked)은 무변경.

### P14-T3C. Home Composition Redesign
- 의존성: P14-T3B
- 산출물(예상): `static/css/home.css`(Pinned/Program/Review/Gallery 매크로 구성), `frontend-tests/visual-regression.spec.js`(카드 폭 상수 갱신 + 신규 composition 계약), 본 문서.
- 작업 내용: T3B가 정리한 카드 디테일(비율/hover/배지/object-fit)은 그대로 둔 채, Home 5개 section(Pinned/Program/Review/Notice/Gallery)이 "CMS 기본 템플릿처럼 평평하고 반복적"으로 보이는 문제를 카드 디테일이 아니라 **매크로 구성(실루엣)** 차원에서 해결한다. Pinned는 (사용자 조건부 승인에 따라) 2열 수평 편집형 media card, Program은 수평 정보 카드(desktop/tablet, image-left 140px)+수직 폴백(mobile, <768px, 텍스트 공간 부족 실측 근거), Review는 paper-frame(inset padding) 액자 처리, Gallery는 사진 중심 3/2/1열 grid로 차별화한다. Notice/Hero/Footer/section heading 텍스트는 완전히 무변경으로 둔다.
- 변경 금지: 전역 `.gallery-grid`/`.gallery-card*`/`.program-cards`/`.program-card*` 값, `#board-grid`(T4B)/`#program-list`(T4A) 규칙, T3A section surface, "전체보기" 복원, 새 breakpoint 추가, Pinned 첫 카드 특별 취급, 조작된 리뷰어/평점/인용 데이터, Notice/Hero/Footer(T2B) 변경, auto-fit+`minmax(_,1fr)` 조합(T3/T3A에서 발견된 좁은 뷰포트 컬럼 수 오산 패턴 재발 금지), Java/DTO/Service/Repository/DB/Flyway/JS.
- 위험: auto-fit 상한이 유동적(`minmax(_,1fr)`)이면 auto-fit 컬럼 수 계산이 하한이 아니라 상한을 기준으로 오작동하는 패턴 재발, Program mobile(<768px) 텍스트 공간 부족(실측 약 202px)으로 인한 제목 줄바꿈 파손, `align-items:stretch`가 thumb의 `aspect-ratio:4/3`을 왜곡, Review 카드 title에 padding이 중복 적용되는 회귀.
- 검증: `HomeControllerTest`(class 기반 selector라 무변경 통과), Playwright 전체(카드 폭 상수 갱신 포함 신규 composition 계약 추가), 9개 viewport(1440/1200/1024/901/900/899/768/390/375) Browser QA, Pinned 2~7건/Program 1~3건 content-count 비파괴 QA(관리자 API 생성 후 즉시 삭제), Board `#board-grid`/Program `#program-list` regression 재확인.
- DoD: Pinned/Program/Review/Notice/Gallery 5개 section이 매크로 구성에서 서로 다른 콘텐츠 타입으로 읽히되 공통 design system(typography/color/radius/focus) 안에서 통일감 유지, 전역 selector 무수정, 기존 테스트 무변경 통과(정당한 계약 갱신 제외), Java/JS/DB 변경 없음.
- 구현 결과(P14-T3C, 최종): `home/index.html` 변경 없이 **`static/css/home.css` CSS만으로** 전 구현을 완료했다(class명 전부 유지 → DOM 재구성만으로 `HomeControllerTest` 무변경 통과 확보). 전역 `.gallery-grid`/`.gallery-card*`/`.program-cards`/`.program-card*`/`#board-grid`/`#program-list`는 diff 0(순수 추가만).
  - **Pinned**: 사용자 조건부 승인에 따라 최초 계획(1열 stack)을 취소하고 **2열 수평 media card**로 구현. `#home-pinned .gallery-card__link`를 `display:flex`(96px 정사각 thumb + 제목, `align-items:center`)로 바꾸고, `#home-pinned .gallery-grid`를 `@media (min-width:768px)`에서 `repeat(auto-fit, 390px)`(고정폭, `minmax(_,1fr)` 아님)+`justify-content:center`로, 768px 미만은 `1fr` 단일 컬럼으로 분기했다. 모바일(<768px)에서도 카드 자체는 계속 `flex-direction:row`를 유지(가로 유지, thumb만 72px로 축소)해 "세로로 쌓이는 리스트"가 아니라 항상 media-object 카드로 보이게 했다(사용자 명시 요구사항). 첫 카드 특별 취급 없음.
  - **Program**: `#latest-programs`로 스코프(사용자 조건부 승인 - 현재 Home 전용 class이나 방어적으로 ID 스코프 유지). `.program-cards`를 `@media (min-width:768px)`에서 `repeat(auto-fit, 380px)`로, `.program-card__link`를 `display:flex; align-items:flex-start`(thumb 140px 고정 + body)로 바꿔 image-left 수평 정보 카드로 전환했다. `align-items:flex-start`를 명시적으로 선택한 이유는 flex 기본값 `stretch`가 thumb의 `aspect-ratio:4/3`을 body 높이에 맞춰 늘려 깨뜨리는 것을 막기 위함(CSS 주석으로 근거 기록). `<768px`는 실측(텍스트 영역 약 202px 부족)에 따라 사용자가 확정한 대로 `flex-direction:column`(image-top)으로 폴백, minmax 하한도 220px→280px로 올렸다.
  - **Review**: `#latest-reviews .gallery-card__link`에 `padding:var(--space-1)`, thumb에 `border-radius:var(--radius-sm)`을 추가해 사진이 카드 안에 액자처럼 얹힌 느낌(paper-frame)을 냈다. 기존 T3B의 title padding 공유 규칙(`#latest-reviews`, `#latest-gallery` 공통)을 분리해 `#latest-gallery`만 유지시켰다(Review는 link-level padding이 이미 title까지 균일하게 감싸므로 이중 padding 방지). 가짜 리뷰어/평점/인용 데이터는 추가하지 않음(Board 스키마에 없는 필드).
  - **Gallery**: `#latest-gallery .gallery-grid`를 `repeat(auto-fit, 320px)`로 넓혀 사진 중심 grid로 전환(4:3+cover는 T3B 그대로 무변경).
  - **Notice/Hero/Footer/heading**: 완전 무변경.
  - **9개 viewport 실측(Playwright 측정 스크립트, Docker 8088)**: 전 viewport `overflow=0`. Pinned/Program 모두 899~1440px에서 정확히 2열(`cardWidth` 390/380px), 768px는 계산대로 1열 dead-zone(2열 시도 시 폭 초과)으로 자연 전환(새 breakpoint 아님, 기존 768px 재사용), 390/375px는 Pinned `flexDirection:row`(가로 유지) vs Program `flexDirection:column`(세로 폴백)으로 정확히 갈렸다. Gallery는 1024~1440px 3열, 768~901px 2열, 390/375px 1열로 계산과 정확히 일치. Review(160px, T3B 계약)는 이번 Task에서 폭 변경 없음, 컬럼 수만 자연 변화.
  - **Pinned/Program content-count 비파괴 QA**: 관리자 API로 Pinned를 2→7건까지, Program을 2→최대 노출 3건까지 늘려가며 실측(완료 후 전부 삭제해 원본 DB 상태로 복원, `afterCleanup` 재측정으로 확인). Pinned는 상한 없이 전부 렌더링되며(개수 제한 없음) 홀수 개수(3/5/7)에서도 마지막 행의 남는 카드가 좌측 트랙에 안정적으로 정렬되고 전체 폭으로 늘어나지 않음을 확인. Program은 "최신 프로그램"이 항상 최신 3건으로 상한이 걸려있는 기존(T3 이전부터의) 동작을 재확인했고, 3건일 때 2+1로 자연스럽게 wrap됨을 확인. 전 케이스 `overflow=0`.
  - **focus-visible 재확인**: Pinned/Program 카드 모두 키보드 포커스 시 `border-color: rgb(31,58,95)`(`--color-primary`)로 정상 강조됨을 확인(Program은 outer `.program-card`의 기존 `:focus-within` 규칙이 그대로 적용됨을 재확인 - 새 flex 래핑이 focus-within 전파를 깨지 않음).
  - **Board/Program List regression 재확인**: `/boards?boardType=GALLERY|REVIEW`의 `#board-grid` thumb(158×158, 1:1, link padding 0)과 `/programs`의 `#program-list` thumb(112×80)·배지가 T4A/T4B 그대로임을 실측으로 재확인(Home 변경이 전파되지 않음).
  - **시각 검토**: 1440/768/375 전체 페이지 스크린샷을 육안 검토해 Pinned(컴팩트 가로 media card)·Program(구조화된 가로 정보 카드+상태 배지)·Review(작은 정사각 액자형)·Notice(텍스트 리스트)·Gallery(큰 사진 중심 grid)가 매크로 구성 자체로 서로 다른 콘텐츠 타입으로 읽히며, "또 하나의 공지사항 list"처럼 보이는 문제가 해소됨을 확인했다. 768px dead-zone에서도 카드가 깨지지 않고 중앙 정렬된 1열로 자연스럽게 보임을 확인.
  - **테스트**: `HomeControllerTest` 무변경 통과(578/578). `visual-regression.spec.js`의 `PROGRAM_CARD_MAX_WIDTH`(320→400)/`GALLERY_CARD_MAX_WIDTH`(240→340)를 새 고정폭(380/320px) 기준으로 갱신(값을 완화한 것이 아니라 실제로 바뀐 디자인 계약을 반영 - 회귀 감지 능력 자체는 auto-fit 고정폭 구조로 오히려 더 강화됨). 신규 `P14-T3C: 메인 섹션 매크로 구성 계약` describe에 4건 추가(desktop Program image-left, mobile Program image-top, desktop Pinned 2열, Gallery 가로형 비율) - 구현 디테일이 아니라 사용자가 실제로 보는 composition 계약만 검증. `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit **578개**, 기존과 동일, failures/errors/skipped 0). Node(`node --test src/test/js/**/*.test.js`) **355/355** pass(JS 무변경). Playwright 전체 **247 executed(기존 243 + 신규 4) / 247 passed / 0 failed**(최초 전체 실행 시 Java 전체 빌드와 동시 실행되어 리소스 경합으로 무관한 테스트 2건이 `page.goto` 30s timeout으로 실패했으나, Home CSS와 무관한 영역(P13-T14 게시판 pagination overflow, P13-T30B Header GROUP dropdown)이었고 격리 재실행 시 즉시 통과해 순수 환경성 flake임을 확인했다).
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build --force-recreate app`으로 `app` 서비스만 재빌드. `docker-compose.local-test.yml`(untracked)은 무변경.

### P14-T3D. Home Art Direction Redesign
- 의존성: P14-T3C
- 산출물(예상): `home/index.html`(Program/Review/Gallery DOM 재구조화 + Pinned/Notice eyebrow), `static/css/home.css`(전 section 재설계), `HomeControllerTest`/`frontend-tests/visual-regression.spec.js`(새 DOM class에 맞춘 selector 갱신 + 신규 composition 계약), 본 문서.
- 작업 내용: T3C가 완성한 카드 디테일 차별화를 넘어, 각 Home section이 실제로 다른 layout silhouette을 갖는 "Editorial Education × restrained Visual Magazine" 방향으로 매크로 구성 자체를 재설계한다. Pinned는 framed card 성격 유지(FEATURED eyebrow만 추가), Program은 uniform card grid→editorial alternating row(큰 순번+좌우 교대), Review는 card grid→deep navy full-width borderless photo strip, Notice는 card 없는 2단(eyebrow+title/list) editorial 구성, Gallery는 균등 grid→1 large+N small 비대칭 mosaic으로 전환한다. muted terracotta(`#C1652F` 계열) accent를 신규 도입하되 eyebrow/순번/rule 등 작은 요소에만 한정한다.
- 변경 금지: Hero carousel 기능/DOM selector 계약(`#hero-viewport`/`#hero-controls`/`.hero__slide`/`.hero__indicator`/`#hero-prev`/`#hero-next`/`#hero-play-pause`), HomePinnedContent 기능(unlimited/sortOrder/visible/0건 미렌더), 전역 `.gallery-grid`/`.gallery-card*`(Board `#board-grid`와 공유) 값, Program excerpt 신규 생성(HTML→plain-text 유틸 포함), 관리자 Theme Settings, Header/Footer/Board List/Program List redesign, backend/API/DB.
- 위험: `.gallery-grid`/`.gallery-card*` 공유 구조를 잘못 건드려 Board GALLERY/REVIEW 회귀, Gallery mosaic이 sparse item count(0~4건)에서 붕괴, CSS Grid 같은 grid-area를 공유하는 여러 item이 겹쳐 그려지는 렌더링 결함(구현 중 실제 발견·수정), terracotta accent의 작은 텍스트 WCAG 대비 부족.
- 검증: `HomeControllerTest`(신규 class 반영), Playwright 전체, 9-viewport(1440/1200/1024/901/900/899/768/375) 실측, Gallery 0~5건/Review 0~3건/Pinned 2~8건 content-count QA, Board GALLERY/REVIEW regression 재확인.
- DoD: 5개 section이 서로 다른 layout silhouette으로 read되면서 교육기관 신뢰감 유지, 공유 selector 무수정, 기존 기능 계약(Hero/Pinned/Board/Program List) 무회귀, backend/API/DB 변경 없음.
- 구현 결과(P14-T3D, 최종): Figma(`https://www.figma.com/design/hgxkG5vJBIxakzll7woW9a`)는 이 환경에서 인증 문제로 직접 열 수 없었다(HTTP 403) - 사전 READ-ONLY 조사에서 사용자가 명시한 디자인 계약(텍스트)만을 기준으로 구현했고 이 사실을 그대로 기록한다.
  - **Pinned**: DOM/CSS 거의 무변경(T3C 2-column 구조 그대로 재사용). `FEATURED` eyebrow(`<p class="section-eyebrow">`, `--color-accent-text` 색) 1줄만 추가. 2~8건 비파괴 실측으로 기존 계약(2열/1열 dead-zone/72px mobile 가로 유지) 전부 무회귀 확인.
  - **Program**: `.program-cards`/`.program-card*`(P13-T2~P14-T3C)를 전량 제거하고 `.program-rows`/`.program-row*`로 교체(repo 전체 재검색으로 사용처가 Home 1곳뿐임을 재확인한 뒤 dead CSS 없이 완전 교체). DOM은 [순번-썸네일-본문] 고정 순서, desktop 좌우 교대는 CSS Grid `grid-template-areas`를 홀/짝 row(`nth-child(even)`)별로 다르게 배정하는 것만으로 구현(DOM 재정렬 없음). 순번(01/02/03)은 실제 반복 순서(`programStat.count`)를 쓴 실데이터이며 excerpt는 명시적으로 도입하지 않음(현재 DTO에 없고, 가짜 문장 금지 원칙). status 배지는 T4A(Program List)가 이미 검증한 절제된 시각 언어(surface+border+muted, OPEN만 primary 강조)로 재사용. `<768px`는 기존과 동일한 실측 근거로 세로 stack 폴백. 768~1440px 전 구간에서 dead-zone 없이 alternating이 유지됨을 실측 확인(그리드 기반이 아니라 flex column of full-width row라 T3C의 auto-fit 계산이 아예 필요 없어진 구조적 개선).
  - **Review**: `#latest-reviews`를 `.section--dark`(`background:var(--color-primary)`, 새 색상 추가 없이 기존 navy 토큰 재사용)로 반전하고 Home 전용 `.review-strip*` component(공유 `.gallery-card*`와 완전 분리)로 교체. border/shadow 없는 photo-centric strip, placeholder는 dark UI에 맞는 옅은 반투명 흰색. focus-visible은 흰색 2px outline(navy 대비 11.48:1). 다크 배경용 신규 토큰 `--color-accent-on-dark`(#E8A17B, navy 대비 약 5.37:1)를 eyebrow에, 헤딩/caption은 기존 `--color-primary-contrast`(흰색, 11.48:1)를 재사용해 새 토큰 중복 없이 완성.
  - **Notice**: `.notice-list*` DOM/class 완전 무변경, `NEWS` eyebrow만 추가. **구현 중 실제 렌더링 결함을 2단계로 발견·수정했다.** 1차: eyebrow와 title을 같은 grid-area(`identity`)에 공유시키면 두 item이 같은 셀에 겹쳐 그려져(CSS Grid는 같은 area의 여러 item을 자동으로 세로 flow시키지 않음) 텍스트가 겹쳐 보이는 문제 발견 → `eyebrow`/`title`을 별도 행(area)으로 분리하고 `list`가 두 행에 걸쳐 반복되는 이름으로 자동 span되도록 1차 수정. 2차(최종 승인 전 QA에서 재발견): 그 1차 수정 구조에서도 `list`가 2개 행에 걸쳐 span하는 item이라, CSS Grid의 암시적 `auto` 행 크기 분배가 "list가 필요로 하는 초과 높이"를 `eyebrow` 행에도 나눠 줘 eyebrow 행이 부풀고 그 아래 title이 아래로 밀려 "NEWS"와 "공지사항" 사이가 부자연스럽게 벌어지는 문제가 실제 Docker 렌더링에서 다시 발견됨 → 근본 원인(list와 행을 공유하는 구조 자체)을 제거하기 위해 eyebrow+h2를 `.notice-section__heading` wrapper 1개로 묶어 "list와 스팬을 공유하지 않는 단일 grid item"으로 만들고, 1행 2열(`"heading list"`) + `align-items:start`로 재구성해 최종 해결(실측: eyebrow-title 간격 8px로 의도한 작은 gap, heading/list 모두 동일 y좌표에서 시작). `.section-title`의 전역 기본 `margin-bottom`(24px)은 Notice heading 안에서 불필요한 여백을 만들어 `#latest-notices .notice-section__heading .section-title`로만 스코프해 0으로 재설정(전역 `.section-title` 무변경). desktop 2단(좌측 heading/우측 list)은 `#latest-notices .container`만 ID 스코프 grid화(`grid-template-areas`), wrapper 1개(`.notice-section__heading`) 외 추가 DOM 없음.
  - **Gallery**: `.gallery-grid`/`.gallery-card*`(공유)에서 완전히 분리한 `.gallery-mosaic*`로 교체. "1 large + N small"은 `:first-child`의 `grid-column/row: span 2` + `grid-auto-flow:dense`의 auto-placement만으로 구현(`:has()` 미사용, 조건부 마크업/JS 없음). caption은 사진 위 gradient scrim으로 항상 노출(hover 전용 정보 금지 원칙 준수). desktop 4열 percentage 기반 grid(고정 px 트랙 아님 - 그 자체로 auto-fit 관련 회귀 클래스가 구조적으로 불가능해짐), mobile은 2열로 전환하고 lead는 2열 전체 폭+자연 aspect-ratio로 재설정. 실제 프로덕션 상한(5건)에서 첫 item이 두 번째 item보다 항상 큰 면적으로 렌더됨을 실측 확인, 0~4건 상태는 dev DB가 이미 상한을 초과해 비파괴로 강제 재현 불가(T3C의 Program count 조사에서 확인된 것과 동일한 제약) - CSS 수학적 설계(dense packing)와 실제 상한 상태에서의 검증으로 갈음.
  - **Typography/Color**: Pretendard 단일 유지(신규 font/CDN 없음). 신규 토큰 4개만 추가 - `--color-accent`(#C1652F, 큰 텍스트/비텍스트 전용), `--color-accent-text`(#96491E, 작은 텍스트용, 실측 대비 6.43:1), `--color-accent-on-dark`(#E8A17B, Review 다크 섹션용, 대비 5.37:1), `--color-text-muted-on-dark`(#c0c8d2, 대비 6.79:1, 현재 미사용이나 향후 다크 배경 muted 텍스트 필요 시를 위해 정의). `.section-eyebrow` 공용 class 1개 추가.
  - **Spacing**: 기존 `--space-3/4/5`만 재사용, 신규 modifier 2개(`.section--relaxed`=64px: Program/Gallery, `.section--compact`=24px: Notice) + `.section--dark`(Review) 추가. Pinned/Review는 modifier 없이 기존 40px 유지.
  - **Shared CSS 보호**: `.gallery-grid`/`.gallery-card*`/`.program-cards`/`.program-card*` 등 전역 규칙 diff 0(순수 삭제만 발생한 `.program-card*`는 재검색으로 다른 사용처 없음을 확인한 뒤 제거). `/boards?boardType=GALLERY|REVIEW`(`#board-grid`, 158×158/1:1 무변경) 실측 재확인 완료.
  - **테스트**: `HomeControllerTest` 578/578 무변경 통과(Program/Review/Gallery 관련 selector를 새 class로 갱신, Pinned/Notice/Hero 관련은 무변경). `./gradlew cleanTest test build` BUILD SUCCESSFUL. Node 355/355 pass(JS 무변경). Playwright `메인 카드 폭 회귀 검증`을 구조적으로 더 이상 성립하지 않는 계약으로 판단해 폐기(값 완화 아님 - Program/Gallery 모두 percentage/flex 기반이라 auto-fit 폭 폭주 자체가 불가능해짐), `P14-T3D: 메인 섹션 매크로 구성 계약`(7건: Program 홀/짝 교대 2건+mobile 1건, Pinned 2열 재사용 1건, Review dark 배경 1건, Notice desktop/mobile 2단 2건)과 `P14-T3D: "갤러리" 비대칭 mosaic`(3건: 첫 item 확대, 단일 item, mobile overflow) 신규 추가(총 10건). Playwright 전체 결과·최종 diff는 최종 보고에 기록.
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build --force-recreate app`으로 `app`만 재빌드(구현 중 Notice 겹침 결함 발견 후 재빌드 1회 추가 수행). `docker-compose.local-test.yml`(untracked)은 무변경.

### P14-T4A. Program List Visual Refinement
- 의존성: P14-T1 (T3와 병렬 가능)
- 산출물(예상): `home/program/list.html`, `static/css/home.css`, `ProgramViewControllerTest`(A-9의 raw enum 기대값 갱신), 필요 시 공통 list visual foundation
- 작업 내용: Program 목록(제목/thumbnail/metadata/hover/spacing)과 Program filter/search 영역 시각 정리, Program 사용자 표시 라벨(programType/recruitStatus)의 한글 표시명 적용(presentation layer에서만), Bootstrap `list-group`/`badge`/`btn`/`form-control` override. Program과 Board가 공유하는 pagination/filter 등 공통 visual foundation은 여기서 먼저 만들고 T4B가 재사용한다(실제 코드 조사 결과 공통 요소를 어느 Task에 두는 것이 더 안전한지 T4A 계획 단계에서 다시 확인한다). Program 상세(`program/detail.html`)의 배지 표시명은 T5와의 중복을 피해 T4A 계획에서 소속을 확정한다.
- 변경 금지: domain enum/DB/API 값, 필터·검색·pagination 동작과 파라미터, 목록 상태 보존 계약(P13-T14/T28), `#program-list > li > a` 구조와 `.program-list__*`, thumbnail placeholder 동작.
- 위험: `ProgramViewControllerTest:217`의 raw enum 기대값 갱신(삭제/약화 금지), `.badge` locator 3곳 재확인, `#program-list` id selector specificity, 목록 thumbnail 회귀(P13-T12).
- 검증: `ProgramViewControllerTest`(갱신 포함), P13-T12/T14 Playwright, 6개 viewport 렌더링.
- DoD: Program 목록/필터/검색 시각 통일, raw enum이 한글 표시명으로 표시되고 domain/DB/API 값 무변경, 공통 foundation이 문서화되어 T4B가 재사용 가능, 기존 테스트 무변경 통과(정당한 계약 갱신 제외).
- 구현 결과(P14-T4A, 최종): **Option B — Editorial Content Hierarchy**를 채택했다. 이번 Task는 **Program List만** 구현했고(T4B/T5는 착수하지 않음), 변경 파일은 `home/program/list.html`, `static/css/home.css`, `ProgramViewControllerTest`, 본 문서 4개뿐이다. `.filter-nav`/`.pagination-bar`(Program+Board 공유 foundation)는 이미 충분히 정돈되어 있어 **수정하지 않았다**(공유 selector에 손대지 않았으므로 Board regression 위험이 원천적으로 없음 - 아래 실측으로도 재확인).
  - **raw enum 한글 표시**: `#program-list .program-list__meta`의 두 배지를 Home(`#latest-programs`, P13-T17)과 동일한 Thymeleaf 삼항 표현식 패턴으로 매핑했다(domain enum/DB/API/DTO 무변경, Java helper 추가 없음). ProgramType/RecruitStatus 각각 COURSE/SPECIAL, OPEN/CLOSED 두 값만 존재함을 구현 전 재확인했다. `programType`: COURSE→"수강", SPECIAL→"특강". `recruitStatus`: OPEN→"모집중"(배지에 `.is-open` modifier 추가), CLOSED→"모집마감".
  - **DOM 계약 무변경**: `#program-list > li.list-group-item > a.program-list__link` 구조, `.program-list__*` 클래스명 전부 유지. 배지에 `.program-list__type-badge`/`.program-list__status-badge` 클래스만 추가(기존 `badge bg-secondary`/`bg-info` Bootstrap color utility class를 제거하고 대체 - Bootstrap 전역 `.badge` 규칙 자체는 무수정).
  - **hover/focus-visible**: `#program-list .list-group-item`에 hover/`:focus-within` 시 `--color-surface` 배경(키보드 포커스가 마우스 hover보다 약하지 않도록 동일 적용), `.program-list__link:focus-visible`에 명시적 `--color-primary` outline(기존 브라우저 기본 outline 제거 없이 추가), hover/focus-visible 시 제목(`.program-list__title`) 색이 `--color-primary`로 변경(Home Notice list의 hover 패턴과 동일 언어).
  - **배지 시각**: Bootstrap 기본 `bg-secondary`(회색)/`bg-info`(청록)가 사이트 token과 무관해 "관리자 CRUD 나열" 인상을 줬다(조사 단계 스크린샷 실측). 두 배지 모두 `--color-surface` 배경 + `--color-border` outline + `--color-text-muted` 텍스트로 절제하고, 모집 상태가 OPEN일 때만 `--color-primary` outline/텍스트로 미세하게 구분한다(텍스트 자체가 "모집중"/"모집마감"이라 색만으로 상태를 전달하지 않음).
  - **Thumbnail**: 크기 계약(desktop 112×80/mobile 80×60) 완전 무변경, `border-radius`만 `--radius`→`--radius-sm`로 다른 컴포넌트와 통일(순수 코너 절제, 크기/object-fit 무관).
  - **Filter/Pagination**: **무변경**(§12 규칙에 따라 수정하지 않음 - 이미 Program/Board가 공유하는 generic foundation이 충분히 정돈되어 있다고 판단).
  - **Board/Home 회귀 확인**: `.filter-nav`/`.pagination-bar`/`.gallery-card*`/`.program-card*` 등 공유 selector를 전혀 건드리지 않았으므로 원천적으로 회귀 위험이 없으나, 실제 브라우저로 `/boards`(legacy/NOTICE/GALLERY/ARCHIVE/REVIEW/REVIEW+COURSE/REVIEW+SPECIAL, 1440/375)와 Home(hero/pinned/latest-programs/reviews/gallery, 1440/375)을 재측정해 **모든 값이 P14-T2A/P14-T3 그대로**임을 확인했다(Board active filter 라벨 정상, Home hero 480/193px·pinned 200px·program 225px·review/gallery 160px 전부 동일, overflow 0, console error 0).
  - **Deferred(문서에 이미 정의된 범위로 이관, 코드 무수정)**: Program 상세(`home/program/detail.html`)의 programType/recruitStatus raw 배지 → **P14-T5**(문서 산출물 그대로). Board(`home/board/list.html`) raw enum/시각 정리 → **P14-T4B**. Home(`#latest-programs .program-card__type`)의 raw programType 표시는 이미 완료된 P14-T3 범위를 다시 열지 않기로 결정해 이번 T4A에서도 스코프 아웃했다 - 문서상 이를 명시적으로 맡을 후속 Task가 아직 정의되어 있지 않으므로, 새 Task로 임의 배정하지 않고 "별도 후속 범위 확인 필요"로만 기록한다.
  - **테스트**: `ProgramViewControllerTest`에 신규 2건 추가(`listShowsKoreanLabelsForSpecialProgramTypeAndClosedRecruitStatusInsteadOfRawEnumNames`, `listMarksOnlyOpenRecruitStatusBadgeWithIsOpenModifierClass`), 기존 `listRendersThumbnailTitleAndExistingTypeStatusInfoInsideTheItemLinkKeepingUlLiAStructure`의 raw enum 기대값을 한글 표시명으로 갱신(`.doesNotContain("COURSE","OPEN")` 추가). DOM 구조 assertion(`ul#program-list.list-group`, `#program-list > li.list-group-item > a.program-list__link`)은 무변경으로 유지되어 그대로 통과. `./gradlew cleanTest test build` BUILD SUCCESSFUL(JUnit **575개**, failures 0, errors 0, skipped 0 - 기존 573 + 신규 2). Node(`node --test`) 355/355 pass(무관). Playwright 전체(`admin-console-errors`/`public-console-errors`/`visual-regression`) **243 executed / 243 passed / 0 failed / 0 skipped**(기존 assertion 약화 없이 통과).
  - **Docker**: `docker compose -f docker-compose.yml -f docker-compose.local-test.yml up -d --build app`으로 `app` 서비스만 1회 재빌드(template/CSS 변경을 반영해 실제 Browser QA를 하기 위함 - 정적 리소스가 이미지에 baked-in되어 런타임에 자동 반영되지 않음). `db`/`nginx`/`mariadb-local`과 `docker-compose.local-test.yml`은 무변경, DB 데이터 삭제/초기화 없음.

### P14-T4B. Board List Visual Refinement
- 의존성: P14-T4A
- 산출물(예상): `home/board/list.html`, `static/css/home.css`, `BoardViewControllerTest`(A-9의 raw enum 기대값 갱신)
- 작업 내용: Board 목록(NOTICE/ARCHIVE 행 목록, GALLERY/REVIEW 썸네일 그리드)과 Board 사용자 표시 라벨(boardType) 한글 표시명, 날짜 표기 통일 검토(홈 `yyyy.MM.dd` vs 게시판 `yyyy-MM-dd HH:mm`, 표기 변경 시 관련 단언 확인), T4A의 공통 foundation(pagination/filter/검색) 재사용(중복 구현 금지), Bootstrap override.
- 변경 금지: domain enum/DB/API 값, 필터 nav/pagination/상세→목록 복귀 상태 보존(P13-T28), `#board-grid`/`#board-list`/`#board-type-filter` 및 `.board-list__*`, `.gallery-card*` 구조.
- 위험: `BoardViewControllerTest`의 `.board-list__type` raw enum(`"NOTICE"` 등) 기대값 갱신, REVIEW 두 하위 목록(수강/특강 후기) 필터 회귀, 갤러리 그리드 비율.
- 검증: `BoardViewControllerTest`(갱신 포함), P13-T14/T27/T28 Playwright, 6개 viewport 렌더링.
- DoD: Board 목록 시각이 Program 목록과 하나의 시스템으로 통일, raw enum 한글 표시, 기존 테스트 무변경 통과(정당한 계약 갱신 제외), pagination/filter 중복 구현 없음.
- 구현 결과(P14-T4B, 최종): T4A(Program List)가 확립한 public list visual language를 그대로 재사용해 Board 목록(NOTICE/ARCHIVE 텍스트 목록, GALLERY/REVIEW 썸네일 그리드)을 정리했다. 변경 파일은 `home/board/list.html`, `static/css/home.css`, `BoardViewControllerTest`, 본 문서 4개뿐이다(계획 대비 변동 없음). `.filter-nav`/`.pagination-bar`/pagination fragment는 T4A와 동일하게 이미 충분히 정돈되어 있어 **수정하지 않았다**(중복 구현 없음).
  - **raw enum 한글 표시**: `#board-list .board-list__type` 배지를 NOTICE→공지사항, GALLERY→갤러리, ARCHIVE→자료실, REVIEW→강의 후기로 매핑했다(domain enum/DB/API/DTO 무변경, Java helper 추가 없음). BoardType이 4값이라 T4A의 단순 삼항 대신, 본 파일 상단 `pageTitle`과 이미 같은 방식으로 쓰이던 "하나의 `${}` 안에서 SpEL 삼항을 중첩"하는 패턴으로 전부 처리했다.
  - **날짜 표기 통일**: `yyyy-MM-dd HH:mm` → `yyyy.MM.dd`로 Home(`#latest-notices`)과 통일했다(사용자 결정, Thymeleaf format 문자열만 변경, createdAt 데이터/DTO/Java/DB 무변경).
  - **REVIEW programType 카드 라벨**: 추가하지 않기로 결정(사용자 결정). Home `#latest-reviews`의 동일 컴포넌트도 라벨이 없어 기존 일관성을 유지했고, 수강 후기/특강 후기 구분은 기존 filter context로 충분하다고 판단했다.
  - **DOM 계약 무변경**: `#board-list > li.list-group-item > a.board-list__link > .board-list__row(.board-list__type/.board-list__title) + .board-list__date`, `#board-grid > li.gallery-card > a.gallery-card__link > .gallery-card__thumb(-placeholder) + .gallery-card__title` 구조 전부 유지. 배지는 기존 `.board-list__type` 클래스 그대로 두고 `badge bg-secondary`의 `bg-secondary`만 제거.
  - **`#board-list` hover/focus-visible/배지**: T4A의 `#program-list .list-group-item`/`.program-list__type-badge` 패턴을 동일 원리로 `#board-list` scope에 이식(border-color 토큰, hover/`:focus-within` surface 배경, `.board-list__link:focus-visible` outline, hover/focus 시 제목 강조, 배지를 surface+border+muted token 스타일로 통일). Board는 Program의 recruitStatus 같은 "상태" 개념이 없어 배지 강조색 분기는 두지 않았다.
  - **`#board-grid` sparse layout**: 항목 1~3개일 때 desktop에서 auto-fill이 남긴 빈 트랙 때문에 카드가 왼쪽에 몰리는 문제(Home이 P14-T3에서 고친 것과 동일 원인)를 `#latest-reviews`/`#latest-gallery`와 완전히 동일한 상수(`repeat(auto-fit, 160px)` + `justify-content:center`)로 해결했다. 전역 `.gallery-grid`는 무수정, `#board-grid`로만 스코프해 Home 3개 section(`#home-pinned`/`#latest-reviews`/`#latest-gallery`)과 무관하다. 기존 auto-fill 하한(160px)과 동일한 값이라 데이터가 충분한 페이지의 컬럼 수는 변하지 않는다.
  - **`#board-grid` 카드 hover/focus**: 전역 `.gallery-card__link`에는 원래 hover/focus-visible이 없었다(Home도 동일, `#home-pinned`만 자체 테두리 강조 보유). `#home-pinned`와 동일한 언어(테두리 + hover/focus 시 `--color-primary` 강조)를 `#board-grid .gallery-card__link`에 이식했다. 전역 `.gallery-card*`는 무수정.
  - **전역 selector 무수정 확인**: `.list-group-item`(Bootstrap), `.badge`(Bootstrap), `.gallery-grid`, `.gallery-card*`(base) 전부 diff 없음 — 새 규칙은 전부 `#board-list`/`#board-grid` ID로 스코프됨(T4A와 동일 원칙, Home/Program 회귀 원천 차단).
  - **테스트**: `BoardViewControllerTest`에 신규 1건 추가(`listShowsKoreanLabelsForAllFourBoardTypesInsteadOfRawEnumNames` — legacy `/boards`에서 4개 타입이 섞여 나올 때 4값 전부 한글로 표시되고 raw 값이 남지 않는지 확인, GALLERY/REVIEW는 필터링 시 `#board-grid`로 전환돼 배지 자체가 없으므로 legacy 혼합 목록으로 검증), 기존 `listRendersBoardTypeTitleAndCreatedAtInsideTheItemLink`의 raw enum(`"NOTICE"`)과 날짜 정규식(`\d{4}-\d{2}-\d{2} \d{2}:\d{2}`) 기대값을 각각 한글 표시명(`"공지사항"`)과 새 날짜 형식(`\d{4}\.\d{2}\.\d{2}`)으로 갱신. DOM 구조 assertion과 REVIEW/programType 필터·복귀 상태·pagination 테스트는 전부 무변경으로 유지되어 그대로 통과. `visual-regression.spec.js`는 boundingBox/텍스트 기반 assertion이 배지 색이나 날짜 형식 자체에 결합되어 있지 않아 **변경하지 않았다**(§14 판단 기준 - 새로 보호가 필요한 안정적 계약이 없다고 판단).
  - **Deferred(문서에 이미 정의된 범위로 이관, 코드 무수정)**: Board 상세(`home/board/detail.html`)의 raw boardType 배지, REVIEW 상세 programType 표시 여부 → **P14-T5**.

### P14-T5. Detail / Static Page Reading Experience
- 의존성: P14-T1, P14-T4B (상세 라벨 표시명은 T4A/T4B 결정 반영)
- 산출물(예상): `home/program/detail.html`, `home/board/detail.html`, `home/page/detail.html`, `static/css/home.css`(`.ckeditor-content` 텍스트 요소 구간)
- 작업 내용: 읽기 폭(약 760~820px), page title/metadata 위계(가능하면 h1, 변경 전 Java/Playwright 의존성 재확인), paragraph/list/table/blockquote/link typography(`.ckeditor-content`의 **텍스트 요소에 한정**), 첨부파일/신청하기/목록으로 버튼 위계, 정적 페이지(GREETING/INTRODUCTION/HISTORY/LOCATION)가 단순 CKEditor 출력처럼 보이지 않도록 공통 page header 정리(신규 breadcrumb/hero 금지 — 정정 노트 참고). 정적 페이지의 표/iframe 등 콘텐츠 출력은 sanitizer 허용 범위를 T5 계획에서 먼저 확인한다.
- 변경 금지: CKEditor image resize/alignment/alt/figcaption/overflow/link policy 규칙, sanitizer, 대표 이미지/본문 이미지 분리(P13-T41, 상세에 대표 이미지 자동 출력 금지), `#attachment-link`/`#apply-link`, 목록 복귀 로직(P13-T28), 관리자 CKEditor 설정.
- 위험: `.ckeditor-content .image*` 규칙과의 specificity 충돌(P13-T29/T39/T40 회귀), h2→h1 변경, 이미지 overflow.
- 검증: P13-T20/T22/T28/T29 Playwright, `HtmlSanitizerTest`(무변경), 375/768/1440에서 inline 이미지/resize/정렬 콘텐츠 렌더링 확인.
- DoD: 3종 상세와 정적 페이지의 읽기 경험 개선, CKEditor 이미지 기능 전부 무회귀, 기존 테스트 무변경 통과.

### P14-T6A. Accessibility Polish
- 의존성: P14-T2, P14-T3, P14-T4B, P14-T5
- 산출물(예상): `static/css/home.css`, `home/layout/default.html`(skip link), 필요 시 공개 template heading 정리
- 작업 내용: skip link(`#site-main` 대상), heading hierarchy 재점검(Home 포함, T3 결정 반영), 전 영역 text contrast 재검증, interactive target 크기 점검(filter-nav/pagination 포함), 색만으로 상태를 전달하는 곳 보완, mobile keyboard/focus QA.
- 변경 금지: 기존 focus-visible/aria-current/keyboard navigation/Escape/focusout/popup focus trap/reduced-motion 동작, 숨겨진 SEO 전용 h1 추가.
- 위험: skip link가 sticky/stacking과 충돌, heading 변경이 test selector에 영향.
- 검증: keyboard-only 순회, P13-T30B/C/T35/T37 Playwright, `public-console-errors.spec.js`.
- DoD: skip link 동작, AA contrast 충족, 터치 영역 기준 충족, 기존 접근성 테스트 무회귀.

### P14-T6B. Responsive Polish
- 의존성: P14-T2, P14-T3, P14-T4B, P14-T5
- 산출물(예상): `static/css/home.css`
- 작업 내용: 1440/1024/900/899/768/375(필요 시 390/430)에서 spacing/stacking/grid 밀도/typography 최종 조정. 기존 breakpoint(480/767.98/899.98/900)를 유지하고 새 breakpoint 추가를 피한다.
- 변경 금지: 900px navigation breakpoint 변경, DOM 재구성, JS.
- 위험: 좁은 폭에서 긴 제목/긴 한글 문자열 clipping, gallery/program grid 회귀(P13 "메인 카드 폭"/"긴 제목 오버플로우" 회귀 테스트).
- 검증: 6개 viewport 전 페이지 실측, 기존 반응형 Playwright 전체.
- DoD: 전 viewport horizontal overflow/clipping/이미지 왜곡 없음, top-level 한 줄 유지, 기존 테스트 무변경 통과.

### P14-T7. Final Visual Regression / Design QA
- 의존성: P14-T6A, P14-T6B
- 산출물(예상): `frontend-tests/visual-regression.spec.js`(신규 케이스 추가 위주, 기존 assertion 약화 금지), `docs/TASK.md`(결과 기록)
- 작업 내용: 공개 전 페이지 × 6개 viewport 최종 QA, visual QA 보강(기존 infrastructure 우선 활용), 문서 기록. 필요 시 Phase 완료 후 CSS refactoring Task 제안(승인 전 착수 금지).
- 변경 금지: 기존 테스트 삭제/약화, 새 기능 추가.
- 위험: 환경 의존 flaky 스냅샷.
- 검증: Playwright 전체, `./gradlew build`, Docker 8088 수동 확인.
- DoD: 전 Task DoD 재확인, 보존 계약 전 항목 무회귀, 6개 viewport QA 체크리스트 통과, 남은 후속 항목이 문서에 기록됨.

### P14-T8A. Theme Settings Backend/Domain Foundation
- 의존성: 없음(Phase 14 최초 계획에는 없던 신규 후속 작업. P14-T4A/P14-T4B는 Program/Board List Visual Refinement로 이미 완료된 별개 Task이며, ID 재사용을 피하기 위해 Theme Settings는 P14-T8A~T8D로 신규 채번한다)
- 산출물: `db/migration/V13__create_site_theme_setting_table.sql`, `theme/entity/SiteThemeSetting.java`, `theme/entity/AccentPreset.java`, `theme/repository/SiteThemeSettingRepository.java`, `theme/dto/SiteThemeSettingView.java`, `theme/service/SiteThemeSettingService.java`, `theme/repository/SiteThemeSettingRepositoryTest.java`, `theme/service/SiteThemeSettingServiceTest.java`, 본 문서
- 작업 내용: Admin이 홈 화면의 accent 색상 프리셋과 5개 section(Pinned/Program/Review/Notice/Gallery) 노출 여부를 나중에 관리할 수 있도록 하는 backend/domain 기반만 구축한다(이번 Task는 읽기 전용 기반뿐이며 Admin API/UI/공개 화면 적용은 전혀 포함하지 않는다). `site_theme_setting` 테이블은 `setting_key` 컬럼과 `UNIQUE(setting_key)` 제약으로 `SITE_THEME`라는 단일 논리 그룹만 표현하는 "logical singleton"이며(테이블 전체가 물리적으로 1행만 가질 수 있음을 강제하는 구조는 아님 - CHECK 제약/고정 PK는 도입하지 않고, `settingKey`를 Controller/Request DTO 등 외부 입력으로 절대 받지 않는 것으로 충분한 안전장치로 판단), 값은 `SiteThemeSetting.SITE_THEME_KEY` 상수로만 참조한다. `AccentPreset`은 이번 Task에서 `TERRACOTTA` 하나만 정의한다(BURGUNDY/FOREST는 실제 색상/WCAG 대비 검증 후 P14-T8C에서 함께 추가). 기본 visibility는 5개 section 전부 `true`(Pinned=true/Programs=true/Reviews=true/Notices=true/Gallery=true, P14-T3D의 현재 공개 화면 동작과 동일). `V13` 마이그레이션이 위 기본값으로 시드 1행을 생성한다. `SiteThemeSettingService.getSetting()`이 유일한 public API로, DB에 `SITE_THEME` 행이 있으면 그 값을, 없으면 위 기본값과 동일한 fallback을 반환하며 fallback 반환 자체는 어떤 DB write도 일으키지 않는다(read 시점 auto-create 없음). 반환 타입은 Entity가 아니라 불변 record `SiteThemeSettingView`로, 저장되지 않은(id==null) Entity를 반환할 때 생기는 persistence-state 혼동을 피하기 위한 선택이다.
- 변경 금지: 기존 완료 Task(P14-T4A/P14-T4B 포함) 문서·코드, public/admin UI(Controller/템플릿/CSS/JS/sidebar), `SecurityConfig`, V1~V12 마이그레이션, Home/Header/Footer 렌더링.
- 위험: 없음(신규 독립 패키지, 기존 코드 미참조).
- 검증: `SiteThemeSettingRepositoryTest`, `SiteThemeSettingServiceTest`, `./gradlew clean build` 전체.
- DoD: `SiteThemeSetting`/`AccentPreset`(TERRACOTTA only)/`Repository`/`Service`(읽기 전용, `getSetting()` 단일 API) 구현, `V13` 마이그레이션+기본값 시드, row 없을 때 DB write 없는 fallback, 기존 테스트 전체 무회귀, public/admin UI 변경 없음.
- 구현 결과(P14-T8A, 최종): 위 설계대로 정확히 구현했다. Service는 `getSetting()` 하나만 public으로 노출하며 `updateSetting()`/`saveSetting()`/upsert류는 전혀 없다(Request DTO/Admin API가 아직 없으므로 YAGNI). `SiteThemeSettingRepositoryTest` 3건(정상 저장/조회 round-trip, 미존재 시 empty, `setting_key` 중복 시 `DataIntegrityViolationException`), `SiteThemeSettingServiceTest` 3건(행 존재 시 DB 값 반환, 행 없을 때 TERRACOTTA+5×true fallback 반환, fallback 호출이 `repository.count()==0`을 유지함을 확인하는 negative test)을 추가했다. `./gradlew clean build` 기준 JUnit 584/584 pass(기존 578 + 신규 6), failures/errors/skipped 전부 0. public/admin UI·Header/Footer/home.css/JS/SecurityConfig 변경 0건(신규 `com.monicalab.theme` 패키지 + `V13` 마이그레이션 + 테스트만 추가, 기존 tracked 파일 diff 없음). 후속 범위: Admin API/UI는 **P14-T8B**, BURGUNDY/FOREST 등 실제 accent 색상과 공개 CSS 적용은 **P14-T8C**, Home 5개 section의 실제 show/hide 적용은 **P14-T8D**로 각각 이관한다(Hero는 이미 Banner 관리로 동등한 통제가 가능해 Theme Settings 대상에서 제외). section reorder, layout variant, 자유 색상/폰트 선택, page builder류 기능은 P14-T4 투자조사 단계에서 이미 scope 밖으로 결정되었으며 이번 Task에서도 다루지 않는다. T8B/T8C/T8D는 아직 계획 단계 이전이므로 별도 절로 미리 만들지 않고 이 후속 범위 설명으로만 남긴다.

### P14-T8B. Admin Theme Settings API/UI
- 의존성: P14-T8A
- 산출물: `theme/dto/SiteThemeSettingRequest.java`, `theme/controller/AdminThemeController.java`, `theme/controller/AdminThemeViewController.java`, `admin/theme/form.html`, `theme/entity/SiteThemeSetting.java`(변경), `theme/service/SiteThemeSettingService.java`(변경), `common/exception/ErrorCode.java`(변경), `admin/layout/sidebar.html`(변경), `theme/controller/AdminThemeControllerTest.java`, `theme/controller/AdminThemeViewControllerTest.java`, `theme/service/SiteThemeSettingServiceTest.java`(변경), `admin/controller/AdminViewControllerTest.java`(변경), `frontend-tests/admin-console-errors.spec.js`(변경), 본 문서
- 작업 내용: 관리자가 P14-T8A의 Theme Settings 값(accentPreset + 5개 section visibility)을 조회·저장할 수 있는 Admin API/UI를 추가한다. **공개 홈페이지 적용은 이번 Task 범위가 아니다**(accent 적용은 P14-T8C, section show/hide 적용은 P14-T8D). `com.monicalab.page`(`CmsPage`/`PageType`)의 고정 타입 singleton 패턴을 그대로 벤치마킹했다: singleton write 정책은 "SITE_THEME row가 없으면 자동 생성하지 않고 404"이며(`findBySettingKey(...).orElseThrow(SITE_THEME_SETTING_NOT_FOUND)`, `CmsPage.update()`와 동일 방식), row 생성 책임은 계속 V13 마이그레이션 시드에만 있다. `getSetting()`의 기존 read fallback(행 없음 → DB write 없이 기본값 반환)은 이번 Task에서 전혀 수정하지 않아 read/write의 "행 없음" 처리를 의도적으로 다르게 유지한다. Request DTO(`SiteThemeSettingRequest`)는 정확히 6개 필드(`accentPreset` + 5개 `Boolean`)만 받으며 id/settingKey를 받지 않고, boolean은 primitive가 아니라 `@NotNull Boolean`으로 선언해 JSON 필드 누락이 조용히 `false`로 저장되는 것을 원천 차단한다. Admin API는 `GET/PUT /api/admin/theme`(singleton이라 `/{id}` 없음), Admin View는 `GET /admin/theme` → `admin/theme/form`이며, 화면은 `admin/page/form.html`과 동일하게 JS가 `GET`으로 초기값을 채우고 `PUT`으로 저장하는 패턴을 재사용한다. Accent 선택 UI는 `AccentPreset.values()`를 Model로 내려받아 enum 기반으로 렌더링해, T8C에서 프리셋이 늘어나도 template 구조 변경 없이 자동 확장되도록 했다. Hero toggle은 만들지 않는다(Banner 관리로 대체). Security/CSRF는 기존 `/admin/**`·`/api/admin/**` wildcard 보호와 CSRF 규칙을 그대로 재사용했고 `SecurityConfig`/`GlobalExceptionHandler`/`AdminFetch.js`는 무변경이다. Migration 추가/변경 없음(V13이 이미 모든 컬럼 보유).
- 변경 금지: 기존 완료 Task(P14-T4A/P14-T4B, P14-T8A 기록) 수정, public 파일(`HomeController`/`home/index.html`/`home.css`/Header·Footer/public JS) 및 `ThemeControllerAdvice` 등 공개 렌더링 연결, `SecurityConfig`/`GlobalExceptionHandler`/`AdminFetch.js`, V1~V13 마이그레이션, `AccentPreset`에 새 프리셋 값 추가, section reorder/layout variant/자유 색상·폰트/page builder.
- 위험: JSON 필드 누락이 조용히 `false`로 저장될 위험(`@NotNull Boolean`으로 차단), read fallback과 write 정책 혼동(별개로 명확히 분리), singleton key/id를 client가 조작할 수 있는 API 설계(Request에 두지 않음으로 원천 차단), scope가 공개 적용까지 확대되는 문제(NEW/CHANGE 목록에 public 파일 0건으로 명시).
- 검증: `SiteThemeSettingServiceTest`(update 케이스 추가), `AdminThemeControllerTest`, `AdminThemeViewControllerTest`, 기존 `AdminViewControllerTest`(sidebar 링크 목록 갱신), `./gradlew clean build` 전체, Playwright `admin-console-errors.spec.js`(`/admin/theme` 추가), 실제 Docker 재빌드 후 로그인→저장→새로고침 지속성 수동 검증.
- DoD: `GET/PUT /api/admin/theme` 정상 동작(인증/CSRF 보호, 6개 필드 중 하나라도 누락/오류 시 400, row 없음 시 404), `GET /admin/theme` 화면이 공통 admin 레이아웃과 함께 정상 렌더링, 저장한 값이 새로고침 후에도 유지, `getSetting()` fallback이 여전히 DB write 0건, public 화면·기존 완료 이력 무회귀, 기존 테스트 전체 통과(정당한 계약 갱신 제외).
- 구현 결과(P14-T8B, 최종): 위 설계대로 구현했다. **NEW 6개**: `SiteThemeSettingRequest`, `AdminThemeController`, `AdminThemeViewController`, `admin/theme/form.html`, `AdminThemeControllerTest`, `AdminThemeViewControllerTest`. **CHANGE 7개**(계획 대비 2개 추가, 아래 설명): `SiteThemeSetting`(`update()` 메서드 추가), `SiteThemeSettingService`(`updateSetting()` 추가), `ErrorCode`(`SITE_THEME_SETTING_NOT_FOUND` 추가), `admin/layout/sidebar.html`(`/admin/theme` 링크 1개 추가, 기존 9개 순서 무변경), `SiteThemeSettingServiceTest`(update/404/no-row-created 테스트 3건 추가), 그리고 계획에 없었지만 구현 중 실제로 필요했던 2개: (1) `AdminViewControllerTest` — `dashboardRendersSidebarWithAllAdminDomainLinks` 테스트가 sidebar 링크를 `containsExactly(...)`로 정확히 9개 고정 검증하고 있어, `/admin/theme` 추가로 이 계약이 legitimate하게 10개로 바뀌어야 했다(테스트 완화가 아니라 의도된 신규 항목 반영). (2) `frontend-tests/admin-console-errors.spec.js` — 신규 admin 화면 추가 시 `ADMIN_PAGES` 목록에 추가하는 기존 관례(P13-T38A 주석에 명시)를 그대로 따라 `/admin/theme`를 추가했다. **Public 파일 변경 0건**(계획대로 `HomeController`/`home/index.html`/`home.css`/`ThemeControllerAdvice` 어느 것도 만들지 않음 - Admin이 `showGallery=false`를 저장해도 공개 Gallery는 T8D 전까지 계속 그대로 렌더링되는 것이 의도된 단계적 구현이다). **Migration/SecurityConfig/GlobalExceptionHandler/AdminFetch.js 변경 0건**(V13이 이미 모든 컬럼 보유, 기존 `/admin/**`·`/api/admin/**` wildcard 보호와 CSRF 규칙 재사용). `AccentPreset`은 여전히 TERRACOTTA 1개뿐이며 Admin UI는 `AccentPreset.values()` 기반 enum 렌더링으로 T8C 확장에 대비했다. **테스트**: `SiteThemeSettingServiceTest` 5건(기존 3 + 신규 2: `updateSettingPersistsNewAccentPresetAndVisibilityFlags`, `updateSettingWhenRowMissingThrowsNotFoundAndCreatesNoRow`), `AdminThemeControllerTest` 13건(인증 GET/PUT, CSRF 누락 403, accentPreset 누락/오류 400, 5개 boolean 각각 누락 시 400 - parameterized, row 없음 404), `AdminThemeViewControllerTest` 6건(미인증 리다이렉트, view name, Model의 `accentPresets`, 공통 레이아웃, sidebar 링크, form element 존재). `./gradlew clean build` 기준 JUnit **605/605 pass**(기존 584 + 신규 21), failures/errors/skipped 전부 0. Playwright `admin-console-errors.spec.js` 11/11 pass(`/admin/theme` 포함, pageerror 0건) - 실제 Docker `app` 서비스를 재빌드해 검증했다(`db`/`nginx`/`docker-compose.local-test.yml` 무변경). 실제 브라우저로 로그인→`/admin/theme` 진입→초기값 확인(TERRACOTTA+5×true)→`showGallery` 토글→저장→새로고침으로 지속성 확인→원래 값으로 복원까지 수동 검증했고, 1280px에서 가로 overflow 없음과 console/page error 0건도 확인했다(DB는 검증 후 최초 상태로 정확히 복원).

### P14-T8C. Public Theme Accent 적용
- 의존성: P14-T8A, P14-T8B
- 산출물: `theme/entity/AccentPreset.java`(변경), `static/css/home.css`(변경), `home/layout/default.html`(변경), `admin/theme/form.html`(변경), `theme/controller/ThemeControllerAdvice.java`(신규), `theme/controller/ThemeControllerAdviceTest.java`(신규), `theme/service/SiteThemeSettingServiceTest.java`(변경), `common/exception/GlobalExceptionHandlerTest.java`(변경), 본 문서
- 작업 내용: 관리자가 `/admin/theme`에서 저장한 `accentPreset`을 공개 홈페이지에 실제 적용한다. `AccentPreset`을 TERRACOTTA/BURGUNDY/FOREST 3개로 확장하고(enum은 순수 식별자만 유지, 실제 색상/라벨은 View 계층에 둠), `HeaderMenuControllerAdvice`와 정확히 동일한 4개 public View Controller(`HomeController`/`PageViewController`/`ProgramViewController`/`BoardViewController`)에 `@ControllerAdvice(assignableTypes={...})`로 scope를 한정한 신규 `ThemeControllerAdvice`가 `SiteThemeSettingService.getSetting()`을 호출해 `SiteThemeSettingView` 전체를 `siteTheme` model attribute로 공급한다(T8D가 동일 attribute의 visibility 필드를 재사용할 수 있도록 하되, 이번 Task의 public template은 `accentPreset`만 참조한다). `home/layout/default.html`의 `<html>`에 `th:attr="data-theme=${siteTheme.accentPreset}"`을 추가해 SSR 단계에서 FOUC 없이 테마가 적용된다. CSS는 `home.css`의 기존 `:root`(TERRACOTTA 값 그대로 유지)에 `[data-theme="BURGUNDY"]`/`[data-theme="FOREST"]` override 블록을 추가해 accent 계열 3개 변수(`--color-accent`/`--color-accent-text`/`--color-accent-on-dark`)만 재정의한다 - `--color-primary` 등 구조색과 `.section-eyebrow`/`.program-row__number` 같은 component selector는 프리셋과 무관하게 공용으로 유지된다(accent 실사용처가 이 3개 selector뿐임을 repo 전수 검색으로 확인). `data-theme`이 없거나 알 수 없는 값이면 `:root`의 TERRACOTTA가 그대로 유지되어 서버 fallback(row 없음 → TERRACOTTA, DB write 0건, 무변경 유지)과 CSS fallback이 이중 안전장치를 이룬다. Admin form은 `AccentPreset.values()` 순회 구조를 그대로 유지한 채 라벨 삼항식만 2-way(테라코타/raw)에서 3-way(테라코타/버건디/포레스트)로 확장했다.
- 변경 금지: `home/index.html`(section visibility `th:if` 연결은 P14-T8D), `SiteThemeSetting`/`SiteThemeSettingService`/`SiteThemeSettingRequest`/`SiteThemeSettingView`/`AdminThemeController`/`AdminThemeViewController`(T8A/T8B 계약 무변경), `SecurityConfig`/`GlobalExceptionHandler` 로직(테스트의 mock 보강 제외)/`AdminFetch.js`/public JS, migration, `admin.css`/admin layout/sidebar/header(Admin Redesign은 별도 Task).
- 위험: accent 변경이 의도치 않게 넓은 UI를 바꾸는 문제(3개 selector로 실측 확인되어 낮음), BURGUNDY/FOREST의 WCAG 대비 실패(실제 계산으로 전부 통과 확인, 아래 palette 참고), `@WebMvcTest`가 클래스패스의 모든 `@ControllerAdvice`를 강제 로드해 무관한 슬라이스 테스트가 깨지는 문제(`GlobalExceptionHandlerTest`에서 실제 발생, `MenuService`와 동일하게 `SiteThemeSettingService`를 `@MockitoBean`으로 보강해 해결).
- 검증: `SiteThemeSettingServiceTest`(BURGUNDY/FOREST 저장·조회 추가), 신규 `ThemeControllerAdviceTest`, 기존 `GlobalExceptionHandlerTest`(mock 보강), `./gradlew clean build` 전체, Playwright `visual-regression.spec.js`/`public-console-errors.spec.js`/`admin-console-errors.spec.js` 전체, 실제 Docker 재빌드 후 3개 프리셋 저장→Home/Board/Program/Page 전파→1440/900/375 뷰포트→원상 복구까지 수동 검증.
- DoD: `AccentPreset` 3개, 4개 public Controller 전부에서 `data-theme`이 저장값을 정확히 반영, row 없음 시 `data-theme="TERRACOTTA"`+DB write 0건, Admin form이 코드 수정 없이 3개 선택지를 한글로 표시, migration 변경 없음, section visibility는 여전히 public에 미연결, 기존 테스트 전체 무회귀+신규 테스트 통과, 실제 브라우저 검증 완료 후 DB 원상 복구.
- 구현 결과(P14-T8C, 최종): 위 설계대로 정확히 구현했다. **최종 palette**(실제 WCAG 상대휘도 계산): TERRACOTTA(무변경) `#C1652F`/`#96491E`/`#E8A17B`(대비 4.06:1 / 6.43:1 / 5.37:1). BURGUNDY `#B4455B`/`#7A2E3F`/`#E0A0AF`(대비 5.33:1 / 9.17:1 / navy 대비 5.37:1 - 최초 후보 `#D98FA0`은 navy 대비 4.59:1로 마진이 좁아 더 밝은 `#E0A0AF`로 조정해 5:1 이상 확보). FOREST `#4B7355`/`#2F5233`/`#8FBF9B`(대비 5.41:1 / 8.84:1 / navy 대비 5.52:1, 조정 없음). accent 계열 변수 3개만 override되고 `--color-primary` 등 구조색은 전 프리셋에서 동일함을 실측(`getComputedStyle`)으로 확인. **NEW 2개**: `ThemeControllerAdvice`, `ThemeControllerAdviceTest`. **CHANGE 7개**(계획 대비 1개 추가): `AccentPreset`, `home.css`, `admin/theme/form.html`, `home/layout/default.html`, `SiteThemeSettingServiceTest`, 본 문서, 그리고 계획에 없었던 `GlobalExceptionHandlerTest` — `@WebMvcTest`가 controllers 필터와 무관하게 클래스패스의 모든 `@ControllerAdvice`를 로드하는 Spring Boot 표준 동작 때문에(기존 `HeaderMenuControllerAdvice`+`MenuService`와 동일한 문제) 신규 `ThemeControllerAdvice`의 `SiteThemeSettingService` 의존성을 `@MockitoBean`으로 보강해야 했다(테스트 완화가 아니라 컨텍스트 기동을 위한 필수 보강). **Public 적용 범위**: `accentPreset`만 반영, `home/index.html` 무변경, 5개 `show*` 값은 `siteTheme` model에 존재하되 어떤 public template도 참조하지 않음(T8D 선행 구현 없음, repo 전체 diff에서 `showPinned` 등 문자열이 새 public 코드에 등장하지 않음을 확인). **Migration/SecurityConfig/AdminFetch.js/public JS/admin.css 변경 0건**. **테스트**: `SiteThemeSettingServiceTest` 7건(기존 5 + 신규 2: BURGUNDY/FOREST 저장·조회), `ThemeControllerAdviceTest` 5건(4개 Controller 전부 model 확인, Admin 미적용, BURGUNDY/FOREST/row-없음 render 확인). `./gradlew clean build` 기준 JUnit **612/612 pass**(기존 605 + 신규 7), failures/errors/skipped 전부 0. Playwright: `visual-regression.spec.js` 235/235 pass(ADMIN 자격 증명 포함 전체 실행, 스크린샷 스냅샷 없음), `public-console-errors.spec.js` 6/6 pass, `admin-console-errors.spec.js` 11/11 pass - 기존 assertion 완화 없음, 신규 스냅샷 없음. 실제 Docker `app` 재빌드 후 TERRACOTTA/BURGUNDY/FOREST 3개 프리셋 각각 저장→Home에서 `data-theme`/`--color-accent`/`--color-accent-text`/`--color-accent-on-dark`/`.section-eyebrow`/`.section--dark .section-eyebrow`/`.program-row__number` computed 값 실측→Board/Program/Page 공통 전파 확인(BURGUNDY)→1440/900/375에서 overflow 없음+console/page error 0건 확인. 시각 검증 결과 세 프리셋이 명확히 구분되고(주황/와인레드/숲녹색), navy primary와 충돌 없이 Editorial/Academic 방향이 유지됨을 스크린샷으로 확인. 테스트 시작 전 실제 DB 값(`TERRACOTTA`+5×`true`)을 API로 기록해두고, 검증 종료 후 정확히 그 값으로 복원하여 재조회로 재확인했다.

### P14-T8D. Public Home Section Visibility 적용
- 의존성: P14-T8A, P14-T8B, P14-T8C
- 산출물: `home/index.html`(변경), `home/controller/HomeControllerTest.java`(변경), `frontend-tests/visual-regression.spec.js`(변경), 본 문서
- 작업 내용: 관리자가 `/admin/theme`에서 저장한 5개 visibility(`showPinned`/`showPrograms`/`showReviews`/`showNotices`/`showGallery`)를 실제 Public Home section 렌더링에 연결한다. **View-only 방식**을 채택해 `HomeController`(데이터 조회 무변경)/`ThemeControllerAdvice`(T8C에서 이미 `siteTheme` 전체를 공급 중이라 무변경) 어느 것도 건드리지 않고, `home/index.html`의 5개 `<section>` 최상위 wrapper에만 `th:if` 게이트를 추가했다. Pinned는 기존 0건 조건(`pinnedContents != null and !pinnedContents.isEmpty()`)에 `siteTheme.showPinned`를 AND로 추가(P13-T38 "0건이면 미렌더" 계약 그대로 보존). Programs/Reviews/Notices/Gallery는 기존에 section-level 조건 자체가 없던 자리(0건이어도 heading+empty-state가 항상 보이는 기존 정책)에 `siteTheme.showX` 게이트만 신규로 추가했고, 그 안쪽의 목록/empty-state 분기는 한 글자도 수정하지 않았다 - 따라서 showX=true+0건은 기존과 동일하게 "section+empty-state"가 보이고, showX=false는 그 로직에 도달하기도 전에 section 자체가 SSR 단계에서 생성되지 않는다. Hero(`#banners`)/Popup(`#popups`)은 이 기능과 완전히 독립이며 전혀 건드리지 않았다. `home.css`의 `.section { padding: ... }`가 margin-collapse가 아니라 각 section 자신의 padding으로 리듬을 만드는 구조임을 코드 조사로 확인해, 중간 section(특히 dark 배경인 Reviews)이 제거돼도 CSS 수정 없이 앞뒤 section이 자연스럽게 이어짐을 실측으로 재확인했다.
- 변경 금지: `HomeController`/`ThemeControllerAdvice`/`SiteThemeSetting`/`SiteThemeSettingView`/`SiteThemeSettingRequest`/`SiteThemeSettingService`/`AccentPreset`/`AdminThemeController`/`AdminThemeViewController`/`admin/theme/form.html`/`home/layout/default.html`/`home.css`/`SecurityConfig`/`GlobalExceptionHandler`/production Repository/migration/public JS/`admin.css`(Admin Redesign은 별도 Task) - 전부 실제로 무변경.
- 위험: visibility 테스트가 남긴 `site_theme_setting` row가 같은 클래스의 다른 테스트로 새어 들어가는 테스트 격리 문제(실제로 4건의 기존 테스트가 실패로 재현됨 - 아래 참고), Reviews(dark section) 제거 시 레이아웃 이음새 문제(CSS 조사+실측 결과 없음), all-false 상태에서의 예기치 않은 breakage(없음, 신규 validation 도입하지 않음).
- 검증: `HomeControllerTest`(신규 visibility 테스트 7건), `frontend-tests/visual-regression.spec.js`(신규 `P14-T8D` describe 10건), `./gradlew clean build` 전체, Playwright `visual-regression.spec.js`/`public-console-errors.spec.js`/`admin-console-errors.spec.js` 전체, 실제 Docker 재빌드 후 개별 OFF 5종+all-ON/OFF+관리자 UI end-to-end까지 수동 검증.
- DoD: 5개 showX=false 각각이 대응 section을 SSR 미렌더링, true+기존 데이터 조건 만족 시 정상 렌더(Pinned는 0건 미렌더, 나머지 4개는 0건이어도 empty-state 유지), row 없음 fallback 시 5개 전부 렌더+DB write 0건, all-false 정상 동작, Hero/Popup/accent/direct URL/menu/publication 정책 영향 없음, migration 없음, console/page error 0, responsive 회귀 없음, 검증 후 SITE_THEME 원복, 기존 테스트 전체 무회귀.
- 구현 결과(P14-T8D, 최종): 위 설계대로 정확히 구현했다. **CHANGE 4개**(예상과 정확히 일치, NEW 0개): `home/index.html`(5개 section에 `th:if` 추가), `HomeControllerTest.java`(신규 visibility 테스트 7건 + `SiteThemeSettingRepository` 의존성 추가), `frontend-tests/visual-regression.spec.js`(`P14-T8D` describe 10건 추가), 본 문서. **테스트 격리 문제 실제 발견 및 해결**: 신규 visibility 테스트가 `saveTheme()`으로 남긴 row가 JUnit 실행 순서상 뒤에 오는 기존 테스트로 새어 들어가 `homePinnedSectionShowsVisiblePublicBoardAndProgramInSortOrderThenId` 등 4건이 실제로 실패하는 것을 확인했다 - "실제 격리 문제를 확인했을 때만 공통 `@BeforeEach` 변경을 허용"하는 기준에 따라 `setUp()`에 `siteThemeSettingRepository.deleteAll()`을 추가했다(row 없으면 fallback이 5개 전부 true라 기존 테스트가 가정하는 "전 section 표시 가능" 전제와 완전히 동일 - 기존 30여 개 테스트는 이 변경으로 결과가 전혀 달라지지 않음을 재실행으로 확인). **Java**: `HomeControllerTest` 45/45 pass(기존 38 + 신규 7: 개별 OFF 5건, all-false 1건, row-없음 fallback-render 1건), 전체 `./gradlew clean build` 기준 JUnit **619/619 pass**(기존 612 + 신규 7), failures/errors/skipped 전부 0. **Playwright**: 신규 `P14-T8D` 10건(개별 OFF 5, all-false 1, 900/375 all-on↔all-off 전환 2, accent 무회귀 1, 관리자 UI end-to-end 1) 전부 pass, 기존 `visual-regression.spec.js` 전체 235/235 pass(무회귀), `public-console-errors.spec.js` 6/6, `admin-console-errors.spec.js` 11/11 - 기존 assertion 완화 없음, 대량 스냅샷 생성 없음. **실제 브라우저 검증**: Docker `app` 재빌드 후 1440px에서 개별 OFF 5종+all-ON+all-OFF 스크린샷 확인 - Reviews(dark) 제거 시 Programs→Notices가 이음새 없이 자연스럽게 이어짐, all-OFF는 Header→Footer만 남고 깨짐 없음을 육안 확인. 900px/375px에서 all-ON↔all-OFF 전환 시 가로 overflow/console/page error 전부 0건. `html[data-theme]`이 visibility만 바꿔도 기존 accentPreset(TERRACOTTA)을 그대로 유지함을 확인(accent 무회귀). 검증 시작 전 실제 DB 값(TERRACOTTA+5×true)을 기록하고, Playwright `afterAll`과 수동 스크립트 양쪽에서 각각 정확히 그 값으로 복원 후 재조회로 재확인했다. **Migration 변경 없음**, `HomeController`/`ThemeControllerAdvice`/Admin 계층/`home.css`/`home/layout/default.html` 전부 실제 diff 0건.

### P14-T9A. Admin Critical UX Fix
- 의존성: P14-T9(Admin UI/UX Redesign READ-ONLY 조사, Critical 4항목 도출)
- 산출물: `admin/controller/AdminNavigationControllerAdvice.java`(신규), `admin/controller/AdminNavigationControllerAdviceTest.java`(신규), `static/js/admin/admin-layout.js`(신규), `frontend-tests/admin-layout.spec.js`(신규), `admin/layout/sidebar.html`(변경), `admin/layout/header.html`(변경), `admin/layout/default.html`(변경), `static/css/admin/admin.css`(변경), `admin/board/list.html`(변경), `admin/program/list.html`(변경), `admin/menu/list.html`(변경), `admin/controller/AdminViewControllerTest.java`(변경), `frontend-tests/admin-console-errors.spec.js`(변경), 본 문서
- 작업 내용: P14-T9 조사에서 발견한 4개 Critical 항목만 구현한다. **(1) Logout UI**: 기존 `POST /api/admin/logout` 계약을 그대로 사용해 header에 `#admin-logout-button`을 추가하고, 기존 `AdminFetch.adminFetch()`(CSRF 쿠키→헤더 자동 변환)로 호출한다. 성공 시 `/admin/login`으로 이동, 실패 시 이동하지 않고 버튼 재활성화 + `window.alert`로 실패를 알린다(신규 toast 시스템 도입 안 함). **(2) Sidebar Active Navigation**: 후보 A(Thymeleaf `#httpServletRequest`)를 실제로 구현해 `AdminViewControllerTest`로 검증한 결과 `admin/layout/sidebar.html`이 `th:replace`로 include되는 fragment 안에서는 `#httpServletRequest` 자체가 null로 평가되어(`SpelEvaluationException: EL1007E ... 'requestURI' cannot be found on null`) 18/19 테스트가 즉시 실패하는 것을 실측으로 확인했다 - template hack을 시도하지 않고 사전 합의된 대로 후보 C(Admin 전용 `@ControllerAdvice`)로 전환했다. 신규 `AdminNavigationControllerAdvice`가 `HeaderMenuControllerAdvice`/`ThemeControllerAdvice`와 동일한 `@ControllerAdvice(assignableTypes={...})` 패턴으로 정확히 기존 10개 Admin View Controller에만 scope되어 `HttpServletRequest.getRequestURI()`를 `currentAdminPath` model attribute로 공급하고, `sidebar.html`의 10개 `<a>`는 href/label/순서를 전혀 바꾸지 않은 채 `th:classappend`로 `is-active`만 추가한다(대시보드는 정확히 일치, 나머지는 prefix 일치 - `/admin/programs/new` 등 하위 경로에서도 부모 메뉴가 active 유지). **(3) Mobile Off-canvas Sidebar**: Bootstrap 자체 `md` breakpoint 값인 767.98px를 그대로 재사용해 그 이하에서만 `#admin-sidebar`를 `position:fixed`+`transform:translateX(-100%)`로 감추고 `.is-open`으로 노출한다. `d-md-none` 유틸리티로 숨겨지는 `#admin-sidebar-toggle`(`aria-controls="admin-sidebar"`/`aria-expanded`/`aria-label`)이 신규 `admin-layout.js`(UMD, 기존 `AdminFetch`/`AdminHeader`와 동일 모듈 패턴)의 클릭 핸들러로 열고 닫으며, `Escape` 키로도 닫힌다. 새 아이콘 라이브러리 없이 텍스트 "☰"만 사용, backdrop은 복잡도 대비 낮은 가치로 생략했다. **(4) Board/Program/Menu 목록 반응형 테이블**: 세 화면의 기존 `<table>`을 `<div class="table-responsive">`로 감싸기만 했다(컬럼/데이터/필터/검색/페이지네이션/액션 무변경). 이 과정에서 Menu(7컬럼) 목록만 375px에서 페이지 전체가 107px 가로로 밀리는 실제 버그를 Playwright로 발견했다 - `<main class="flex-grow-1 p-4">`가 `.d-flex`의 flex item으로서 기본값 `min-width:auto` 때문에 넓은 테이블의 intrinsic 폭만큼 스스로 넓어져 `.table-responsive`의 `overflow-x:auto`를 완전히 무력화하는 구조적 원인이었다(Board/Program은 컬럼이 적어 우연히 드러나지 않았을 뿐 동일 원인). 767.98px 이하 media query 안에 `main { min-width: 0; }`를 추가해 해결했다(desktop에는 이 규칙 자체가 적용되지 않음).
- 변경 금지: migration/`SecurityConfig`/`AdminAuthController`/Logout API 계약/public 파일/Theme Settings 도메인/CKEditor/Admin URL 구조/Bootstrap 버전·번들 추가/외부 프론트엔드 라이브러리/React·Vue 도입/카드형 레이아웃 전면 재작성/sidebar 그룹핑 착수/Dashboard 재설계 착수/badge·empty-state 전면 정리 착수/기존 테스트 삭제·완화/active-nav를 위한 Service·Repository 변경 - 전부 실제로 무변경. Sidebar IA(10개 flat link의 href/label/순서)도 T9B 몫으로 남겨 이번 Task에서는 전혀 바꾸지 않았다.
- 위험: `#httpServletRequest`가 fragment include 안에서 지원되지 않아 template만으로 active-nav를 구현할 수 없는 문제(실측으로 확인, Admin 전용 ControllerAdvice로 해결), `.table-responsive` wrapper만으로는 flex 부모(`<main>`)의 `min-width:auto` 때문에 컬럼이 많은 테이블에서 페이지 전체가 밀리는 문제(Menu 목록에서 실측으로 발견, `main{min-width:0}`으로 해결), 변경 파일 수가 사전 고지된 "9~13개 정상, 14개 이상 Stop Condition" 기준의 상한에 걸치는 문제(아래 최종 결과 참고, 판단은 사용자에게 보고).
- 검증: `AdminNavigationControllerAdviceTest`(신규 2건), `AdminViewControllerTest`(신규 active-nav 13-case parameterized + logout/toggle 존재 1건), `./gradlew clean build` 전체, 신규 `frontend-tests/admin-layout.spec.js`(14건: active-nav 6 + desktop 1 + mobile sidebar 2 + responsive table 3 + logout 2), `admin-console-errors.spec.js`(`/admin/pages` 커버리지 추가 후 전체), `visual-regression.spec.js` 전체(무회귀 확인), 실제 Docker 재빌드 후 375/767/768/900/1440 뷰포트+CKEditor+Theme Settings 화면+키보드 접근성 수동 검증, `git diff --check`.
- DoD: 4개 항목 각각의 DoD를 모두 만족(로그아웃 성공/실패/버튼 재활성화, 10개 항목 href/label/순서 무변경+정확히 1개만 active, 767.98px 경계로 desktop/mobile 분리+토글 aria 동기화+Escape 동작, Board/Program/Menu 페이지 레벨 overflow 0이며 `.table-responsive` 내부 스크롤은 허용), 기존 테스트 전체 무회귀, T9B~T9E 미착수, commit/push/PR/merge 없이 작업 트리에만 결과 존재.
- 구현 결과(P14-T9A, 최종): 위 설계대로 정확히 구현했다. **NEW 4개**: `AdminNavigationControllerAdvice`, `AdminNavigationControllerAdviceTest`, `admin-layout.js`, `admin-layout.spec.js`. **CHANGE 9개**: `sidebar.html`, `header.html`, `default.html`, `admin.css`, `board/list.html`, `program/list.html`, `menu/list.html`, `AdminViewControllerTest`, `admin-console-errors.spec.js`. **본 문서 추가로 총 변경 파일 14개** - 사전 고지된 "14개 이상이면 Stop Condition" 기준과 정확히 같은 수치다. 다만 14개 전부가 이번 지시 자체가 사전 승인한 범위와 1:1로 대응한다: `#httpServletRequest`가 실측으로 지원되지 않아 명시적으로 사전 허용된 "+2 파일(Admin 전용 ControllerAdvice+test)" 옵션 C가 트리거된 것, 나머지는 4개 기능 각각에 필요한 최소 파일(JS 1 + 그 test 1 + list 템플릿 3 + layout 템플릿 3 + CSS 1 + 기존 테스트 보강 2)이며 신규 패키지/Entity/API는 0건이다 - 그럼에도 정확히 임계값에 도달했으므로 이를 그대로 진행 완료로 볼지, Stop Condition으로 처리해 되돌릴지는 사용자 판단에 맡긴다(자체적으로 결정하지 않음). **Active Navigation**: 후보 A `#httpServletRequest`를 fragment 안에서 실제로 시도 → `AdminViewControllerTest` 18/19 실패(`EL1007E: ... 'requestURI' cannot be found on null`)로 실측 확인 → template hack 없이 후보 C(Admin 전용 ControllerAdvice)로 전환 → 재실행 19/19 pass. **Logout**: 기존 API 계약/CSRF 방식 무변경, 성공 시 `/admin/login` 이동 후 재접근 차단까지 확인, 실패(500 mock) 시 이동 없음+버튼 재활성화+alert 확인. **Mobile Sidebar**: 767px(off-canvas, toggle 노출)/768px(정적, toggle 숨김) 경계 실측 확인, `aria-expanded` toggle 클릭/Escape/키보드(Tab+Enter) 전부 동기화 확인. **Responsive Table**: Board/Program은 최초 구현부터, Menu는 `main{min-width:0}` 추가 후 375px 페이지 레벨 overflow 0(이전 107px) 확인, 세 화면 모두 `.table-responsive` 내부 스크롤만 존재. **Java**: `./gradlew clean build` 기준 JUnit **635/635 pass**, failures/errors/skipped 전부 0. **Playwright**: `admin-layout.spec.js` 14/14 pass(신규), `admin-console-errors.spec.js` 12/12 pass(`/admin/pages` 추가 후, 무회귀), `visual-regression.spec.js` 245/245 pass(1건은 전체 실행 중 `page.goto` 30s 타임아웃으로 1차 실패했으나 T9A가 전혀 건드리지 않은 Program 수정 폼 라운드트립 테스트였고 단독 재실행 시 즉시 pass해 flaky로 판단, 실제 회귀 아님). **수동 브라우저 검증**: CKEditor `/admin/boards/new`에서 정상 로드(`.ck-editor` 1개), `/admin/theme` 화면 overflow 0+form 정상 렌더로 완전 무영향 확인, active/inactive sidebar 항목의 `background-color`/`font-weight`/`border-left` computed 값이 실제로 다름을 확인, 실제 키보드 Tab으로 logout 버튼에 도달 시 `:focus-visible` matches true + box-shadow 포커스 링 확인. **SITE_THEME**: 검증 전후 모두 `{"accentPreset":"TERRACOTTA","showPinned":true,"showPrograms":true,"showReviews":true,"showNotices":true,"showGallery":true}`로 무변경 확인. **`docker-compose.local-test.yml`**: 전 과정에서 `git status` 상 `??`(untracked)로만 존재, 수정/삭제/스테이징 없음. **`git diff --check`**: 통과(공백 오류 0건). **commit/push/PR/merge**: 수행하지 않음, T9B~T9E 미착수, 결과는 `feature/p14-t9a-admin-critical-ux` working tree에만 존재.

### P14-T9B. Admin Visual Foundation
- 의존성: P14-T9A(Admin Critical UX Fix - logout/active navigation/mobile off-canvas sidebar/Board·Program·Menu responsive table)
- 산출물: `static/css/admin/admin.css`(변경), `admin/layout/default.html`(변경), `admin/layout/header.html`(변경, 3차 보강), `admin/layout/sidebar.html`(변경), `admin/login.html`(변경), 본 문서
- 작업 내용: 기존 Bootstrap 5.3.3 구조와 T9A의 4개 기능(logout/active-nav/mobile sidebar/responsive table)을 그대로 유지한 채, Admin 전체에 Modern Editorial CMS 방향의 공통 시각 기반(token/typography/surface hierarchy/component foundation)을 구축한다. **Admin 전용 토큰(11개)**: `--admin-bg`(#F8F6F2)/`--admin-surface`(#FFFFFF)/`--admin-surface-muted`(#F0EEE8)/`--admin-text`(#22262B)/`--admin-text-muted`(#60666D)/`--admin-border`(#E1DFD7)/`--admin-primary`(#3A5169)/`--admin-primary-hover`(#2C3E52)/`--admin-danger`(#B3392C)/`--admin-focus`(rgba(58,81,105,.35))/`--admin-radius`(8px) - Public(`home.css`/`SiteThemeSetting`/`AccentPreset`/`data-theme`)과 이름·값 모두 독립이라 관리자가 Public accent를 TERRACOTTA/BURGUNDY/FOREST 중 무엇으로 바꿔도 Admin 색상은 전혀 바뀌지 않는다. **Typography**: Public(`home/layout/default.html`)이 이미 쓰는 것과 정확히 동일한 Pretendard Variable CDN(jsDelivr, v1.3.9 고정, dynamic subset)을 `admin/layout/default.html`과 `admin/login.html`에 재사용(신규 CDN/의존성 없음). 20개 template의 heading semantic(h2=목록 제목/h3=폼·로그인 제목/h4·legend.h5=섹션 제목)은 이미 일관됨을 조사로 확인해 markup은 그대로 두고 CSS로만 고정 크기(h2 1.625rem/700, h3 1.375rem/700, h4·h5.h5·legend.h5 1.05rem/600)로 정돈했다. **Surface hierarchy**: body/`main` 배경을 warm-neutral(`--admin-bg`)로, header/sidebar/table 배경을 white(`--admin-surface`)로 분리해 카드형 markup 추가 없이 CSS만으로 위계를 만들었다(폼 input/alert는 Bootstrap 기본값이 이미 white라 무변경으로도 surface로 읽힘). **Header/Sidebar**: T9A의 DOM 계약(`#admin-header`/`#admin-sidebar-toggle`/`#admin-logout-button`/active-nav class)은 전혀 건드리지 않고 배경/border/색만 토큰으로 교체했다. **Sidebar visual grouping**: 기존 10개 `<a>`의 href와 순서는 전혀 바꾸지 않고(`AdminViewControllerTest`가 href 순서를 그대로 검증) 링크 "사이"에 비-클릭 `<span class="admin-sidebar__group-label">`만 삽입했다 - 조사 단계가 제안한 "홈 화면(배너/팝업/메인 고정/홈페이지 디자인)"/"사이트 관리(메뉴/파일)" 3그룹 안은 실제 href 순서(배너→팝업→**파일→메뉴**→메인 고정→홈페이지 디자인)와 맞지 않아(파일/메뉴가 배너/팝업과 메인 고정/홈페이지 디자인 사이에 끼어 있음) 링크 재배치 없이 4그룹(콘텐츠/사이트 노출/사이트 관리/홈 화면)으로 재구성했다. **Button/Form/Table/Badge/Alert/CKEditor foundation**: Bootstrap class semantics(`btn-primary`/`btn-outline-*`/`form-control`/`table`/`badge`/`alert`) 그대로 유지, admin.css override만으로 radius/색/hover/focus를 정돈했다(markup 전수 교체 없음, plain text→badge 전환·empty state·컬럼 재설계는 T9C로, required marker·action footer는 T9D로 명시적으로 미룸). CKEditor는 `.ck-editor__editable`의 border/focus 색만 토큰으로 치환했고 JS/config/plugin/upload adapter/resize/sanitizer는 0건 변경. **Login**: 공통 layout을 쓰지 않는 독립 화면이라 동일 Pretendard `<link>`와 `.admin-login-card`(white surface) class만 추가했다(인증 로직/form action/API/CSRF 무변경). **Main flex overflow foundation 수정**: T9A가 375~767.98px 전용으로 두었던 `main{min-width:0}`을 breakpoint와 무관한 Admin shell 공통 규칙으로 일반화했다 - 실측 결과 Board 등록/수정 폼(CKEditor 포함)이 정확히 T9A의 Menu 375px 문제와 동일한 원인(`<main>`이 `.d-flex`의 flex item이라 기본값 `min-width:auto` 때문에 CKEditor 툴바의 넓은 intrinsic 폭에 밀림)으로 768px 데스크톱 폭에서 116px 페이지 레벨 overflow를 일으키는 것을 발견해 함께 해결했다(부수효과로 HomePinned 375px의 기존 12px overflow도 0으로 해소됨을 확인 - 의도한 조치는 아니며 `.table-responsive` 미적용은 T9C로 그대로 남김).
- 변경 금지: migration/Backend(Controller·Service·Repository·Entity·`SecurityConfig`·`AdminAuthController`·`AdminNavigationControllerAdvice`)/API 계약/Public 파일(`home.css`/public template·Controller·JS/`SiteThemeSetting`/`AccentPreset`/`ThemeControllerAdvice`)/CKEditor JS·config·plugin/신규 JS(`admin-layout.js` 포함)/Bootstrap 제거·버전 변경·JS 번들 추가/신규 아이콘 라이브러리/신규 CSS 파일/T9C(raw enum 한글화·badge 전환·empty state·delete feedback 통일·컬럼 재설계)·T9D(required marker·section grouping·action footer)·T9E(dashboard KPI·quickMenus·구성 변경) 착수/sidebar href 순서 변경 - 전부 실제로 무변경.
- 위험: sidebar grouping 제안이 실제 href 순서와 맞지 않아 재구성이 필요했던 문제(링크 재배치 금지 원칙을 우선해 4그룹으로 해결, href/순서 자체는 무변경), `.form-control:focus` 등 focus 의존 시각 검증 시 Playwright 스크립트가 여러 context/navigation을 연속 수행하면 `document.hasFocus()`가 false로 남아 focus 전용 CSS가 실제로는 정상 동작함에도 오탐지되는 테스트 방법론적 함정(실제 원인 규명 후 `page.bringToFront()`+CSS transition 대기로 해결, 프로덕션 결함 아님), Board form 768px overflow 수정이 다른 화면에 부작용을 주지 않는지 확인 필요(1440/900/768/767/375 전부 재확인 완료, 부작용 없음).
- 검증: `./gradlew clean build` 전체(무회귀, 테스트 파일 변경 0건), `frontend-tests/admin-layout.spec.js`(T9A 14건 전체 재검증), `admin-console-errors.spec.js` 전체, `visual-regression.spec.js` 전체, 실제 Docker 재빌드 후 375/767/768/900/1440 뷰포트 + Dashboard/Board list/Board form/Program list/Menu list/HomePinned/Theme Settings/Login 화면 수동 검증, 실제 WCAG 대비 재계산, 키보드 접근성(logout/mobile toggle/form input/checkbox 실제 포커스) 검증, `git diff --check`.
- DoD: Admin 토큰 11개 존재+Public 완전 독립, Pretendard 적용, typography/surface hierarchy 개선, header/sidebar visual foundation+grouping 적용(href/순서 무변경), button/form/table/badge/alert foundation 적용, login 시각 통일, CKEditor 기능 무변경+주변 시각만 토큰 적용, Board form 768px overflow 0, T9A 4개 기능(logout/active-nav/mobile sidebar/responsive table) 무회귀, 375/767/768/900/1440 정상, 주요 contrast 4.5:1 이상, Java·Playwright 전체 PASS, console/pageerror 0, Migration·Backend·Public·JS 변경 0건, T9C~T9E 미착수, `docker-compose.local-test.yml` 무변경.
- 구현 결과(P14-T9B, 최종): 위 설계대로 정확히 구현했다. **CHANGE 4개**(신규 0개, 목표 "production 5개 이내" 대비 여유 있게 달성 - header.html은 ID selector만으로 스타일링이 가능해 markup 변경이 불필요했다): `admin.css`, `admin/layout/default.html`, `admin/layout/sidebar.html`, `admin/login.html`. 본 문서 추가로 **총 변경 파일 5개**(목표 "전체 8개 이하"에 여유 있게 부합). **테스트 변경 0건** - sidebar grouping이 href 순서를 그대로 보존해 `AdminViewControllerTest`가 무수정으로 통과했다(실제 재확인). **실제 최종 contrast**(구현된 HEX 기준 재계산, 전부 4.5:1 이상): text/surface 15.22:1, text/bg 14.10:1, muted/surface 5.80:1, muted/bg 5.38:1, white/primary(버튼) 8.20:1, white/primary-hover 10.95:1, white/danger(버튼) 5.93:1, active-nav-text(primary)/active-bg(surface-muted) 7.07:1. **Board form 768px overflow**: 수정 전 116px → 수정 후 **0px**(CKEditor 정상 로드 확인). **HomePinned 375px overflow**: 12px → **0px**(우연히 해소, T9C `.table-responsive` 적용 필요성은 그대로 유지). **T9A 회귀 검증**: `admin-layout.spec.js` 14/14 pass(active-nav 6/desktop 1/mobile sidebar 2/responsive table 3/logout 2 - 전부 T9A 원본 그대로 무수정 재사용), 767px(off-canvas, toggle 노출)/768px(정적, toggle 숨김)/900px/1440px 전부 정상. **Java**: `./gradlew clean build` 기준 JUnit **635/635 pass**(T9A와 동일 수치, failures/errors/skipped 전부 0 - 신규/변경 테스트 없음 자체가 곧 무회귀 증거). **Playwright**: `admin-console-errors.spec.js` 12/12 pass, `visual-regression.spec.js` 245/245 pass(전체 실행 중 완전히 무관한 public 페이지 2건이 `page.goto` 30s 타임아웃으로 1차 실패했으나 - Hero 배너 재생 버튼, NOTICE 필터 - 둘 다 T9B가 전혀 건드리지 않는 public 화면이고 단독 재실행 시 즉시 pass해 flaky로 판단, 실제 회귀 아님). **수동 브라우저 검증**: Pretendard 적용 확인(`getComputedStyle(document.body).fontFamily`에 "Pretendard Variable" 확인), sidebar group label 4개("콘텐츠"/"사이트 노출"/"사이트 관리"/"홈 화면") 렌더링 확인, href 순서 원본과 100% 일치 확인, active nav 색상(`--admin-primary`)/배경(`--admin-surface-muted`) computed 값 확인, `btn-primary` 배경 `--admin-primary`+radius 8px 확인, `table`/`thead` 배경 토큰 확인, Login 화면 1440/375 overflow 0+카드 배경 white+Pretendard 적용 확인, CKEditor `/admin/boards/new`에서 정상 로드 확인, Theme Settings 화면 완전 무영향 확인. **접근성**: 실제 키보드 Tab으로 도달한 logout 버튼의 `:focus-visible` box-shadow가 `--admin-focus` 링으로 정상 렌더(진단 중 발견한 `document.hasFocus()`/CSS transition 타이밍 함정을 제어한 뒤 재확인), form input/checkbox의 실제 클릭 focus에서 border-color/box-shadow가 `--admin-primary`/`--admin-focus`로 정상 전환 확인, mobile toggle 키보드 focus 확인. **Migration/Backend/Public/JS 변경 0건**(`admin-layout.js`/`AdminNavigationControllerAdvice` 포함 전부 diff 없음 확인). **`docker-compose.local-test.yml`**: 전 과정 `??`(untracked) 유지. **`git diff --check`**: 통과. **commit/push/PR/merge**: 수행하지 않음, T9C~T9E 미착수, 결과는 `feature/p14-t9b-admin-visual-foundation` working tree에만 존재.
- 구현 결과(2차 보강 - Sidebar Visual Hierarchy + Bootstrap 탈피, 최종): 사용자가 실제 화면을 확인한 결과 "여전히 Bootstrap 기본 관리자 화면처럼 보인다"는 피드백을 받아 admin.css만 추가로 대폭 보강했다(production 여전히 4개, header.html 추가 불필요 - `#admin-header`/`#admin-sidebar` 등 기존 ID selector만으로 전부 해결됨). **1차 Sidebar hierarchy 보완**: group label(font-size 0.75rem/weight 700/muted color/letter-spacing 0.03em)과 실제 링크(그룹 소속 9개만 `:not(:first-child)`로 선택해 padding-left 1.5rem 들여쓰기, 대시보드는 0.75rem 유지) 사이 시각 차이를 명확히 하고, hover(옅은 중립 tint)/focus-visible(hover+`--admin-focus` 링)/active(font-weight 700 강화)의 3단계 위계를 재조정했다 - 10개 링크 전부 375~1440px에서 정확히 40px 높이로 균일해(wrap 없음) 실측 확인. **2차 Bootstrap 탈피**: 사용자가 지정한 7개 Bootstrap 기본색(`#0d6efd`/`#6c757d`/`#dc3545`/`#198754`/`#f8f9fa`/`#e9ecef`/`#dee2e6`)에 `#ffc107`(warning)/`#0dcaf0`(info)를 더해 1440px 기준 11개 전체 Admin 화면(로그인 포함)의 **실제 computed style**을 전수 스캔한 결과, 처음에는 disabled pagination 버튼(Bootstrap `.btn:disabled`가 `.btn-outline-secondary`보다 selector 특정성이 높아 기존 override를 무시), Dashboard quickMenus/File 목록의 plain `<a>`(버튼 class 없는 링크는 Bootstrap 기본 link blue 그대로), Menu/HomePinned의 기존 badge(`text-bg-success/secondary/primary/info`), table의 border-top/left/right(bottom만 override했었음), login의 `.text-danger` 에러 텍스트에서 실제 Bootstrap 원색이 발견됐다. 전부 실측 기반으로 수정: `:disabled` 상태별 override 추가, `main a`/`#admin-footer a` link color 추가, **신규 토큰 3개**(`--admin-success` #3F7D52/`--admin-warning` #C9963D/`--admin-info` #3C6E8F, 총 14개, 15개 이내) + Bootstrap이 `.text-bg-*`/`.alert-*`에서 실제로 읽는 `--bs-primary-rgb`/`--bs-secondary-rgb`/`--bs-success-rgb`/`--bs-warning-rgb`/`--bs-info-rgb`/`--bs-danger-rgb`와 `--bs-success/danger-text-emphasis/bg-subtle/border-subtle`(Bootstrap 5.3 "subtle" 알림 변수)를 `:root`에서 재정의(공식 문서화된 Bootstrap 커스터마이징 지점, markup/class는 전부 무변경, 의미는 success=success/danger=danger로 유지, 색만 절제) + `.table`의 border-color를 4면 전체로 확장 + `.text-danger`를 `--admin-danger`로. **재스캔 결과 11개 화면 전체에서 지정된 8개 Bootstrap 기본색 hit 0건**(수정 전 다수 발견 → 수정 후 0). **Login 재설계**: `.admin-login-card`에 브랜드 한 줄("모니카영어교육연구소") 추가(순수 markup, 인증/API/CSRF 무변경), radius 8→12px, 옅은 shadow(`0 1px 3px rgba(34,38,43,.06)`) 추가, 패널 폭은 실측(376px 렌더 폭, 400~460px 권장 범위 내) 결과 200px sidebar와 마찬가지로 이미 적정해 조정하지 않음. **Header**: 브랜드 텍스트 크기 확대(1.0625rem)+관리자명을 `--admin-surface-muted` pill로 감싸 "Admin shell" identity를 강화했다(markup 무변경, id/class/JS contract 그대로). **Sidebar 폭**: 실측 결과 200px에서 최장 라벨 2개를 포함한 10개 링크 전부가 wrap 없이 정확히 동일한 높이를 유지함을 재확인해(desktop/mobile 동일) 변경하지 않기로 결정 - "실제 rendering 기준으로 결정, 무조건 넓히지 않는다"는 원칙에 따른 근거 있는 유지 결정. **회귀 재검증**: `admin-layout.spec.js` 14/14 pass(T9A 4개 기능 전부 무회귀), `admin-console-errors.spec.js` 12/12 pass, `visual-regression.spec.js` 245/245 pass(1차와 마찬가지로 전체 실행 중 T9B와 무관한 public 화면 2건이 시스템 부하로 인한 `page.goto` 타임아웃으로 flaky 발생했으나 무관), Board form 768px overflow 0/HomePinned 375px overflow 0 유지, `./gradlew clean build`는 markup 변경(login.html 브랜드 span 추가)이 순수 presentation이라 View test 영향 없음을 확인(테스트 파일 변경 0건 유지). **Screenshot 기반 최종 시각 판단**: Login/Dashboard/Board list/Board form/Theme Settings/Mobile sidebar 6개 화면을 실제 스크린샷으로 확인한 결과 - group hierarchy 즉시 인식 가능, active 항목이 sidebar에서 가장 먼저 눈에 띔, Dashboard가 그룹과 명확히 구분되는 독립 항목으로 보임, 전체 화면이 하나의 Admin product처럼 일관됨, Bootstrap demo 페이지 인상이 사라짐을 확인(조사용 스크린샷은 QA 후 즉시 삭제, repo 미포함). **최종 production 여전히 4개**(`admin.css`/`admin/layout/default.html`/`admin/layout/sidebar.html`/`admin/login.html`), 본 문서 포함 **총 5개** 무변경 유지. **`docker-compose.local-test.yml`**: 전 과정 `??`(untracked) 유지. **commit/push/PR/merge**: 수행하지 않음, T9C~T9E 미착수.
- 구현 결과(3·4차 보강 + 최종 승인, 최종 - 위 1·2차 기록의 수치 중 "production 4개/header.html 변경 불필요/sidebar 200px 유지/총 5개"는 본 항목으로 대체된다): 사용자가 실제 localhost 화면에서 "여전히 Bootstrap 기본 관리자 페이지 + 텍스트 링크 목록"으로 보인다고 지적해 Admin shell을 재구성했다. **최종 변경 파일 6개(신규 0개)**: production 5개(`admin.css`, `admin/layout/default.html`, `admin/layout/header.html`, `admin/layout/sidebar.html`, `admin/login.html`) + 본 문서. `dashboard.html`/개별 list·form template/JS/Backend/Security/Migration/Public은 무변경. **Shell**(`default.html` presentation wrapper): header(white, 64px) / sidebar(white panel, 본문 높이 전체) / `.admin-workspace`(warm-neutral) 안의 `.admin-page`(white content surface, 최대 1320px, 800px form 화면은 `:has()`로 form 폭에 맞춤) / footer(작업 영역 하단). flex item이 `.admin-workspace`로 바뀌어 `min-width:0` foundation도 여기로 옮겼다. **Header**(`header.html` presentation markup): 텍스트 monogram "M"(icon library 아님) + 기관명 + "ADMIN" 보조 라벨, 오른쪽 "관리자" 라벨/이름 2단 + 로그아웃 버튼. `#admin-header`/`#admin-sidebar-toggle`/`#admin-name`/`#admin-logout-button`/aria 속성/`admin-header.js` 계약 무변경(767px 이하에서는 이름 블록만 숨김, 요소는 DOM 유지). **Sidebar**(`sidebar.html` class만 교체 - `mb-2`/inline `min-width` 제거, `.admin-nav-link--primary/--child` 부여, href/label/순서/`is-active` 계산 무변경): 폭 240px(768~1199px 220px), 대분류 라벨 12.5px/700/본문색 + 매 그룹 위 1px subtle divider, 소분류는 라벨 대비 텍스트 24px 들여쓰기(14px/400/36px 높이), active는 `#E1E8EF` 배경 + navy 700 + 3px navy `border-left`(대비 6.63:1). **Dashboard quick menu**: `dashboard.html`/JS 무변경, `#quick-menus` id로만 scope해 outline 바로가기 버튼 형태로 표시(구조 재설계는 T9E). **Login**(`login.html` presentation markup, form/필드/`#errorMessage`/인증 script 무변경): warm-neutral 전체 화면 중앙 420px white panel(375px에서 343px), monogram+기관명+ADMIN, 안내문, navy 전체폭 버튼(`rgb(58,81,105)`). **실제 렌더링 불일치 원인 규명**: 3차 보강 후에도 사용자 화면이 바뀌지 않은 원인은 서버가 아니라 **브라우저 heuristic cache** - nginx가 `/css/`를 호스트 작업 트리 bind mount에서 `Last-Modified`/`ETag`만 붙여(`Cache-Control`/`Expires` 없음) 서빙하므로, 브라우저가 이전에 받은 구버전 admin.css를 재검증 없이 재사용했고 template은 서버 렌더링이라 최신이어서 "새 template + 구 CSS" 조합이 표시됐다. HEAD(T9A) admin.css를 새 template에 입혀 사용자 증상(로그인 폼 1440px 전체폭, 버튼 `rgb(13,110,253)`, header 텍스트 한 줄, sidebar 텍스트 목록, 파란 밑줄 quick link)을 정확히 재현해 확인했다. 최종 admin.css hash는 host working tree/nginx 서빙/app JAR 모두 `006549cb5d5d`로 일치. 사용자가 Ctrl+Shift+R 강력 새로고침 후 최신 디자인 정상 반영을 직접 확인하고 최종 디자인을 승인했다. **정적 리소스 cache-busting 정책(nginx `Cache-Control` 또는 versioned URL)은 본 Task에서 구현하지 않고 별도 후속 Task로 분리한다.** **검증(캐시 없는 브라우저 context의 computed style)**: Bootstrap 기본 blue(`rgb(13,110,253)`) 가시 요소 Dashboard/Boards/Menus/Theme 0건, 375/767/768/900/1440 overflow 0, Board form 768px overflow 0, HomePinned 375px overflow 0, console/pageerror 0. `./gradlew cleanTest test` JUnit 635/635 pass, `admin-layout.spec.js`/`admin-console-errors.spec.js` 무회귀(T9A logout/active-nav/mobile sidebar/Escape/aria-expanded/767·768 유지), `visual-regression.spec.js`는 전체 실행 중 실행마다 다른 public/CKEditor 테스트가 timeout·병렬 테스트 데이터 충돌로 실패했으나 단독·순차(`--workers=1 --repeat-each=3`) 재실행 전부 pass해 flaky로 판정(T9B 회귀 아님, public template은 admin.css 미로드). 모바일 폭에서 HomePinned/Board 표 열이 좁아지는 현상은 T9B 이전부터 존재하며 T9C 범위로 남긴다. raw enum(`[GALLERY]` 등) 유지, T9C/T9D/T9E 미착수, `docker-compose.local-test.yml`은 untracked 유지.

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