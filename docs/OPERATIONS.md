# OPERATIONS.md

운영 서버 배포·운영 runbook (P15-T8)

이 문서는 운영 서버에서 실행하는 **명령과 절차의 canonical 문서**다. 구조/보안 계약의 근거는 `docs/ARCHITECTURE.md` "운영 배포 계약(Phase 15)", release/tag Git 흐름은 `docs/GIT_WORKFLOW.md` §6-1/§6-2를 따른다. 다른 문서는 운영 명령을 복사하지 않고 이 문서를 링크한다.

표기 규칙:

- `<DOMAIN>`: 최종 운영 도메인(발주처 확정 public domain `www.monicaenglish.com` - §3. 이 runbook의 명령/절차에서는 placeholder로 표기한다)
- `<REPO_PATH>`: 운영 서버의 저장소 checkout 절대 경로(예: `/srv/monica-lab-homepage` 같은 형태. 이 문서는 특정 경로를 강제하지 않는다)
- `<BACKUP_DIR>`: `<REPO_PATH>` **밖**의 백업 디렉토리
- `<DEPLOY_TAG>` / `<PREVIOUS_TAG>`: 배포할 / 직전 release tag(예: `v1.0.0`)
- `<ADMIN_LOGIN_ID>`: `.env`의 관리자 로그인 ID
- `<BUILD_REPO>`: 빌드 머신(app image를 build하는 개발자 PC 등)의 저장소 checkout 경로(§20)
- `<IMAGE_DIR>`: 운영 서버에서 app image archive를 받는 디렉토리(`<REPO_PATH>` **밖**, §20)
- `<SERVER>`: 빌드 머신에서 운영 서버로 접속하는 SSH 대상(호스트 별칭 등 - 서버별 값은 기록하지 않는다)
- 이 문서의 모든 `docker compose` 명령은 `<REPO_PATH>`에서 실행한다(compose는 같은 디렉토리의 `docker-compose.yml`과 `.env`를 읽는다). 운영에서는 `docker-compose.yml`만 사용하며 로컬 개발용 override(`docker-compose.local*.yml` 등)를 함께 지정하지 않는다.
- 비밀번호·secret·계정 정보·서버 IP/접속 정보 등 서버별 값은 이 문서와 저장소에 기록하지 않는다. 공식 public domain(`www.monicaenglish.com`)은 공개 웹사이트의 코드/metadata(`og:url`/`og:image`, footer 등)에 사용할 수 있지만, 이 runbook의 명령 예시와 배포 절차에서 운영자가 입력할 값은 `<DOMAIN>` 등 placeholder로 유지한다.

---

## 목차

1. 전제와 배포 구조
2. 서버 준비
3. DNS / 도메인
4. 운영 `.env`
5. 초기 관리자 계정 / 비밀번호 분실
6. 최초 배포(launch)
7. TLS bootstrap / 인증서 발급
8. 인증서 갱신(renewal) / deploy-hook
9. HSTS 주의사항
10. Reverse proxy / 실제 클라이언트 IP
11. 영속 데이터
12. 백업
13. 복원 / 복원 리허설
14. Flyway(DB migration)
15. 배포 / rollback
16. 상태 확인 / 로그 / 장애 대응 / 배포 후 smoke
17. 보안 / secret / 금지 명령
18. 관리자 운영 안내
19. Launch TBD(발주처/운영 확정 대기)
20. app image 외부 빌드·전달·검증(P15-T9)

---

## 1. 전제와 배포 구조

단일 Linux 서버에서 Docker Compose로 3개 service를 실행한다(`docker-compose.yml`).

| service | image | 역할 | host 노출 |
|---|---|---|---|
| `nginx` | `nginx:1.27-alpine` | `:80` ACME challenge + 그 외 301 https, `:443` TLS termination·HSTS·관리자 로그인 rate limit·정적 리소스(`/css/`, `/js/`, `/images/`, `/vendor/`)·reverse proxy | `80`, `443` |
| `app` | `monica-lab-homepage-app:latest` - 빌드 머신에서 저장소 `Dockerfile`로 build해 서버에 load한 image(Spring Boot, Java 21, `SPRING_PROFILES_ACTIVE=prod`). 서버에서는 build하지 않는다(§20) | 애플리케이션. 기동 시 Flyway migration 적용 | 없음(내부 `8080`) |
| `db` | `mariadb:11.4.12` | DB `monica_lab`, 계정 `monica` | 없음(내부 `3306`) |

- 기동 순서는 `db`(healthy) → `app`(healthy, `/actuator/health`) → `nginx`다(`depends_on: service_healthy`).
- 3개 service 모두 `restart: unless-stopped`, Docker 로그 `json-file` `max-size 10m` × `max-file 3`.
- Nginx는 정적 리소스를 **host checkout의 `./src/main/resources/static`**에서 직접 제공하고, app image는 **빌드 머신에서 같은 release tag로 build**된다(P15-T9 - 운영 서버에서 build하지 않는다). 그래서 **"release tag checkout + 같은 tag에서 build되어 revision 검증을 통과한 app image → `docker compose up -d --no-build --pull never`"가 하나의 배포 단위**다(checkout만 하고 app image 교체·검증 생략 금지, revision이 checkout과 다른 image 기동 금지, app image만 따로 rollback 금지 - ARCHITECTURE "Nginx 정적 리소스 공급" 결정과 P15-T9 변경). 빌드·전달·검증 절차는 §20.
- compose project 이름(`name: monica-lab-homepage`)과 app image 이름(`monica-lab-homepage-app:latest`)은 `docker-compose.yml`에 고정되어 있어 `<REPO_PATH>` 디렉토리 이름과 무관하다.
- 영속 데이터: MariaDB named volume `monica-lab-homepage_db_data`, 업로드 `./data/uploads`, 인증서 `./data/certs`, ACME webroot `./data/certbot`, 비밀값 `.env`(§11).
- Secret 원칙: 비밀번호/인증서 개인키/백업은 Git에 넣지 않는다. `.env`와 `data/`는 `.gitignore` 대상이다(§17).

---

## 2. 서버 준비

특정 Linux 배포판을 계약으로 두지 않는다. 아래 software가 있으면 된다(설치는 각 공식 문서의 해당 배포판 절차를 따른다).

| 필요 software | 용도 |
|---|---|
| Docker Engine + Docker Compose v2(`docker compose`, `up --pull never` 지원 버전) | 서비스 실행(`docker-compose.yml`), app image load(§20) |
| git | release tag checkout |
| openssl | 비밀번호 생성, placeholder 인증서, 인증서 확인 |
| certbot(host 설치) | Let's Encrypt 인증서 발급/갱신(webroot 방식, certbot 컨테이너는 쓰지 않는다) |
| curl | smoke 확인 |

1. Docker daemon을 부팅 시 자동 시작하도록 설정한다(systemd 사용 배포판: `sudo systemctl enable --now docker`). 컨테이너는 `restart: unless-stopped`이므로 daemon이 뜨면 함께 올라온다.
2. 방화벽은 인바운드 `80/tcp`, `443/tcp`(+ 서버 관리용 SSH)만 연다. `8080`/`3306`은 compose가 host에 노출하지 않으므로 열 필요가 없다.
3. **서버 outbound 네트워크가 필요하다**: `mariadb`/`nginx` image pull(Docker Hub), Let's Encrypt ACME 통신. CDN은 사용자 브라우저가 직접 받는다. 서버는 app image를 build하지 않으므로 Gradle/Maven 다운로드는 서버에 필요 없다(P15-T9 - app image build의 outbound는 빌드 머신에 필요하다, 아래 "빌드 머신").
4. 저장소를 clone한다.

   ```bash
   git clone https://github.com/krcbdnn/monica-lab-homepage.git <REPO_PATH>
   cd <REPO_PATH>
   mkdir -p data/uploads data/certs data/certbot
   ```

5. app image archive 수신 디렉토리를 저장소 밖에 만든다(§20): `mkdir -p <IMAGE_DIR> && chmod 700 <IMAGE_DIR>`

### 빌드 머신(P15-T9)

app image는 운영 서버가 아니라 빌드 머신에서 build한다(§20). 빌드 머신은 운영 서버와 별개이며 다음이 필요하다.

| 필요 software / 조건 | 용도 |
|---|---|
| Docker Engine 또는 Docker Desktop(`linux/amd64` image build 가능) | app image build/save(§20-2) |
| git + 저장소 clone(`<BUILD_REPO>`) | release tag checkout |
| SSH client(`scp`)와 운영 서버 접속 권한 | archive 전달(§20-3) |
| outbound 네트워크 | build 중 base image pull(Docker Hub), Gradle 배포본(`services.gradle.org`)·Maven 의존성 다운로드, runtime image의 `apt-get`(`curl` 설치) |

- build 중 다운로드가 일시적으로 실패하면(로컬 검증에서 Gradle 배포본 10초 read timeout 사례 있음) 같은 tag로 build를 다시 실행한다.
- 운영 서버 CPU architecture가 `linux/amd64`가 아니면 이 절차를 그대로 쓰지 않는다(§20의 `--platform` 값과 서버 architecture가 일치해야 한다).

---

## 3. DNS / 도메인

- 최종 운영 도메인은 발주처 소유의 `www.monicaenglish.com`으로 확정되었다(DNS 소유·관리 주체: 발주처). 공개 metadata(`og:url`/`og:image`, P15-T7B)가 이 도메인을 사용하며, 아래 명령에서는 `<DOMAIN>`으로 표기한다.
- 인증서 발급 전에 `<DOMAIN>`(그리고 apex 등 다른 이름도 서비스할 경우 그 이름도)의 **A 레코드가 이 서버의 공인 IPv4를 가리켜야** 한다. Let's Encrypt HTTP-01 검증은 인터넷에서 `http://<DOMAIN>/.well-known/acme-challenge/...`로 이 서버의 `:80`에 접근한다.
- AAAA(IPv6) 레코드를 두면 Let's Encrypt는 IPv6로도 접근할 수 있다. 서버가 그 IPv6로 `80/443`을 실제로 서비스하지 않으면 AAAA를 두지 않는다(잘못된 AAAA는 발급 실패 원인이 된다).
- DNS 변경 반영을 `dig +short <DOMAIN>` 등으로 확인한 뒤 §7을 진행한다.
- favicon/`og:image`/`og:url`(P15-T7B)은 확정 도메인과 발주처 공식 로고 기준으로 적용되어 있다. 도메인을 바꾸면 `home/layout/default.html`의 `siteUrl` 상수 변경과 새 release가 필요하다.

---

## 4. 운영 `.env`

`.env`는 `<REPO_PATH>/.env`에 둔다. `.env.example`을 복사해 값을 채운다.

```bash
cd <REPO_PATH>
cp .env.example .env
chmod 600 .env
```

필수 5개(비어 있으면 `docker compose config`/`up`이 `required variable ... is missing a value`로 즉시 실패 - fail-fast):

| 변수 | 의미 | 주의 |
|---|---|---|
| `MARIADB_PASSWORD` | DB 계정 `monica` 비밀번호(app의 `DB_PASSWORD`로도 전달) | **DB volume이 처음 만들어질 때만** MariaDB에 적용된다. 이후 `.env`만 바꾸면 app이 DB에 접속하지 못한다(변경은 DB 계정 비밀번호 변경 절차가 별도로 필요 - 현재 runbook 범위 밖) |
| `MARIADB_ROOT_PASSWORD` | MariaDB root 비밀번호(백업/복원 명령이 컨테이너 안에서 사용) | 위와 같이 최초 초기화 때만 적용 |
| `ADMIN_LOGIN_ID` | 초기 관리자 로그인 ID | §5 |
| `ADMIN_PASSWORD` | 초기 관리자 비밀번호 | §5. ASCII만 사용 |
| `ADMIN_NAME` | 초기 관리자 표시 이름 | |

DB명(`monica_lab`)/계정명(`monica`)/`UPLOAD_ROOT=/app/uploads`/`TZ=Asia/Seoul`은 compose에 고정되어 있어 `.env`에 없다.

비밀번호 생성:

```bash
openssl rand -base64 24   # 32자, 영문 대소문자/숫자/`+`/`/`
```

- 값에 `$`, 공백, 따옴표, `#`을 넣지 않는다(compose의 `.env` 변수 치환/주석 해석 혼동 방지). 위 명령의 출력에는 이 문자가 없다.
- `ADMIN_PASSWORD`는 관리자 비밀번호 정책(8~64자, 공백 제외 ASCII 출력 문자, 영문/숫자/특수문자 중 2종 이상 - CODING_RULES)을 만족하게 한다. 위 출력은 거의 항상 만족하지만 영문과 숫자가 모두 들어 있는지 눈으로 확인한다.
- **`ADMIN_PASSWORD`에 한글 등 비ASCII 문자나 72byte를 넘는 값을 쓰지 않는다.** 초기 관리자 생성(`AdminInitializer`)은 `.env` 값을 정책 검증 없이 BCrypt로 hash하며, BCrypt 입력 한도(72byte)를 넘으면 예외가 발생해 app 기동이 실패한다(코드 확인 기준의 동작 - `restart: unless-stopped`로 재시작이 반복된다).
- 설정 확인(값을 출력하지 않는 형태):

  ```bash
  docker compose config --quiet && echo "compose config OK"
  ```

- `.env`는 commit 금지(`.gitignore` 대상), 권한 `600`, 백업 시 secret으로 취급한다(§12).

---

## 5. 초기 관리자 계정 / 비밀번호 분실

### 초기 관리자 생성(현재 코드 동작)

- app 기동 시마다 `AdminInitializer`(`ApplicationRunner`)가 실행된다.
- `ADMIN_LOGIN_ID`/`ADMIN_PASSWORD`/`ADMIN_NAME` 중 하나라도 비어 있으면 계정을 만들지 않고 ERROR 로그만 남긴다(compose fail-fast 때문에 운영에서는 비어 있을 수 없다).
- **`ADMIN_LOGIN_ID`와 같은 login_id의 관리자가 DB에 없을 때만** BCrypt hash로 새 관리자를 만든다. 이미 있으면 아무것도 바꾸지 않는다.
- 따라서 **최초 생성 이후 `.env`의 `ADMIN_PASSWORD`/`ADMIN_NAME`을 바꿔도 기존 계정에 반영되지 않는다.** `ADMIN_LOGIN_ID`를 다른 값으로 바꾸면 그 ID로 관리자가 **추가로** 만들어지므로(기존 계정은 그대로) 바꾸지 않는다.
- 생성 시 app 로그에 `초기 관리자 계정을 생성했습니다. loginId=...`가 남는다(비밀번호는 로그에 남지 않는다).

최초 배포 직후 절차:

1. `https://<DOMAIN>/admin/login`에서 `.env`의 초기 계정으로 로그인한다.
2. 관리자 화면 header의 **"비밀번호 변경"**(`/admin/password`)에서 즉시 새 비밀번호로 바꾼다. 이후 `.env`의 `ADMIN_PASSWORD`는 더 이상 실제 비밀번호가 아니지만 compose 필수값이라 비울 수 없다 - 계속 secret으로 취급한다.

### 관리자 비밀번호 분실 시 recovery

**공식 계약: 현재 관리자 비밀번호 분실에 대한 공식 자동 복구 기능(비밀번호 찾기/재설정 화면, API, 재설정 script)은 없다.** 이는 PRD/FEATURES/API 계약상 의도적 제외다. 일반적인 비밀번호 변경은 로그인한 상태에서 `/admin/password`로 한다.

공식 복구 절차는 아직 확정되지 않았다(§19 Launch TBD: "관리자 비밀번호 분실 recovery 공식화/검증"). 아래는 현재 코드 동작상 **기술적으로 가능한 비상 복구 후보(emergency recovery candidate)**에 대한 설명일 뿐이며, 운영 절차로 승인된 것이 아니다. launch나 정기 운영에서 수행하는 절차가 아니다.

비상 복구 후보 개요:

1. DB 백업(§12)을 먼저 받는다.
2. `.env`에 새 `ADMIN_PASSWORD`를 준비한다(§4 생성 규칙, `ADMIN_LOGIN_ID`는 변경하지 않는다).
3. 분실한 계정의 login_id와 정확히 일치하는 `admin` 행 1건만 삭제한다.
4. app 컨테이너를 다시 만들어(`.env` 변경 반영) `AdminInitializer`가 같은 login_id의 관리자를 새 비밀번호로 다시 생성하게 한다.
5. 새 비밀번호로 로그인한 뒤 `/admin/password`에서 다시 변경한다.

근거(로컬 확인): `AdminInitializer`는 login_id가 DB에 없을 때만 관리자를 만들고, `.env`의 `ADMIN_PASSWORD`는 기존 관리자 행을 자동 갱신하지 않는다. 복원 리허설 DB에서 `admin`을 참조하는 foreign key는 0건이었다.

경고:

- 공식 자동 reset 기능이 아니다. **DB 행을 직접 삭제하는 destructive 작업**이다.
- 실행 전 DB 백업이 필수다.
- login ID를 정확히 확인해야 하며, **다른 관리자 행을 삭제하면 안 된다**(조건을 잘못 쓰면 다른 계정이 삭제되거나, 0건이면 재생성이 일어나지 않는다). 삭제 후 재생성 전까지는 해당 관리자 계정이 없다.
- 운영에서 실행하기 전에 **별도 승인과 검증(운영 DB가 아닌 환경에서의 end-to-end 리허설)이 필요**하다. 현재는 로컬에서 FK 부재만 확인했고 end-to-end 리허설은 하지 않았으므로, 이 문서는 실행 명령을 제공하지 않는다. 실행 명령은 공식화/검증 시 확정한다.
- SQL로 BCrypt hash를 직접 UPDATE하는 방식은 권장하지 않는다(hash 생성 도구·형식 검증이 필요하고 실수 위험이 크다).

---

## 6. 최초 배포(launch)

release tag 생성(develop → main PR merge commit → main에 annotated tag)은 `docs/GIT_WORKFLOW.md` §6-1을 따른다. 서버는 **branch가 아니라 release tag**를 checkout한다(mutable branch pull 금지).

```bash
cd <REPO_PATH>
git fetch --tags origin
git checkout <DEPLOY_TAG>          # detached HEAD가 정상
git status --porcelain             # 출력 없음이어야 한다(.env, data/는 gitignore 대상이라 표시되지 않음)
```

1. §4 `.env` 작성, `docker compose config --quiet` 통과.
2. `data/uploads`, `data/certs`, `data/certbot` 디렉토리 존재 확인(§2).
3. §7-1 placeholder 인증서 생성(인증서 파일이 없으면 nginx 설정 검사가 `cannot load certificate`로 실패해 nginx가 기동하지 않는다 - 로컬 확인).
4. §20-2~20-4로 `<DEPLOY_TAG>`의 app image를 빌드 머신에서 build → 서버로 전달 → load·revision 검증 → `monica-lab-homepage-app:latest` tag.
5. 전체 기동(서버에서 build하지 않는다 - `--build` 금지):

   ```bash
   docker compose pull db nginx       # mariadb/nginx image만 받는다(app image는 registry에서 받지 않음)
   docker compose up -d --no-build --pull never
   docker compose ps                  # db/app: healthy, nginx: Up
   docker compose logs --tail=200 app # Flyway migration 적용, "Started ..." 확인
   ```

   기동 후 §20-5로 실행 중 app container의 image가 검증한 image인지 확인한다.

   최초 기동에서 Flyway가 V1부터 전체 migration을 적용한다(§14). app healthcheck는 `start_period 30s` + `10s` 간격 5회다.
6. §7-2~7-4로 실제 인증서 발급·적용.
7. §5 초기 관리자 비밀번호 변경.
8. §16-4 smoke checklist.
9. 배포 기록: 배포한 tag, `git rev-parse HEAD`, app image ID(`docker image inspect --format '{{.Id}}' monica-lab-homepage-app:latest`), 일시를 운영 기록에 남긴다(§11).

---

## 7. TLS bootstrap / 인증서 발급

Nginx는 도메인과 무관한 고정 경로 `/etc/nginx/certs/fullchain.pem`, `/etc/nginx/certs/privkey.pem`만 읽는다(host `./data/certs`를 read-only mount). ACME webroot는 host `./data/certbot` → 컨테이너 `/var/www/certbot`이다.

### 7-1. placeholder 인증서(최초 1회, 실제 발급 전까지만)

```bash
cd <REPO_PATH>
openssl req -x509 -newkey rsa:2048 -nodes -days 7 -subj "/CN=<DOMAIN>" \
  -keyout data/certs/privkey.pem -out data/certs/fullchain.pem
chmod 600 data/certs/privkey.pem
```

placeholder는 nginx를 띄워 ACME 검증을 받기 위한 것이다. 브라우저는 이 인증서를 신뢰하지 않으므로 **실제 인증서로 바로 교체하고 placeholder로 서비스를 공개하지 않는다**.

### 7-2. ACME webroot 접근 확인

```bash
mkdir -p data/certbot/.well-known/acme-challenge
echo ok > data/certbot/.well-known/acme-challenge/ping
curl -i http://<DOMAIN>/.well-known/acme-challenge/ping   # 200, 본문 ok (301이 아니어야 한다)
rm data/certbot/.well-known/acme-challenge/ping
```

### 7-3. deploy-hook 준비

certbot이 발급/갱신에 **성공했을 때만** 실행하는 hook으로, 새 인증서를 `data/certs`에 실제 파일로 복사하고 nginx 설정 검사 후 reload한다. 검사에 실패하면 직전 파일로 되돌려 기존 유효 인증서를 유지한다. 저장소 밖(예: `/usr/local/sbin/monica-lab-cert-deploy.sh`)에 root 소유로 만든다.

```sh
#!/bin/sh
# certbot deploy-hook: RENEWED_LINEAGE(= /etc/letsencrypt/live/<DOMAIN>)는 certbot이 전달한다.
set -eu
REPO_PATH=<REPO_PATH>
DST="$REPO_PATH/data/certs"
SRC="${RENEWED_LINEAGE:?run by certbot only}"

install -m 644 "$SRC/fullchain.pem" "$DST/fullchain.pem.new"   # live/의 symlink를 따라 실제 내용 복사
install -m 600 "$SRC/privkey.pem"   "$DST/privkey.pem.new"
cp -p "$DST/fullchain.pem" "$DST/fullchain.pem.prev"
cp -p "$DST/privkey.pem"   "$DST/privkey.pem.prev"
mv -f "$DST/fullchain.pem.new" "$DST/fullchain.pem"
mv -f "$DST/privkey.pem.new"   "$DST/privkey.pem"

cd "$REPO_PATH"
if docker compose exec -T nginx nginx -t; then
  docker compose exec -T nginx nginx -s reload
else
  mv -f "$DST/fullchain.pem.prev" "$DST/fullchain.pem"
  mv -f "$DST/privkey.pem.prev"   "$DST/privkey.pem"
  echo "nginx -t failed - previous certificate restored" >&2
  exit 1
fi
```

```bash
sudo chmod 700 /usr/local/sbin/monica-lab-cert-deploy.sh
```

### 7-4. 실제 인증서 발급

```bash
sudo certbot certonly --webroot -w <REPO_PATH>/data/certbot -d <DOMAIN> \
  --deploy-hook /usr/local/sbin/monica-lab-cert-deploy.sh
# 다른 이름(예: apex 도메인)도 서비스하는 경우 -d <그 이름> 추가(DNS가 먼저 이 서버를 가리켜야 한다)
```

- 발급 원본은 `/etc/letsencrypt/live/<DOMAIN>/`(symlink)에 있고, hook이 `data/certs`에 실제 파일로 복사한다. nginx는 `data/certs`만 본다.
- `--deploy-hook`은 renewal 설정에 저장되어 갱신 때도 실행된다.
- 확인:

  ```bash
  openssl x509 -noout -subject -issuer -enddate -in data/certs/fullchain.pem
  curl -sI https://<DOMAIN>/ | head -1       # -k 없이 성공해야 한다
  ```

- Let's Encrypt에는 발급 횟수 제한이 있다. 설정을 시험할 때는 `--dry-run`(staging)으로 먼저 확인한다.

---

## 8. 인증서 갱신(renewal) / deploy-hook

- 갱신은 host certbot이 수행한다. certbot 설치 방식에 따라 systemd timer 또는 cron이 자동 등록되므로 등록 여부를 확인한다(예: `systemctl list-timers | grep -i certbot`). 없으면 하루 1~2회 `certbot renew`를 실행하도록 운영자가 등록한다.
- 갱신 경로 점검(실제 인증서를 바꾸지 않는다. dry-run에서는 deploy-hook이 실행되지 않는다):

  ```bash
  sudo certbot renew --dry-run
  ```

- 갱신 성공 시에만 deploy-hook이 `data/certs` 교체 → `nginx -t` → reload를 수행한다. **갱신이 실패하면 hook이 실행되지 않아 `data/certs`의 기존 유효 인증서가 그대로 유지**된다.
- 만료일 확인: `openssl x509 -noout -enddate -in <REPO_PATH>/data/certs/fullchain.pem`. 만료 30일 이내인데 갱신되지 않았다면 certbot 로그(`/var/log/letsencrypt/`)를 확인한다.
- hook을 수동으로 다시 실행해야 하면 `sudo RENEWED_LINEAGE=/etc/letsencrypt/live/<DOMAIN> /usr/local/sbin/monica-lab-cert-deploy.sh`.

---

## 9. HSTS 주의사항

- Nginx가 `:443`의 모든 응답(정적 리소스 location, 오류 응답, 429 포함)에 `Strict-Transport-Security: max-age=31536000`을 붙인다. `includeSubDomains`, `preload`는 없다. Spring Security HSTS는 꺼져 있다(Nginx 단독 담당). `:80`(301/ACME) 응답에는 붙지 않는다.
- 브라우저는 **유효한 인증서로 받은** HSTS를 1년간 기억해 그 도메인을 HTTP로 열지 않는다. 그 기간에 HTTPS가 깨지면(인증서 만료 등) 사용자는 우회할 수 없다. 그래서 실제 인증서 적용과 §8 갱신 점검을 먼저 끝내고 공개한다.
- HSTS 값을 낮추거나 끄는 변경은 이 runbook 범위가 아니다(ARCHITECTURE 계약 변경 사항).

---

## 10. Reverse proxy / 실제 클라이언트 IP

- 현재 계약은 **Nginx가 인터넷에 직접 노출된 가장 바깥 proxy**인 구성이다. 관리자 로그인 rate limit(`POST /api/admin/login`, `5r/m`, `burst=5`, `nodelay`, 초과 시 429)은 `$binary_remote_addr`(실제 접속 IP)로 계산된다.
- 현재 nginx 설정에는 `real_ip`(`set_real_ip_from`/`real_ip_header`) 설정이 **없다**. Cloudflare/로드밸런서 등 앞단 proxy를 두면 모든 요청이 proxy IP로 보여 **모든 사용자가 하나의 rate limit을 공유**하게 되고(정상 관리자 로그인이 429로 막힐 수 있음), app이 받는 `X-Forwarded-For`도 proxy 기준이 된다.
- 앞단 proxy 도입은 이 runbook만으로 할 수 없다. `nginx/nginx.conf`에 신뢰할 proxy 대역과 `real_ip` 설정을 추가하는 **별도 변경 Task**가 필요하다(ARCHITECTURE "관리자 로그인 rate limit").

---

## 11. 영속 데이터

| 항목 | 위치 | 중요도 | 백업 | Git |
|---|---|---|---|---|
| DB | Docker named volume `monica-lab-homepage_db_data`(compose에 이름 고정, container `/var/lib/mysql`) | 최상 | 논리 dump(§12). volume 파일 직접 복사는 사용하지 않는다 | 해당 없음 |
| 업로드 파일 | `<REPO_PATH>/data/uploads`(→ app `/app/uploads`) | 최상(DB의 file 행과 짝) | `tar` 보관(§12) | gitignore(`data/`) |
| `.env` | `<REPO_PATH>/.env` | 상(secret, DB volume 초기화 비밀번호와 일치해야 함) | secret으로 별도 보관 | gitignore |
| TLS 인증서 | 원본 `/etc/letsencrypt/`(certbot), 사용본 `<REPO_PATH>/data/certs` | 중(재발급 가능) | 선택(아래) | gitignore(`data/`) |
| ACME webroot | `<REPO_PATH>/data/certbot` | 하(임시 파일) | 불필요 | gitignore |
| app image(현재·직전 release tag) | 서버 Docker image 저장소(`monica-lab-homepage-app:<TAG>`, `:latest`) | 중(빌드 머신에서 같은 tag로 재build 가능) | 불필요. rollback용으로 직전 tag image를 서버에 남긴다(§20-6) | 해당 없음 |
| app image archive | `<IMAGE_DIR>`(서버), 빌드 머신의 저장소 밖 디렉토리 | 하(load 후 불필요) | 불필요 | 저장소 밖에 둔다 |
| 배포 기록 | 운영 기록(배포 tag, commit, 일시) | 상(rollback·복원 기준) | 백업과 함께 기록 | 해당 없음 |

- 인증서 백업은 필수가 아니다: 서버를 잃어도 DNS를 새 서버로 옮기고 §7로 재발급할 수 있다. 다만 `/etc/letsencrypt/`에는 ACME 계정 키와 개인키가 있으므로 백업한다면 secret으로 취급한다.
- volume은 이름이 고정되어 있어 checkout 디렉토리 이름이 바뀌어도 같은 volume에 연결되지만, `docker compose down -v`는 이 volume을 **삭제한다**(§17).

---

## 12. 백업

백업 script/자동화 코드는 **저장소에 없다**(Phase 15 결정 D7: 절차로 관리). 아래 명령을 운영자가 실행하거나 운영자가 직접 cron 등에 등록한다.

```bash
cd <REPO_PATH>
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p <BACKUP_DIR> && chmod 700 <BACKUP_DIR>

# 1) DB 논리 dump - InnoDB 일관 snapshot(--single-transaction), root 비밀번호는 컨테이너 안에서만 사용
docker compose exec -T db sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --triggers --default-character-set=utf8mb4 --databases monica_lab' \
  > <BACKUP_DIR>/db-$TS.sql
test -s <BACKUP_DIR>/db-$TS.sql && tail -n 1 <BACKUP_DIR>/db-$TS.sql   # "-- Dump completed on ..." 확인

# 2) 업로드 파일
tar -C data -czf <BACKUP_DIR>/uploads-$TS.tar.gz uploads

# 3) .env(secret) 와 배포 기록
install -m 600 .env <BACKUP_DIR>/env-$TS
{ git describe --tags --exact-match 2>/dev/null || echo "no-tag"; git rev-parse HEAD; docker image inspect --format '{{.Id}}' monica-lab-homepage-app:latest; } > <BACKUP_DIR>/deployed-$TS.txt

chmod 600 <BACKUP_DIR>/*-$TS*
```

- 현재 모든 테이블이 InnoDB라 `--single-transaction`으로 서비스 중단 없이 일관된 dump를 받는다(로컬 확인: 11개 테이블 모두 InnoDB).
- dump에는 관리자 비밀번호 hash가 포함된다. 백업 파일 전체를 secret으로 취급한다.
- `<BACKUP_DIR>`는 저장소 밖에 둔다(저장소 안에 두면 실수로 commit될 수 있다).

보관(필수 계약과 권장값 구분):

- **필수**(D7): DB와 업로드를 매일 백업하고, 서버 내부 단기 보관과 **서버 외부 보관 위치 최소 1곳**을 둔다. 외부 보관 위치와 책임자는 **TBD - launch 전에 확정**(§19).
- **권장**: 배포/rollback/복원 등 위험 작업 직전 추가 백업, 분기 1회 복원 리허설(§13-2). 보관 기간(retention)은 계약값이 없다 - 저장 공간과 발주처 요구에 맞춰 운영자가 정한다.
- cron 등록 예(권장 예시이며 저장소에 포함된 script가 아니다): 위 1)~3)을 운영자가 서버에 `<BACKUP_DIR>` 밖의 script 파일로 만들고 매일 새벽 실행하도록 등록한 뒤, 외부 보관 위치로 복사하는 단계를 이어 붙인다.

---

## 13. 복원 / 복원 리허설

### 13-1. 실제 복원(장애/서버 교체)

**운영 DB에 복원하면 백업 이후의 데이터는 사라진다.** 가능하면 복원 직전 현재 상태도 §12로 백업해 둔다.

새 서버(빈 volume)로 복원하는 순서:

1. §2/§4 준비. `.env`는 백업한 `env-<TS>`를 사용한다(새 비밀번호로 새 volume을 초기화해도 dump에는 DB 계정 정보가 없으므로 무방하다).
2. **백업 시점의 release tag**(`deployed-<TS>.txt`)를 checkout한다. dump의 schema와 코드 버전을 맞춘다.
3. DB만 먼저 기동해 dump를 넣는다.

   ```bash
   docker compose up -d db
   docker compose ps db                                   # healthy 확인
   docker compose exec -T db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < <BACKUP_DIR>/db-<TS>.sql
   ```

4. 업로드 복원(빈 `data/uploads` 기준. 같은 이름 파일은 덮어쓴다):

   ```bash
   tar -C data -xzf <BACKUP_DIR>/uploads-<TS>.tar.gz
   ```

5. §20으로 백업 시점 tag의 app image를 준비(서버 보관본이 없으면 빌드 머신에서 같은 tag로 build·전달)하고 revision을 검증한 뒤, §7 TLS 준비 후 `docker compose pull db nginx` → `docker compose up -d --no-build --pull never`, §16-4 smoke.

기존 서버의 운영 DB를 되돌려야 하는 경우도 3)의 import가 dump에 포함된 테이블을 drop 후 다시 만든다(`mariadb-dump` 기본 동작). 이 작업은 데이터 손실을 수반하므로 운영 책임자 승인 후 수행한다. `down -v`/volume 삭제로 "초기화"하지 않는다.

### 13-2. 복원 리허설(운영 DB를 건드리지 않는 방법)

운영(또는 개발) DB/volume과 **완전히 분리된** 일회용 MariaDB에 복원해 검증한다. 기존 db 컨테이너/volume을 mount하거나 재생성하지 않는다.

```bash
TS=<백업 TS>
N=monica-restore-verify-$TS                    # 운영 이름과 겹치지 않는 이름
docker ps -a --format '{{.Names}}' | grep -x "$N"; docker volume ls -q | grep -x "$N-data"   # 출력 없어야 함
P=$(openssl rand -hex 24)                      # 리허설 전용 root 비밀번호(출력하지 않음)
docker volume create "$N-data"
docker run -d --name "$N" -e MARIADB_ROOT_PASSWORD="$P" -v "$N-data:/var/lib/mysql" mariadb:11.4.12   # host 포트 노출 없음
docker exec "$N" sh -c 'until mariadb-admin ping -h 127.0.0.1 -uroot -p"$MARIADB_ROOT_PASSWORD" --silent; do sleep 2; done'
docker exec -i "$N" sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < <BACKUP_DIR>/db-$TS.sql
```

검증: 운영 DB와 리허설 DB에서 같은 테이블별 `COUNT(*)`를 비교하고, `flyway_schema_history`의 버전/성공 여부를 확인한다. 업로드는 별도 임시 디렉토리에 `tar -xzf` 후 원본과 `diff -rq`로 비교한다(운영 `data/uploads`에 풀지 않는다).

정리(리허설 자원만 삭제):

```bash
docker rm -f "$N"
docker volume rm "$N-data"
```

리허설 기록: 실행 일시, 백업 TS, 테이블별 row 수 비교 결과, 정리 완료 여부. 로컬 리허설 기록은 `docs/TASK.md` P15-T8.

---

## 14. Flyway(DB migration)

- migration은 `src/main/resources/db/migration`의 `V{n}__*.sql`이며, **app 기동 시 Flyway가 자동 적용**한다(별도 명령 없음). prod는 `spring.jpa.hibernate.ddl-auto=validate`로 Entity와 schema 일치를 검증한다.
- **forward-only**다. 이 프로젝트는 undo/rollback migration을 두지 않으며, 이전 tag로 코드를 되돌려도 schema는 자동으로 되돌아가지 않는다.
- 그래서 **migration이 포함된 release는 배포 직전 §12 백업이 필수**다.
- migration 실패 시 app이 기동하지 못한다(재시작 반복). `flyway_schema_history` 행 삭제/수정, Flyway repair, 수동 DDL로 "고치지" 않는다. app 로그를 보존하고 개발 담당자에게 전달한 뒤, 필요하면 §15-2 절차(배포 전 백업 복원 + 이전 tag)로 되돌린다.

---

## 15. 배포 / rollback

### 15-1. 배포(새 release tag)

```bash
cd <REPO_PATH>
git fetch --tags origin
git show --no-patch <DEPLOY_TAG>                 # 대상 tag 확인(main의 annotated tag)
# §12 백업
git checkout <DEPLOY_TAG>
git status --porcelain                           # 출력 없음
docker compose config --quiet
# §20-2~20-4: 빌드 머신에서 <DEPLOY_TAG> app image build → 전달 → load·revision 검증 → :latest tag
docker compose pull db nginx                     # mariadb/nginx image만 받는다
docker compose up -d --no-build --pull never     # 검증한 app image로 변경된 service 재생성(서버 build 없음)
docker compose ps
# §20-5: 실행 중 app container image ID = 검증한 image ID 확인
docker compose logs --tail=200 app
```

- 서버에서는 `--build`/`docker build`를 쓰지 않는다(운영 서버 메모리 부족 위험, P15-T9). 반드시 §20-4 revision 검증을 통과한 image로 `--no-build --pull never` 기동한다. checkout만 하고 app image 교체·검증을 생략하면 nginx의 정적 리소스(host checkout)와 app/template(image)의 버전이 어긋난다.
- `--pull never`이므로 필요한 image가 서버에 없으면 기동이 실패한다(registry에서 대체 image를 받지 않는다). `mariadb`/`nginx`는 직전의 `docker compose pull db nginx`로 준비한다. `docker compose pull`(service 지정 없음)은 app image까지 registry에서 받으려 하므로 쓰지 않는다.
- app image build는 빌드 머신의 외부 네트워크가 필요하다(§2 "빌드 머신").
- 직전 tag의 app image(`monica-lab-homepage-app:<PREVIOUS_TAG>`)는 rollback용으로 서버에 남긴다(§20-6).
- 컨테이너가 재생성되면 그 컨테이너의 이전 Docker 로그는 사라진다. 이전 로그가 필요하면 배포 전에 `docker compose logs app > <BACKUP_DIR>/app-before-$TS.log`로 남긴다.
- 배포 후 §16-4 smoke, 배포 기록(§11) 갱신.

### 15-2. Rollback

- 기본: 이전 tag checkout과 **그 tag의 app image**로 전체 stack을 다시 기동한다(app image만 따로 되돌리지 않는다, 서버에서 build하지 않는다).

  ```bash
  cd <REPO_PATH>
  # 현재 상태 §12 백업
  git checkout <PREVIOUS_TAG>
  git status --porcelain                           # 출력 없음
  # 서버에 monica-lab-homepage-app:<PREVIOUS_TAG>가 남아 있으면 §20-4의 revision/platform 검증만 수행(archive hash/load 생략)
  # 없으면 빌드 머신에서 §20-2~20-3으로 다시 build·전달하고 §20-4 전체(hash 확인·load·검증) 수행
  # (§20의 <DEPLOY_TAG> 자리에 <PREVIOUS_TAG>) - "revision OK"일 때만:
  docker tag monica-lab-homepage-app:<PREVIOUS_TAG> monica-lab-homepage-app:latest
  docker compose pull db nginx
  docker compose up -d --no-build --pull never
  docker compose ps
  # §20-5: 실행 중 app container image ID = <PREVIOUS_TAG> image ID 확인
  ```

- 되돌리는 release에 **migration이 없었다면** 위 절차로 충분하다.
- **migration이 있었다면** 이전 코드가 새 schema와 호환된다는 보장이 없다(기동 시 `ddl-auto=validate` 실패 가능). 이 경우 `<PREVIOUS_TAG>` checkout 후 §13-1처럼 **배포 직전 백업을 복원**해야 하며, 배포 이후 쌓인 데이터는 사라진다. DB를 SQL로 수동 downgrade하지 않는다. 운영 책임자 승인 후 진행한다.

---

## 16. 상태 확인 / 로그 / 장애 대응 / 배포 후 smoke

### 16-1. 상태

```bash
docker compose ps                                   # db/app: (healthy), nginx: Up(healthcheck 없음)
curl -fsS https://<DOMAIN>/actuator/health          # {"status":"UP"} (상세 정보는 노출하지 않음)
```

- app healthcheck: 컨테이너 안 `curl -f http://localhost:8080/actuator/health`. db: `mariadb-admin ping`.
- Actuator는 `health`만 web에 노출된다. 별도 모니터링 시스템은 범위 밖이다(필요하면 외부 uptime 감시로 `/actuator/health`를 확인한다).

### 16-2. 로그

```bash
docker compose logs --tail=200 nginx
docker compose logs --tail=200 app
docker compose logs --tail=200 db
docker compose logs -f app                          # 실시간
```

- 3개 service 모두 Docker `json-file`, `max-size 10m` × `max-file 3`(service당 최대 약 30MB)로 회전한다. 장기 보관/중앙 수집은 없다.
- app prod 로그: SQL 출력 없음, 4xx 업무 예외는 stacktrace 없는 WARN 한 줄, 5xx/미처리 예외는 ERROR + stacktrace.
- nginx access/error log는 stdout/stderr로 나간다. 429는 error log에 `limiting requests`로 남는다.

### 16-3. 장애 확인 순서

1. `docker compose ps` - 멈춘/재시작 반복 service, health 상태.
2. `docker compose logs --tail=200 nginx` - 인증서 로드 실패(`cannot load certificate`), upstream 오류(502), 429.
3. `docker compose logs --tail=300 app` - 기동 실패(Flyway/DB 접속/`.env` 값), 5xx stacktrace.
4. `docker compose logs --tail=200 db` - 디스크/초기화/접속 거부.
5. 디스크 여유(`df -h`), 인증서 만료일(§8).

- 502가 계속되면 app이 healthy인지 먼저 본다(nginx는 app이 healthy가 된 뒤 기동하지만, 이후 app 장애 시 502를 반환한다).
- 같은 tag로 다시 올릴 때도 서버에서 build하지 않고 `docker compose up -d --no-build --pull never`를 쓴다(app image는 §20-4 검증을 통과한 `:latest`). `down -v`는 쓰지 않는다(§17).

### 16-4. 배포 후 smoke checklist

정상 동작 확인용이다. **로그인 반복 시도·rate limit 확인 같은 남용 테스트는 운영에서 하지 않는다**(로컬/검증 환경의 `frontend-tests/admin-login-rate-limit.spec.js` 범위). 관리자 로그인은 1~2회만 수행한다(같은 IP에서 6회를 넘기면 12초당 1회로 제한된다).

```bash
curl -sI http://<DOMAIN>/boards | grep -iE '^HTTP|^location'                    # 301 → https://<DOMAIN>/boards
curl -sI http://<DOMAIN>/.well-known/acme-challenge/none | head -1               # 404 (301 아님 - ACME 예외)
echo | openssl s_client -connect <DOMAIN>:443 -servername <DOMAIN> 2>/dev/null | openssl x509 -noout -issuer -enddate
curl -sI https://<DOMAIN>/ | grep -iE '^HTTP|strict-transport|^server'          # 200, max-age=31536000, "Server: nginx"(버전 없음)
curl -s  https://<DOMAIN>/robots.txt                                             # User-agent: * / Disallow: /admin/ / Disallow: /api/admin/
for p in / /boards /programs /pages/INTRODUCTION /css/home.css /actuator/health; do
  curl -s -o /dev/null -w "%{http_code} $p\n" https://<DOMAIN>$p                # 모두 200
done
curl -s -o /dev/null -w "%{http_code}\n" https://<DOMAIN>/no-such-path            # 404 (HTML 오류 페이지)
curl -sI https://<DOMAIN>/admin/dashboard | grep -iE '^HTTP|^location'           # 302, Location: /admin/login
for p in /favicon.ico /apple-touch-icon.png /images/og-default.png; do
  curl -s -o /dev/null -w "%{http_code} %{content_type} $p
" https://<DOMAIN>$p  # 모두 200, image/*
done
curl -s https://<DOMAIN>/ | grep -oE '<meta property="og:(url|image)" content="[^"]*"'   # og:url = https://<DOMAIN>/, og:image = https://<DOMAIN>/images/og-default.png
```

브라우저 확인:

- [ ] 공개 홈/게시판/프로그램/기관소개 상세가 정상 표시, 게시글 첨부 이미지(업로드 파일 `/api/files/{id}`)가 표시
- [ ] 404 화면이 사이트 오류 페이지로 표시(서버 내부 정보 없음)
- [ ] `/admin/login` 로그인 성공 → 대시보드 표시
- [ ] 개발자 도구에서 `JSESSIONID`(Secure, HttpOnly, SameSite=Lax), `XSRF-TOKEN`(Secure) 확인
- [ ] 로그인 상태에서 `https://<DOMAIN>/api/admin/me`가 관리자 정보 JSON 반환
- [ ] `/admin/password` 화면이 열리는지 확인(최초 배포 시에는 §5대로 실제 변경, 그 외 배포에서는 화면 확인만 - 불필요하게 비밀번호를 바꾸지 않는다)
- [ ] 로그아웃 → `/admin/login`으로 이동, 이후 `/api/admin/me` 401
- [ ] 5xx 화면은 운영에서 일부러 만들지 않는다(자동 테스트로 검증된 범위)
- [ ] 브라우저 탭에 favicon이 표시된다(이전 캐시가 있으면 시크릿 창에서 확인). 메신저/SNS 공유 미리보기는 플랫폼 캐시가 있으므로 필요하면 각 플랫폼의 공유 디버거로 캐시를 갱신한 뒤 확인한다

---

## 17. 보안 / secret / 금지 명령

절대 commit하지 않는다(현재 `.gitignore`: `.env`, `data/`, `application-local.yml`):

- `.env`, `<BACKUP_DIR>`의 `env-*`
- `data/certs`(개인키 `privkey.pem`), `/etc/letsencrypt/`
- `data/uploads`
- DB dump, 백업 archive(저장소 밖 `<BACKUP_DIR>`에만 둔다)

권한 권장: `.env` `600`, `data/certs/privkey.pem` `600`, `<BACKUP_DIR>` `700`, deploy-hook `700`(root 소유).

금지 명령/행동:

| 금지 | 이유 |
|---|---|
| `docker compose down -v` | `-v`는 `monica-lab-homepage_db_data`(이름 고정 volume)를 **삭제**한다 - 전체 DB 손실 |
| `docker volume rm monica-lab-homepage_db_data`, 무계획 `docker volume prune`/`docker system prune --volumes` | DB volume 삭제 |
| 운영 DB/volume에 복원 리허설 | 운영 데이터 덮어쓰기 - 리허설은 §13-2의 별도 컨테이너에서만 |
| 백업 없이 migration 포함 release 배포 | Flyway는 forward-only라 되돌릴 방법이 백업 복원뿐 |
| `git pull`/`git checkout`만 하고 app image 교체·revision 검증 생략, mutable branch(develop/main) 배포 | 정적 리소스와 app/template 버전 불일치, 배포 버전 추적 불가 |
| 운영 서버에서 `docker compose up --build`/`docker compose build`/`docker build`(P15-T9) | 서버 메모리 부족(OOM)으로 실행 중 DB/app이 종료될 위험, 서버 Gradle/Maven outbound 의존 |
| revision label이 서버 checkout HEAD와 다르거나 label이 없는 app image로 기동 | 정적 리소스·schema와 불일치, 출처 미확인 image 실행 |
| `docker compose pull`(service 미지정)·registry에서 app image pull, `--pull never` 없이 기동 | 검증하지 않은 image로 대체될 위험(app image는 §20 archive로만 공급). service 미지정 pull은 `build:`가 있는 app도 pull 대상에 포함한다(로컬 확인 - §20-5) |
| 무계획 `docker image prune -a`/`docker system prune -a`, 직전 tag app image 삭제 | rollback용 app image 손실(재build 필요) |
| app image archive를 `<REPO_PATH>` 안에 두기 | 실수로 commit될 위험(`.gitignore` 대상 아님) |
| app image만 이전 버전으로 rollback | 정적 리소스·schema와 불일치 |
| `flyway_schema_history` 수정/삭제, 수동 DDL | schema 이력 손상 |
| 인증서 개인키·`.env`·dump commit | secret 유출 |
| 운영에서 로그인 반복 시도(rate limit 시험) | 관리자 로그인 차단 |
| 로컬 개발용 compose override를 운영에서 지정 | 포트/설정이 운영 계약과 달라짐 |

---

## 18. 관리자 운영 안내

발주처 관리자에게 전달할 내용(기능 상세는 `docs/FEATURES.md`):

- **로그인/세션**: 관리자 세션은 서버 기본값(마지막 요청 후 30분)으로 만료된다(별도 설정 없음). 긴 글을 오래 작성한 뒤 저장이 실패하면 작성 내용을 복사해 두고 다시 로그인한 뒤 저장한다. 로그인 시도가 너무 많으면 "로그인 시도가 너무 많습니다" 안내가 나오며 잠시 후 다시 시도한다.
- **비밀번호**: header의 "비밀번호 변경"에서 바꾼다(8~64자, 공백 제외 영문/숫자/ASCII 특수문자, 2종 이상). 분실 시 운영 담당자에게 요청한다(현재 공식 자동 복구 기능 없음 - §5).
- **지원 서식**: 제목/굵게/기울임/링크, 글머리·번호 목록과 들여쓰기, 인용, 표(머리글·셀 병합), 이미지 업로드·정렬·크기·대체 텍스트·캡션. 미디어(YouTube 등)·지도 embed는 지원하지 않는다 - 오시는 길은 지도 이미지 + 외부 지도 링크로 작성한다(FEATURES "편집 서식 보존 계약").
- **이미지 대체 텍스트**: 본문 이미지를 선택한 뒤 이미지 도구의 대체 텍스트 입력으로 이미지 내용을 짧게 적는다(화면 낭독 사용자용, 입력은 강제되지 않으므로 운영 원칙으로 지킨다).
- **업로드 한도**: 파일당/요청당 10MB.

---

## 19. Launch TBD(발주처/운영 확정 대기)

Engineering 완료(Phase 15 개발 Task)와 실제 공개 launch 준비는 별개다. 아래는 launch 전에 확정/수행해야 하는 항목이며, 확정/완료된 항목은 비고에 표시한다.

| 항목 | 담당 | 비고 |
|---|---|---|
| 최종 운영 도메인 / DNS 소유·관리 주체 | 발주처 | **확정**: `www.monicaenglish.com`, DNS 소유·관리 발주처(§3). DNS 레코드 설정은 배포 단계 |
| 운영 서버/hosting 정보, SSH 접근 관리자 | 발주처/운영 | §2 |
| 브랜드 asset(로고 원본) → favicon, `og:image` | 발주처 | **완료**: 발주처 공식 로고 제공, P15-T7B 파생 asset 적용(`v1.0.0` 이후 release에 포함) |
| 운영 `.env` 값 생성·보관 책임자 | 운영 | §4 |
| 백업 외부 보관 위치 / 백업 책임자 / 보관 기간 | 발주처/운영 | §12 D7 필수 계약 |
| 관리자 비밀번호 분실 recovery 공식화/검증 | 발주처/운영 | §5 - 현재 공식 자동 복구 기능 없음, 비상 복구 후보만 기록(공식 절차 아님) |
| Let's Encrypt 실제 발급, deploy-hook 등록, `renew --dry-run` | 운영 | §7, §8 |
| release PR(develop → main) / `v1.0.0` tag | 개발 | GIT_WORKFLOW §6-1 |
| 운영 외부 HTTPS smoke | 운영 | §16-4 |
| 앞단 proxy(Cloudflare/LB) 사용 여부 | 발주처/운영 | 사용 시 §10 별도 변경 Task |
| app image 빌드 머신 / 빌드·전달 담당자 | 개발/운영 | §2 "빌드 머신", §20(P15-T9) |

---

## 20. app image 외부 빌드·전달·검증(P15-T9)

운영 서버에서는 app image를 build하지 않는다(ARCHITECTURE "Nginx 정적 리소스 공급" P15-T9 변경). 빌드 머신에서 release tag 기준으로 build한 image를 `docker save` archive로 전달하고, 서버에서 load·검증한 뒤 `docker compose up -d --no-build --pull never`로 기동한다. registry(Docker Hub/GHCR 등)는 사용하지 않는다.

이름 규칙:

| 항목 | 값 |
|---|---|
| compose project | `monica-lab-homepage`(`docker-compose.yml` top-level `name`) |
| app image(배포 버전) | `monica-lab-homepage-app:<DEPLOY_TAG>` |
| app image(compose가 기동하는 이름) | `monica-lab-homepage-app:latest`(`docker-compose.yml` app `image`) |
| OCI label | `org.opencontainers.image.revision` = `<DEPLOY_TAG>`가 가리키는 commit SHA(빌드 머신: `git rev-parse "<DEPLOY_TAG>^{commit}"` - 작업 checkout의 HEAD가 아니다), `org.opencontainers.image.version` = `<DEPLOY_TAG>` |
| build context | `git archive`로 그 commit의 tracked 파일만 추출한 저장소 밖 디렉토리(`<BUILD_CTX>`, §20-2). 작업 checkout(`.`)은 쓰지 않는다 |
| revision 검증 기준 | 빌드 머신: label = `git rev-parse "<DEPLOY_TAG>^{commit}"`(§20-2). 서버: label = tag checkout 상태의 `git rev-parse HEAD`이고 `git describe --tags --exact-match` = `<DEPLOY_TAG>`(§20-4) |
| platform | `linux/amd64`(운영 서버 architecture와 일치해야 한다) |
| archive | `monica-lab-homepage-app_<DEPLOY_TAG>.tar`(빌드 머신·서버 모두 저장소 **밖**) |
| image ID | 빌드 머신과 서버의 image store가 다르면(예: Docker Desktop containerd store와 기존 방식 store) 같은 image라도 ID가 다르게 보일 수 있다. build commit 검증은 revision label로 하고, ID는 서버에서 load한 뒤 서버에서 조회한 값만 기록·비교한다(§6 9단계, §20-5). 로컬 리허설에서는 containerd store가 manifest list digest를 ID로 표시하는 것까지만 확인했고, 서버에서 실제로 ID가 달라지는지는 미검증 |

### 20-1. 전제

- 빌드 머신 요건은 §2 "빌드 머신"을 따른다. 아래 빌드 머신 명령은 POSIX shell(Git Bash 등) 기준이다. Windows PowerShell 5.1에서는 `$REV = git rev-parse "<DEPLOY_TAG>^{commit}"`처럼 변수를 쓰고(빌드 머신 작업 checkout의 HEAD를 쓰지 않는다), `git archive` 출력도 pipe로 넘기지 말고 `-o` 파일로 받는다(§20-2). **`docker save` 출력을 PowerShell pipe/redirect로 받지 말고 반드시 `-o` 파일 옵션을 쓴다**(PowerShell 5.1 pipe는 binary를 텍스트로 변환해 archive를 손상시킬 수 있다). SHA-256은 `Get-FileHash -Algorithm SHA256 <파일>`로 구한다.
- **운영 서버로 전달하는 app image는 반드시 main의 release tag(`<DEPLOY_TAG>`)를 기준으로 §20-2 절차를 수행해 만든 것이어야 한다.** release tag 없이 만든 리허설·검증 image(기능 브랜치 commit 기준 등)는 서버로 전달하지 않는다(서버 §20-4의 tag 확인·revision 검증에서 거부된다). 리허설 image를 release tag 이름으로 재tag해 쓰지 않는다.
- 빌드 머신의 로컬 개발 stack과 image 이름이 겹친다(`monica-lab-homepage-app:latest`는 로컬 개발 `--build`도 만든다). 배포용으로는 항상 `:<DEPLOY_TAG>` tag를 build·save하고, 빌드 머신의 `:latest`를 전달하지 않는다.
- **Windows Git Bash 주의**: (1) Git Bash의 GNU tar는 `C:/…`처럼 콜론이 있는 경로를 `host:path` 원격 경로로 해석해 `Cannot connect to C: resolve failed`로 실패하고 대상 디렉토리를 빈 채로 남긴다. `tar -x -C`에는 `/c/…` 형식 경로를 쓰거나 `tar --force-local`을 쓰고, tar가 실패하면 그 자리에서 중단한다(빈 context는 §20-2의 2) diff에서도 걸러지지만 1)에서 멈춘다). (2) `docker cp "$CID":/app/app.jar …`의 container 쪽 경로가 Git Bash 경로 변환으로 Windows 경로로 바뀌지 않도록 `MSYS_NO_PATHCONV=1`을 설정하고 실행한다. 이 설정은 호스트 쪽 경로 변환도 끄므로 `docker cp`의 호스트 경로는 `C:/…` 형식을 써야 한다(`/c/…`를 그대로 넘기면 Docker가 `C:\c\…`로 해석해 `invalid output path`로 실패한다). 즉 같은 디렉토리라도 `tar -x -C`에는 `/c/…`, `docker cp` 호스트 경로에는 `C:/…`를 쓴다(로컬 리허설·v1.1.1 Release QA는 `MSYS_NO_PATHCONV=1` 설정 상태로만 정상 동작을 확인했다. 미설정 시 동작은 미검증).
- **검증 범위**: §20의 빌드 머신 절차와 로컬 기동·rollback·`--pull never`·service 지정 pull 동작은 빌드 머신 Docker Desktop(Compose v2.37.1-desktop.1, Git Bash)에서 확인했다(TASK.md P15-T9 리허설 결과). 서버 `docker-compose-v2 2.40.3`(설치 예정)에서의 동작, `scp` 전달, 서버 sha256 확인·`docker load`·revision 검증은 **미검증**이며 최초 배포 때 §20-4/§20-5로 확인한다.

### 20-2. build(빌드 머신)

**빌드 머신의 작업 checkout(`<BUILD_REPO>`)을 build context로 직접 쓰지 않는다.** 작업 checkout에는 Git이 추적하지 않는 파일(gitignored `src/main/resources/application-local.yml` 같은 로컬 비밀 설정, untracked 파일)이 남아 있을 수 있고, `COPY src src`가 이를 build stage로 복사하면 `bootJar`가 jar 리소스(`BOOT-INF/classes/`)에 포함시킬 수 있다(`git status --porcelain`은 ignored 파일을 보여주지 않는다). 그래서 **배포할 commit의 tracked 파일만 `git archive`로 저장소 밖 빈 디렉토리(`<BUILD_CTX>`)에 추출**해 그 디렉토리를 build context로 쓴다(`.dockerignore`의 `src/main/resources/application-local.yml` 제외 규칙은 보조 방어선이다).

```bash
cd <BUILD_REPO>
git fetch --tags origin
REV=$(git rev-parse "<DEPLOY_TAG>^{commit}")       # tag가 가리키는 commit SHA
git describe --tags --exact-match "$REV"           # <DEPLOY_TAG>
BUILD_CTX=<저장소 밖 빈 디렉토리>                   # 예: 빌드 머신 임시 디렉토리 아래 monica-ctx-<DEPLOY_TAG>
mkdir "$BUILD_CTX"                                 # 이미 있으면 실패한다 - 실패하면 중단(이전 추출물 재사용 금지)

# 1) 정확한 commit의 tracked 파일만 추출(작업 checkout의 ignored/untracked/미commit 변경은 포함되지 않는다)
git archive --format=tar "$REV" | tar -x -C "$BUILD_CTX"

# 2) 추출 결과 = commit의 tracked 파일 목록(차이 출력 없음이어야 한다 - ignored/추가 파일 혼입 없음)
diff <(cd "$BUILD_CTX" && find . -type f | sed 's|^\./||' | sort) <(git ls-tree -r --name-only "$REV" | sort) \
  && echo "context = tracked files only"

# 3) 로컬 비밀 설정 파일 부재 확인(파일 내용은 열거나 출력하지 않는다)
test ! -e "$BUILD_CTX/src/main/resources/application-local.yml" && echo "application-local.yml absent"

# 4) gradlew 줄바꿈 LF 확인(CR 0개여야 한다). 실행 권한은 Dockerfile의 `chmod +x gradlew`가 부여한다
test "$(tr -cd '\r' < "$BUILD_CTX/gradlew" | wc -c)" -eq 0 && echo "gradlew LF OK"
head -1 "$BUILD_CTX/gradlew"                       # #!/bin/sh 계열 shebang

# 5) build(context = $BUILD_CTX, 작업 checkout `.` 금지)
docker build --platform linux/amd64 \
  --label org.opencontainers.image.revision=$REV \
  --label org.opencontainers.image.version=<DEPLOY_TAG> \
  -t monica-lab-homepage-app:<DEPLOY_TAG> "$BUILD_CTX"
docker image inspect --format '{{.Os}}/{{.Architecture}} {{index .Config.Labels "org.opencontainers.image.revision"}} {{index .Config.Labels "org.opencontainers.image.version"}}' \
  monica-lab-homepage-app:<DEPLOY_TAG>               # linux/amd64 <REV> <DEPLOY_TAG>

# 6) jar 안에 application-local.yml이 없는지 확인(파일 이름 목록만 본다 - 내용 추출/출력 금지)
CID=$(docker create monica-lab-homepage-app:<DEPLOY_TAG>)   # 실행하지 않는 임시 container(검사용)
# Git Bash: MSYS_NO_PATHCONV=1, 호스트 경로는 C:/… 형식(§20-1)
docker cp "$CID":/app/app.jar "$BUILD_CTX/../app-check-<DEPLOY_TAG>.jar"
docker rm "$CID"
unzip -Z1 "$BUILD_CTX/../app-check-<DEPLOY_TAG>.jar" | grep -E '^BOOT-INF/classes/application' # application.yml, application-prod.yml만 보여야 한다
unzip -Z1 "$BUILD_CTX/../app-check-<DEPLOY_TAG>.jar" | grep -q 'BOOT-INF/classes/application-local' \
  && echo "FOUND application-local - 배포 중단" || echo "application-local absent in jar"
rm "$BUILD_CTX/../app-check-<DEPLOY_TAG>.jar"

# 7) save + hash
docker save -o <저장소 밖 경로>/monica-lab-homepage-app_<DEPLOY_TAG>.tar monica-lab-homepage-app:<DEPLOY_TAG>
sha256sum <저장소 밖 경로>/monica-lab-homepage-app_<DEPLOY_TAG>.tar   # 값을 기록한다
```

- 1)~4)와 6) 중 하나라도 기대값이 아니면(diff 출력 있음, `application-local.yml` 존재, gradlew에 CR 존재, jar에 `application-local` 존재) **build/save/전달하지 않고 중단**한다. 6)에서 FOUND이면 그 image는 삭제(`docker image rm monica-lab-homepage-app:<DEPLOY_TAG>`)하고 원인을 확인한다.
- `unzip`이 없으면 같은 목록을 JDK `jar tf <jar>` 또는 Windows `tar -tf <jar>`로 본다(목록만, `-x`/압축 해제 금지).
- **비밀값 노출 금지**: `application-local.yml`·`.env`의 내용을 `cat`/`type`/`grep` 결과로 출력하지 않는다. jar 검사는 파일 이름 목록만 다루고 리소스 내용을 추출하거나 출력하지 않는다. build 로그(`docker build` 출력)나 이 절차의 출력에 비밀값이 섞이면 그 로그를 운영 기록·PR·채팅에 붙이지 않는다.
- `docker create`로 만든 검사용 container는 실행하지 않으며, 6)에서 바로 삭제한다(`docker ps -a`로 남은 것이 없는지 확인).
- `<BUILD_CTX>`와 검사용 jar는 저장소 밖에 두고, 7) 이후 `<BUILD_CTX>`는 삭제한다(다음 build에 재사용하지 않는다).
- 빌드 머신의 작업 checkout에서 `git checkout <DEPLOY_TAG>`를 할 필요는 없다(`git archive "$REV"`가 tag commit을 직접 추출한다). Windows PowerShell 5.1에서는 `git archive` 출력을 PowerShell pipe로 넘기지 말고 Git Bash에서 실행하거나 `git archive --format=tar -o <파일> "$REV"` 후 `tar -xf <파일> -C <BUILD_CTX>`로 푼다(§20-1 binary pipe 주의와 같은 이유). Git Bash GNU tar에서는 `-C`에 `C:/…` 형식 경로를 쓰지 않는다(§20-1 Windows Git Bash 주의).
- Dockerfile은 build stage에서 `./gradlew bootJar -x test`를 실행한다. 테스트는 release 전 CI/Release QA에서 통과한 commit(tag)이어야 한다(GIT_WORKFLOW §6-1).
- build 중 다운로드 일시 실패 시 같은 tag로 다시 build한다(§2 "빌드 머신").

### 20-3. 전달(빌드 머신 → 서버)

```bash
scp <저장소 밖 경로>/monica-lab-homepage-app_<DEPLOY_TAG>.tar <SERVER>:<IMAGE_DIR>/
```

- 접속 정보·호스트 키 검증은 운영자 SSH 설정을 따른다(호스트 키 검증을 끄지 않는다).

### 20-4. load·revision 검증(서버)

```bash
cd <REPO_PATH>
git describe --tags --exact-match                  # <DEPLOY_TAG>(§6/§15에서 이미 checkout한 상태)
sha256sum <IMAGE_DIR>/monica-lab-homepage-app_<DEPLOY_TAG>.tar   # 20-2에서 기록한 값과 일치해야 한다
docker load -i <IMAGE_DIR>/monica-lab-homepage-app_<DEPLOY_TAG>.tar
EXPECTED=$(git rev-parse HEAD)
ACTUAL=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' monica-lab-homepage-app:<DEPLOY_TAG>)
docker image inspect --format '{{.Os}}/{{.Architecture}} {{index .Config.Labels "org.opencontainers.image.version"}}' \
  monica-lab-homepage-app:<DEPLOY_TAG>               # linux/amd64 <DEPLOY_TAG>
if [ -n "$ACTUAL" ] && [ "$EXPECTED" = "$ACTUAL" ]; then echo "revision OK"; else echo "revision MISMATCH - 기동 중단"; fi
```

- rollback(§15-2)·복원(§13-1)에서 해당 tag image가 이미 서버에 보관되어 있으면 archive hash 확인과 `docker load`는 생략하고, `EXPECTED`/`ACTUAL` revision 비교와 platform 확인부터 수행한다.
- `revision OK`일 때만 다음을 실행한다. hash 불일치, label 없음, revision 불일치, platform 불일치이면 기동하지 않고 20-2부터 다시 수행한다.

  ```bash
  docker tag monica-lab-homepage-app:<DEPLOY_TAG> monica-lab-homepage-app:latest
  ```

### 20-5. 기동과 실행 image 확인(서버)

```bash
docker compose pull db nginx
docker compose up -d --no-build --pull never
docker compose ps
# 실행 중 app container의 image ID = 검증한 image ID
docker inspect --format '{{.Image}}' $(docker compose ps -q app)
docker image inspect --format '{{.Id}}' monica-lab-homepage-app:<DEPLOY_TAG>
```

- 두 ID가 다르면 배포 실패로 보고 원인을 확인한다(같은 tag의 image를 다시 load/tag 후 재기동).
- `docker compose pull`에 service를 지정하지 않으면 `build:`가 있는 app도 pull 대상에 포함된다(로컬 확인). 반드시 `docker compose pull db nginx`로 지정한다(§17). service 미지정 pull에서 app pull 실패가 경고/오류 중 어느 쪽으로 처리되는지는 미검증이다.
- app image를 load·tag하지 않은 채 `up -d --no-build --pull never`를 실행하면 `No such image`로 실패하고, pull이나 build로 대신 채워지지 않는다(로컬 확인). 실패하면 §20-4로 돌아간다.
- `Found orphan containers` 경고가 나와도 `--remove-orphans`를 덧붙이지 않는다. orphan은 다른 compose 파일로 만든 container일 수 있으므로 정리는 확인 후 별도로 결정한다.
- 이후 §6(최초 배포) 또는 §15-1(배포)의 나머지 단계(로그 확인, smoke, 배포 기록)를 이어간다.

### 20-6. 보관/정리

- 서버에는 현재 tag와 직전 tag의 app image(`monica-lab-homepage-app:<DEPLOY_TAG>`, `:<PREVIOUS_TAG>`)를 남긴다(§15-2 rollback). 그보다 오래된 tag image는 운영자가 확인 후 `docker image rm monica-lab-homepage-app:<OLD_TAG>`로 지운다(무계획 `prune -a` 금지 - §17).
- archive는 20-5 확인 후 서버 `<IMAGE_DIR>`와 빌드 머신에서 삭제해도 된다(같은 tag로 다시 build 가능). 남긴다면 저장소 밖에만 둔다.
