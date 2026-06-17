# compose-notes — 인프라 구성 노트 (인프라 엔지니어 소유, v1.6)

> **v1.6 런타임 검증 수정 (2026-06-17, docker compose 실기동 E2E 중 발견·수정)**
> 컨테이너 실기동 후 게이트웨이 다운로드를 E2E 검증(`/tmp/v16_runtime_test.sh`, 최종 15/15 PASS)하는 과정에서 `nginx/nginx.conf` 의 `/_minio/` location 결함 2건을 고쳤다.
>
> **(수정 1) `Host` 헤더 하드코딩** — `proxy_set_header Host $proxy_host;` → `Host minio:9000;`
> - 증상: 모든 다운로드가 MinIO `400 InvalidRequest "Invalid Request (invalid hostname)"`.
> - 원인: named upstream(`minidrive_minio`) 사용 시 `$proxy_host` 가 upstream 이름(언더스코어 포함)으로 해석됨. (1) MinIO 가 언더스코어 호스트명 거부, (2) 백엔드 presign 의 SigV4 서명 host(`minio:9000`)와 불일치. → 서명값과 동일하게 고정.
>
> **(수정 2) `Authorization` 헤더 제거** — `proxy_set_header Authorization "";` 추가
> - 증상: 인증 파일 다운로드(`/api/files/{id}/download`)만 MinIO `400 "request has multiple authentication types"` (공유 공개 다운로드는 정상).
> - 원인: 인증 다운로드는 클라이언트가 `Authorization: Bearer` 헤더를 보내는데 nginx 가 기본적으로 이를 MinIO 로 전달 → presign 쿼리(SigV4) + Authorization 헤더 = 인증수단 2개로 MinIO 가 거부. MinIO 인증은 URL presign 으로만 하므로 비운다.
>
> **(운영 주의) 단일 파일 bind-mount 갱신** — `./nginx/nginx.conf` 는 단일 파일 bind-mount 라, 에디터 atomic-save(임시파일+rename)로 inode 가 교체되면 실행 중 컨테이너가 옛 내용을 계속 본다(WSL2). `nginx -s reload`/`restart` 로도 안 풀리고 **`docker compose up -d --force-recreate nginx`(컨테이너 재생성)** 필요. nginx.conf 변경 후에는 재생성으로 적용할 것.

> **v1.6 변경 요약 (2026-06-17, 게이트웨이 경유 다운로드 · X-Accel-Redirect)**
> 계약 storage-layout v1.6 "다운로드 모델 v1.6"를 인프라에 반영. 브라우저-facing presigned URL 폐기, MinIO 직접 접근 경로 제거.
>
> **(1) nginx `/_minio/` internal location 추가 (`nginx/nginx.conf`)**
> - `upstream minidrive_minio { server minio:9000; }` 추가.
> - `location /_minio/ { internal; proxy_pass http://minidrive_minio/; ... }`:
>   - `internal` 지시어로 외부 직접 접근 차단(브라우저가 `/_minio/...` 직접 호출 시 404). X-Accel-Redirect 내부 재진입만 허용.
>   - `proxy_pass .../;` 의 **trailing slash** 로 `/_minio/` prefix 가 제거되어 `<objectPath>?<presignedQuery>`(SigV4 서명 쿼리)가 변형 없이 MinIO 로 전달됨.
>   - `proxy_hide_header Content-Type` / `Content-Disposition`: MinIO 응답 헤더가 백엔드(컨트롤러)가 설정한 헤더를 덮어쓰지 않게 함(계약 v1.5 ContentTypes·한글 파일명 보존). X-Accel-Redirect 시 nginx 는 원 요청=백엔드 응답 헤더를 유지.
>   - `proxy_buffering off` / `proxy_request_buffering off` + 3600s 타임아웃: 대용량 다운로드 스트리밍.
>   - `Host $proxy_host`(=minio:9000, presign 서명 호스트 일치), `x-amz-*`·`Server` 헤더 숨김.
> - 기존 `/api/`·`/ws`·`/` 블록은 그대로. 다운로드 진입 경로 `/api/.../download` 는 이미 `/api/` 블록으로 백엔드 프록시됨(추가 블록 불요).
>
> **(2) MinIO 9000 호스트 비노출 (`docker-compose.yml`)** — ⚠️ 사람 승인 게이트(기동 구성 변경)
> - minio: `ports: ["${MINIO_API_PORT:-9000}:9000"]` 제거 → `expose: ["9000"]`(도커 네트워크 내부 전용, nginx 만 접근).
> - 콘솔 `9001`(`MINIO_CONSOLE_PORT`)은 유지(데이터 평면과 무관).
> - backend.environment 에서 `MINIO_PUBLIC_ENDPOINT` 제거(백엔드 미사용). `MINIO_ENDPOINT=http://minio:9000` 유지.
>
> **(3) 환경변수 (`.env.example`, `.env`)**
> - `MINIO_API_PORT`, `MINIO_PUBLIC_ENDPOINT` 폐기 — 폐기 사유 주석으로 남김. `.env` 는 값만 정리(새 시크릿 생성 없음).
>
> **적용 영향 (사람 승인 시 인지 필요)**
> - 호스트(`localhost:9000`)에서 MinIO S3 API 직접 접근 **불가**(끊김). 도커 네트워크 내부 nginx/backend 만 도달.
> - 기존 브라우저-facing presigned URL 다운로드 경로 **무효화** → 모든 다운로드는 nginx 게이트웨이 경유로 전환(백엔드 v1.6 컨트롤러가 X-Accel-Redirect 응답 필요).
> - MinIO 웹 콘솔(`localhost:9001`)은 그대로 사용 가능.
>
> ---
> **v1.2 변경 요약 (2026-06-16, qa 반려 결함 수정)**
> - **I-3 presign 공개 endpoint**: backend에 `MINIO_PUBLIC_ENDPOINT=http://localhost:9000` 추가(`.env.example`에도 키 추가). 내부 `MINIO_ENDPOINT=http://minio:9000`는 유지. MinIO API 포트 9000은 호스트로 published 확인됨(`${MINIO_API_PORT:-9000}:9000`).
> - **I-2 공유 라우팅 확인**: 공개 공유 API가 `GET /api/public/share/{token}`(계약 v1.2)로 이동. nginx `/api/` 블록이 `/api/public/...`을 그대로 백엔드로 프록시함 → **별도 `/share` 프록시 블록 불요·미존재**. 사람이 여는 `/share/{token}` 페이지는 SPA `location /` 폴백으로 처리. 충돌/불필요 블록 없음(주석으로 명시).

루트 `docker-compose.yml`, `nginx/nginx.conf`, `.env.example`의 구성·계약을 정리한다.
백엔드/프런트는 이 문서의 포트·환경변수·빌드 산출물 경로 요구를 그대로 맞춘다.

## 1. 구성 서비스

| 서비스 | 이미지/빌드 | 역할 | 헬스체크 | depends_on |
| --- | --- | --- | --- | --- |
| `postgres` | postgres:16-alpine | DB(`minidrive`) | `pg_isready` | - |
| `minio` | minio/minio:latest | S3 호환 스토리지 | `mc ready local` | - |
| `createbuckets` | minio/mc:latest | `minidrive` 버킷 생성(일회성) | - | minio(healthy) |
| `backend` | build `./backend` | Spring Boot API/WS | - | postgres(healthy), minio(healthy), createbuckets(completed) |
| `frontend` | build `./frontend` | 정적 빌드 산출물 생성 | - | - |
| `nginx` | nginx:1.27-alpine | 리버스 프록시 | - | backend, frontend |

네트워크: `minidrive-net`(bridge). 볼륨: `postgres-data`, `minio-data`, `frontend-dist`.

## 2. 포트 매핑

| 포트(호스트) | 대상 | 비고 |
| --- | --- | --- |
| `${HTTP_PORT:-80}` → 80 | nginx | **주 진입점**(웹 UI + /api + /ws) |
| `${BACKEND_PORT:-8080}` → 8080 | backend | 직접 디버그용(운영 진입은 nginx 경유) |
| `${POSTGRES_PORT:-5432}` → 5432 | postgres | DB 직접 접근 |
| ~~`${MINIO_API_PORT}` → 9000~~ | ~~minio S3 API~~ | **v1.6 폐기** — host 미노출, `expose: 9000`(내부 전용, nginx만 접근) |
| `${MINIO_CONSOLE_PORT:-9001}` → 9001 | minio 콘솔 | 브라우저 관리 UI(유지) |

컨테이너 내부 통신: backend↔postgres(`postgres:5432`), backend↔minio(`minio:9000`),
nginx↔backend(`backend:8080`).

## 3. Nginx 라우팅 (`nginx/nginx.conf`)

- `/api/` → `http://backend:8080` (REST, 타임아웃 300s, `client_max_body_size 0`로 대용량 업로드 허용)
  - **공개 공유 조회 `GET /api/public/share/{token}`(계약 v1.2, 비인증)도 이 블록으로 백엔드 전달** → 별도 `/share` 프록시 블록 불요 (qa I-2).
- `/ws`   → `http://backend:8080` (WebSocket: `Upgrade`/`Connection` 헤더 + 3600s 타임아웃, STOMP 핸드셰이크)
- `/`     → 정적 SPA(`/usr/share/nginx/html`, `try_files ... /index.html`로 클라이언트 라우팅 폴백)
  - **사람이 여는 공유 페이지 `/share/{token}`은 SPA 라우트 → 이 폴백으로 index.html 서빙.** 해당 페이지가 위 공개 API를 호출(라우팅 충돌 없음, qa I-2).

> 백엔드 요구: WebSocket/STOMP 엔드포인트를 **`/ws`** 경로에 노출할 것. REST는 모두 **`/api/...`** 프리픽스 사용.
> 공개 공유 조회는 **`/api/public/share/{token}`** 로 유지(별도 nginx 블록 추가 금지 — SPA 폴백과 충돌함).

## 4. 환경변수 (backend 컨테이너에 주입되는 키)

`.env`(템플릿 `.env.example`)에서 주입. 시크릿 평문 금지 — `.env.example`은 예시값만 포함.

| compose 변수(.env) | backend 환경변수 | 값/기본 | 용도 |
| --- | --- | --- | --- |
| POSTGRES_DB | `SPRING_DATASOURCE_URL`에 반영 | minidrive | jdbc URL DB명 |
| POSTGRES_USER | `SPRING_DATASOURCE_USERNAME` | minidrive | DB 사용자 |
| POSTGRES_PASSWORD | `SPRING_DATASOURCE_PASSWORD` | (필수, 시크릿) | DB 비밀번호 |
| - | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/minidrive` | 고정 호스트=`postgres` |
| MINIO_ROOT_USER | `MINIO_ACCESS_KEY` | minioadmin | S3 access key |
| MINIO_ROOT_PASSWORD | `MINIO_SECRET_KEY` | (필수, 시크릿) | S3 secret key |
| - | `MINIO_ENDPOINT` | `http://minio:9000` | S3 endpoint(내부 put/get/delete + 내부 전용 presign, path-style) |
| ~~MINIO_PUBLIC_ENDPOINT~~ | ~~`MINIO_PUBLIC_ENDPOINT`~~ | — | **v1.6 폐기** — 브라우저-facing presign 제거, 백엔드 미사용 |
| MINIO_BUCKET | `MINIO_BUCKET` | minidrive | 버킷명 |
| MINIO_REGION | `MINIO_REGION` | us-east-1 | S3 region |
| JWT_SECRET | `JWT_SECRET` | (필수, 시크릿) | JWT 서명 키 |
| JWT_ACCESS_TTL_SECONDS | `JWT_ACCESS_TTL_SECONDS` | 900 | Access 토큰 만료(초) |
| JWT_REFRESH_TTL_SECONDS | `JWT_REFRESH_TTL_SECONDS` | 1209600 | Refresh 토큰 만료(초) |
| - | `SERVER_PORT` | 8080 | 톰캣 포트 |
| SPRING_PROFILES_ACTIVE | `SPRING_PROFILES_ACTIVE` | docker | Spring 프로필 |

> 백엔드 요구: 위 환경변수 키를 그대로 읽어 `application.yml`/`application-docker.yml`에서 바인딩할 것.
> 특히 DataSource 호스트는 `postgres`, MinIO endpoint는 `http://minio:9000`(내부 DNS) 사용.
> S3 클라이언트는 `path-style-access=true`로 설정(storage-layout 계약).
>
> **presign 호스트 분리 (qa I-3, storage-layout v1.2)**: put/get/delete 클라이언트는 `MINIO_ENDPOINT`(내부),
> **presignGet 발급은 `MINIO_PUBLIC_ENDPOINT`(브라우저가 해석 가능한 공개 호스트)** 를 사용한다.
> presigner 전용 S3Presigner를 공개 endpoint로 별도 구성하거나, 발급 URL 호스트를 공개 endpoint로 치환할 것.
> 모든 `downloadUrl`(파일 상세·버전·공유)이 `MINIO_PUBLIC_ENDPOINT` 호스트로 나가야 도커 기동 시 다운로드가 동작.

## 5. 빌드 산출물 경로 / Dockerfile 요구사항

### backend (`./backend/Dockerfile` — 백엔드 팀이 작성)
- 멀티스테이지 권장: Gradle/Maven 빌드 → JRE 25 런타임 이미지.
- 컨테이너가 **8080** 리슨. `SERVER_PORT`/`server.port` 환경변수 존중.
- 위 환경변수(섹션 4)로 DataSource·MinIO·JWT 설정을 구성.
- 기동 시 DB 마이그레이션(스키마 생성) 수행 — `db-schema.md` 계약 테이블.
- MinIO 버킷은 `createbuckets`가 선생성하지만, storage-layout 계약대로 앱 기동 시 없으면 생성도 허용.

### frontend (`./frontend/Dockerfile` — 프런트 팀이 작성)
- React+TS(Vite 등)로 정적 빌드 후 산출물을 **`/dist`** 디렉터리에 둔다.
- 컨테이너는 빌드 산출물을 named volume `frontend-dist`(컨테이너 마운트 경로 `/dist`)에 노출한다.
- nginx가 같은 볼륨을 `/usr/share/nginx/html`(읽기 전용)로 마운트해 서빙한다.
- 따라서 frontend Dockerfile은 빌드 결과 정적 파일(index.html, assets/...)을 **`/dist` 루트**에 배치해야 한다.
- API 호출은 동일 출처 `/api`, WebSocket은 `/ws`(상대 경로) 사용 — CORS/호스트 하드코딩 불필요.

> 빌드 산출물 정합: frontend `/dist` 내용 = nginx `/usr/share/nginx/html`. SPA 진입점은 `index.html`.

## 6. 기동 절차 (사람 승인 게이트)

> 아래 기동/정지/파괴 명령은 **사람 승인 후** 실행한다. 인프라 에이전트는 자동 실행하지 않는다.

```bash
# 0) 환경변수 준비 (시크릿 실제값으로 교체)
cp .env.example .env
# .env 편집: POSTGRES_PASSWORD / MINIO_ROOT_PASSWORD / JWT_SECRET 등

# 1) 구문 검증 (자동 수행 OK — 파괴적 아님)
docker compose config

# 2) 빌드 + 기동 (사람 승인 필요)
docker compose up -d --build

# 3) 상태 확인
docker compose ps
docker compose logs -f backend

# 4) 접속
#   웹 UI : http://localhost:${HTTP_PORT:-80}
#   API   : http://localhost/api/...
#   MinIO 콘솔 : http://localhost:9001  (root 계정)

# 정지(볼륨 보존)
docker compose down

# 파괴적: 볼륨/데이터 삭제 — 반드시 사람 승인 (DB·오브젝트 전부 소실)
# docker compose down -v
```

시연 시드 절차: 기동 후 프런트 웹 UI에서 회원가입 → 로그인 → 업로드 순으로 진행
(PRD 11장 시연 시나리오). 별도 DB 시드 스크립트는 후속 보완 단계에서 추가 가능.

## 7. config 검증 결과

```
$ docker compose config        # (v1.6 재검증, 2026-06-17)
# → 정상 파싱, exit 0
# 6개 서비스(postgres, minio, createbuckets, backend, frontend, nginx) 전개 확인
# minio: expose ["9000"] 확인, ports 에 9000 published 없음(콘솔 9001만 published) — v1.6
# backend.environment 에 MINIO_PUBLIC_ENDPOINT 부재 확인(폐기, grep -c = 0) — v1.6
# backend.environment 에 MINIO_ENDPOINT: http://minio:9000 유지 확인
# nginx.conf: /_minio/ internal location + upstream minidrive_minio 추가(디렉티브 grammar 정상).
#   nginx -t 는 compose 네트워크 밖 standalone 에서 upstream DNS(backend) 미해석으로만 실패 →
#   문법 오류 아님(스택 기동은 사람 승인 게이트라 미실행).
```

- `up`/`down -v`, 볼륨/이미지 삭제는 미실행(사람 승인 게이트 준수).
- `${VAR:?...}` 필수 시크릿 가드: 미설정 시 compose가 오류로 차단(평문 기본값 금지).
- **I-2**: nginx에 `/share` 프록시 블록 없음 확인. `/api/`가 `/api/public/...`을 커버, SPA `location /`가 `/share/{token}` 페이지를 폴백 처리 → 충돌 없음.

## 8. 미해결 / 후속

- `backend/Dockerfile`, `frontend/Dockerfile`은 각 팀이 작성(인프라는 build context만 지정).
- 썸네일 생성 파이프라인은 storage-layout 계약대로 초기 빌드에서 키 규칙만 확정, 생성은 후속.
- 프런트 빌드 산출물 디렉터리가 `/dist`가 아닌 다른 경로(예: `/app/dist`)면 인프라에 통지 → compose 볼륨 마운트 경로 조정.
