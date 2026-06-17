# 검증 모듈: 보안 (JWT/인가/공유토큰/비밀번호)
검증일: 2026-06-16 · 검증자: qa-reviewer · 방법: 정적 분석(read/grep)

## 판정: 통과

JWT 만료·refresh 회전·logout 무효화, 소유자 인가, 공유 토큰 차단, BCrypt 저장 모두 계약대로 구현됨. 위반 없음.

## 위반 목록

| # | 항목 | 증거(파일:라인) | 심각도 | 담당 |
| --- | --- | --- | --- | --- |
| (없음) | 보안 차단 수준 위반 없음 | — | — | — |

## 확인된 통과 항목

- **JWT 만료 검증.** `JwtService.parse`(`auth/JwtService.java:51-57`)가 `parseSignedClaims`로 서명·만료 검증, 만료 시 예외. REST는 `JwtAuthFilter.doFilterInternal`(`auth/JwtAuthFilter.java:33-45`)에서 예외 시 컨텍스트 비워 미인증 처리 → SecurityConfig 엔트리포인트가 **401 UNAUTHENTICATED**(`config/SecurityConfig.java:48-49`). 미인증 보호 자원은 `anyRequest().authenticated()`(`SecurityConfig.java:46`).
- **Refresh 회전(기존 무효화).** `AuthService.refresh`(`auth/AuthService.java:63-87`): 저장 해시 조회→`stored.isValid()` 검사→**`stored.revoke()`로 기존 무효화**→신규 access+refresh 발급. 위조/만료/회수는 `INVALID_REFRESH`(401). typ 클레임이 `refresh`인지도 확인(`:71`).
- **Logout 무효화.** `AuthService.logout`(`:89-93`)이 해당 refresh 해시를 찾아 `revoke()`. 컨트롤러는 인증 필요 경로.
- **Refresh 토큰 원문 미저장.** SHA-256 해시로 저장(`AuthService.hash :102-110`, `issueRefreshToken :95-100`) — db-schema 계약(token_hash) 준수.
- **WS 인증.** CONNECT 프레임 `Authorization: Bearer` JWT 검증 + access 타입 확인, 실패 시 연결 거부(`sync/WebSocketConfig.java:54-66`).
- **소유자 인가 — 남의 자원 403.** 모든 file/folder 조회·변경이 `requireOwnedFile`/`requireOwnedFolder`로 `owner_id == 요청자` 검증, 위반 시 `ApiException.forbidden()`(403). 목록은 쿼리에서 `ownerId` 필터링. 적용 메서드: file upload/version/get/patch/delete/listVersions/versionDownload/restore/permanentDelete, folder list/create/update/delete, notification markRead(`findByIdAndUserId`). (백엔드 감사 에이전트 교차 확인 — 누락 메서드 없음.) 공유 비활성화도 file 소유권으로 인가(`share/ShareService.java:94-96`).
- **공유 토큰 차단.** `ShareService.resolve`(`share/ShareService.java:67-85`): `is_active=false`면 **DISABLED(410)**, `isExpired()`면 **EXPIRED(410)**, 토큰 없음/파일 없음/비UPLOADED면 **INVALID_LINK(404)**. ErrorCode 매핑 EXPIRED/DISABLED=GONE(410), INVALID_LINK=NOT_FOUND(404) — 계약 일치.
- **비밀번호 BCrypt.** `SecurityConfig.passwordEncoder`(`:64-66`)=`BCryptPasswordEncoder`. signup 시 `passwordEncoder.encode`(`auth/AuthService.java:45`), login 시 `matches`(`:54`). 평문 저장 없음.

## 사람 승인/위험 보고

- (경미) WS `onWebSocketClose`(프런트 `realtime/stompClient.ts:52-58`)는 최신 토큰으로 헤더만 갱신하고 stomp 자동 재연결(reconnectDelay 3000)에 의존. 단순 재사용으로 충분하다는 계약 메모(websocket-events v1.1 §)와 부합 — 위반 아님.
- (정보) Access TTL 기본 900s(15분), 계약은 "≈30분" 표기. 도커 환경변수 `JWT_ACCESS_TTL_SECONDS` 기본 900(`docker-compose.yml:93`). 보안상 더 짧으므로 위반 아님이나, 시연 중 빈번한 refresh 유발 가능 — 운영 합의 권장.
- 런타임 e2e(실제 만료 토큰 401, 타 사용자 403 응답)는 **인프라 기동 후 확인** 권장(정적으로는 통과).

---

# 검증 모듈: 다운로드 게이트웨이 (계약 v1.6 — X-Accel-Redirect, 공유 비활성화 우회 차단)
검증일: 2026-06-17 · 검증자: qa-reviewer · 방법: 정적 분석(read/grep) + 백엔드 통합테스트 실측(ShareFlowTest/FileFlowTest) + `docker compose config` 렌더 확인

## 판정: 통과 (정적 종합)

브라우저-facing presigned URL 폐기 → 게이트웨이 경유(X-Accel-Redirect) 단일 인가 길목으로 전면 개정됨. 헤더 규약 삼각 정합, 브라우저 비노출 불변식, 옛 모델 제거, 포트 비노출, 인가/에러 매핑, 프런트 정합 모두 계약 v1.6대로 구현. 백엔드 통합테스트 13건 전부 통과(ShareFlowTest 6/6, FileFlowTest 7/7, failures=0). 차단 수준 위반 없음. 잔여 항목은 모두 런타임(인프라 기동) 검증 대상이며 사람 승인 게이트.

## 위반 목록

| # | 항목 | 증거(파일:라인/테스트) | 심각도 | 담당 |
| --- | --- | --- | --- | --- |
| (없음) | v1.6 차단 수준 위반 없음 | — | — | — |
| O-1(관찰) | 프런트 `download.ts` 주석이 "백엔드가 바디(스트림) + Content-Disposition 반환"이라 기술 — 실제 v1.6 흐름은 nginx가 X-Accel-Redirect 소비 후 MinIO에서 스트리밍(백엔드 바디 없음). 코드 동작은 정상(런타임에 nginx 거치면 바디가 실림). 주석 부정확만 존재. | `frontend/src/lib/download.ts:4-7` | 경미(문서) | frontend |

## 확인된 통과 항목 (정적 교차검증 6항목)

### 1. 헤더 규약 삼각 정합 — PASS
- **백엔드 출력 형식**: `GatewayDownload.redirect`(`storage/GatewayDownload.java:42-56`)가 `X-Accel-Redirect = "/_minio"` + `pathAndQuery(presignedUrl)`(`:48,59-64`)로 `/_minio/<objectPath>?<presignedQuery>` 생성. `INTERNAL_PREFIX="/_minio"`(`:24`) 고정 — 계약 §X-Accel-Redirect 내부 규약과 일치.
- **path-style·버킷 포함**: presign이 `S3Configuration.pathStyleAccessEnabled(true)`(`storage/S3Config.java:47-49`, `application.yml:37 path-style-access: true`)로 서명되어 URL 경로가 `/<bucket>/<key>` = `/minidrive/users/{u}/{f}` 형태. nginx 주석 예시(`nginx.conf:72-73`)와 동일. `objectPath`에 버킷명 포함 확인.
- **nginx trailing-slash prefix 제거**: `location /_minio/ { internal; proxy_pass http://minidrive_minio/; }`(`nginx.conf:75-77`) — `proxy_pass`에 trailing slash(`/`)가 있어 `/_minio/` prefix가 제거되고 `/<bucket>/<key>?<query>`가 minio 루트로 그대로 전달.
- **SigV4 Host 정합**: presigner가 **내부 endpoint(`minio:9000`)로 서명**(`S3Config.java:43` endpointOverride=props.getEndpoint, docker `MINIO_ENDPOINT=http://minio:9000` `docker-compose.yml:91`). nginx가 `proxy_set_header Host $proxy_host`(`nginx.conf:82`, = `minio:9000`)로 전달 → 서명 호스트와 전달 Host 일치 → SigV4 유효. 쿼리스트링은 nginx가 변형 없이 통과(`nginx.conf:74,81`).

### 2. 브라우저 비노출 불변식 — PASS
- **downloadUrl이 게이트웨이 상대경로**: share resolve = `"/api/public/share/{token}/download"`(`share/ShareService.java:94`), file detail = `"/api/files/{id}/download"`(UPLOADED일 때, 아니면 null) (`file/FileService.java:187-191`). presigned URL 아님.
- **실측**: `FileFlowTest.get_uploadedFile_downloadUrl_isGatewayPath`가 `$.downloadUrl == /api/files/{id}/download` 단언(`FileFlowTest.java:73-78`); `ShareFlowTest`가 `$.downloadUrl == /api/public/share/{token}/download` 단언(`ShareFlowTest.java:64-66`). 둘 다 통과.
- **응답 바디·헤더에 presign 미노출**: 다운로드 응답은 바디 없는 200(`GatewayDownload.java:55` `.build()`). 실측 `content().string("")`(`FileFlowTest.java:106-107`, `ShareFlowTest.java:80-81`). presign 쿼리(`X-Amz-Signature`)는 `X-Accel-Redirect` 헤더 값에만 존재.
- **X-Accel-Redirect는 nginx가 소비(클라이언트 미전달)**: nginx 기본 동작(internal redirect 시 X-Accel-Redirect 헤더는 클라이언트로 전달되지 않음). 계약 §브라우저 비노출 불변식과 일치. (MockMvc 단위에서는 nginx 미경유라 헤더가 보이지만 — 이는 백엔드 출력 검증용이며, 실제 비노출은 nginx 단계에서 발생: 런타임 절차서 (d)로 확인.)
- **MinIO 부가 헤더 정리**: nginx가 `proxy_hide_header Content-Type/Content-Disposition/x-amz-*`(`nginx.conf:90-96`)로 MinIO 헤더를 숨기고 백엔드 헤더를 우선 → 계약 §응답 헤더 규칙 준수.

### 3. 옛 모델 제거 확인 — PASS
- `MINIO_PUBLIC_ENDPOINT` / `public-endpoint` / 브라우저 presign 치환 로직: 코드·설정에서 **모두 제거**. 잔존은 전부 "폐기됨" 주석뿐(grep 결과: `S3Config.java:37`, `application.yml:39`, `docker-compose.yml:92`, `.env:19`, `.env.example:22` — 모두 폐기 설명 주석).
- 브라우저-facing presign 5분(`presign-download-ttl-seconds`/300s) 참조: **잔존 없음**. presign은 `internal-presign-ttl-seconds`(기본 30s, `StorageProperties.java:20`, `application.yml:40`)로 내부 전용·초단기화. S3Presigner는 내부 endpoint로 서명(`S3Config.java:41-51`).
- `.env`/`.env.example`: `MINIO_API_PORT`·`MINIO_PUBLIC_ENDPOINT` 제거 명시(`.env:19-20`, `.env.example:19-22`).

### 4. 포트 비노출 — PASS (compose config 렌더 실측)
- `docker-compose.yml` minio: `expose: ["9000"]`(`:39-40`), `ports`는 콘솔 `9001:9001`만(`:41-42`). 9000 host published 없음.
- **`docker compose config` 렌더 실측**: minio 섹션이 `expose: - "9000"` + `ports: target:9001 published:"9001"`만 렌더. 전체에서 `published` 포트는 backend(18080), minio-console(9001), nginx(7915) 뿐 — **9000 published 없음 확인**.
- nginx upstream `minidrive_minio { server minio:9000; }`(`nginx.conf:16-18`)로 도커 네트워크 내부에서만 접근.

### 5. 인가 규칙·에러 매핑 — PASS (통합테스트 실측)
- **공유 다운로드 매 요청 재검증**: `ShareService.resolveForDownload→validateAndGetFile`(`share/ShareService.java:106-126`)가 토큰 존재→INVALID_LINK(404), `!isActive`→DISABLED(410), `isExpired`→EXPIRED(410), `status!=UPLOADED`→INVALID_LINK(404). 컨트롤러가 매 요청 호출(`ShareController.java:63-67`). **메타조회/다운로드 공통 검증 경로**라 메타조회 후 비활성화돼도 다운로드 차단.
- **인증 다운로드 소유자+UPLOADED 재검증**: `FileService.resolveForDownload`(`file/FileService.java:200-206`)가 `requireOwnedFile`(403/404) + `status!=UPLOADED→FILE_NOT_FOUND(404)`. 버전 다운로드 `resolveVersionForDownload`(`:261-266`) 소유자+버전존재(404). 미인증은 `SecurityConfig anyRequest().authenticated()`로 401, 공유 다운로드는 `/api/public/** permitAll`(`config/SecurityConfig.java:44`)로 401/403 미발생.
- **에러코드 HTTP 매핑**: `ErrorCode` INVALID_LINK=404, EXPIRED=410, DISABLED=410, FORBIDDEN=403, UNAUTHENTICATED=401, FILE_NOT_FOUND=404 — 계약 일치.
- **실측 단언 포함 확인(요청 항목)**:
  - `ShareFlowTest.create_share_then_public_resolve_and_disable`(`ShareFlowTest.java:84-94`): 비활성화 후 **다운로드 엔드포인트가 즉시 410 DISABLED**(TTL 대기 없이) 단언. → "비활성→즉시 410" 실재.
  - `ShareFlowTest.public_invalidToken_404_and_expired_410`(`:99-122`): 다운로드 경로 INVALID_LINK(404)·EXPIRED(410) 단언.
  - `FileFlowTest.download_nonOwner_403_and_missing_404`(`FileFlowTest.java:110-137`): 비소유 403 / 미존재 404 / 미인증 401 단언.
  - 전부 통과(`build/test-results/test/TEST-*.xml` failures=0).

### 6. 프런트 정합 — PASS (O-1 주석 제외)
- **인증 다운로드 = fetch-blob(Authorization)**: `downloadAuthenticated`(`frontend/src/lib/download.ts:8-27`)가 `fetch(path, { Authorization: Bearer })` → blob 저장. ExplorerPage `onDownload`(`ExplorerPage.tsx:117-126`)·VersionsDialog(`VersionsDialog.tsx:56`)가 `filesApi.downloadPath`/`versionDownloadPath`로 호출.
- **공유 공개 다운로드 = 비인증 링크**: `PublicSharePage`(`pages/PublicSharePage.tsx:75-81`)가 `<a href={file.downloadUrl} download>` — 비인증, 단순 네비게이션.
- **presigned JSON 분기 제거**: `filesApi`에 presigned downloadUrl 추출 단계 없음. `downloadPath`/`versionDownloadPath`가 게이트웨이 경로 문자열 반환(`api/files.ts:97-104`). 타입 `FileDetail.downloadUrl`·`PublicSharedFile.downloadUrl` 주석이 "게이트웨이 경로(presigned 아님)"로 갱신(`types/api.ts:123,192`).

## 런타임 데모/검증 절차서 (사람 승인 게이트 — qa 미실행)

전제: `docker compose up`은 **사람 승인 게이트**. 아래는 승인 후 사람/오케스트레이터가 수행하는 절차다.

- **(a) 공유 비활성화 즉시 차단(우회 0%)**
  1. 로그인 → 파일 업로드 → `POST /api/files/{id}/share`(body `{}` = 무기한) → token 확보.
  2. `GET /api/public/share/{token}/download` → 200 + 파일 다운로드(브라우저).
  3. `DELETE /api/share/{shareId}`(소유자 인증) → 204.
  4. **즉시**(TTL 대기 없이) `GET /api/public/share/{token}/download` 재호출 → **410 DISABLED** (`{"code":"DISABLED"}`) 확인. ← v1.6 핵심: presign TTL 잔존 우회 제거.
  5. (만료 변형) 과거 `expiredAt`로 공유 생성 → 다운로드 즉시 410 EXPIRED 확인.
- **(b) MinIO 직접 접근 차단(포트 비노출)**
  - host에서 `curl -v http://localhost:9000/minidrive/users/...`(서명 없이/옛 presign으로) → **연결 거부(Connection refused)** 또는 도달 불가 확인(9000 host published 아님).
  - host에서 `curl -v http://localhost:<HTTP_PORT>/_minio/minidrive/...` 직접 호출 → nginx `internal` 차단으로 **404** 확인.
- **(c) 대용량(>30s) 무중단 다운로드**
  - 내부 presign TTL(30s)보다 다운로드 소요가 긴 대용량 파일 다운로드 → **중단 없이 완료** 확인. (만료는 요청 시작 시점 SigV4 서명 시각 기준; 스트리밍 중 만료 무관. nginx `proxy_buffering off` + `proxy_read_timeout 3600s` `nginx.conf:100-103`.)
  - 단, presign 발급~nginx 재진입 사이 지연이 30s를 넘으면 `SignatureDoesNotMatch` 가능 — 정상 운영에선 즉시 소비라 비현실적이나, 매우 큰 부하 시 TTL 여유(예: 60s) 상향을 운영 합의 권장(관찰).
- **(d) 브라우저 네트워크 탭 presign 비노출**
  - 인증 다운로드(`/api/files/{id}/download`)·공유 다운로드(`/api/public/share/{token}/download`) 응답 헤더에 `X-Accel-Redirect`·`X-Amz-Signature`·presign 쿼리가 **나타나지 않음** 확인(nginx가 소비). 응답은 Content-Type/Content-Disposition + 바이트 스트림만.

## 사람 승인 게이트 목록

- `docker compose up`/`down -v`, 컨테이너 기동 — 위 (a)~(d) 런타임 절차 실행 전 필수.
- MinIO 포트 구성 변경(`ports:["9000:9000"]`→`expose`)은 인프라 담당 + 사람 승인 게이트(이미 compose에 반영됨; 기동 시 재확인).

## 사람 승인/위험 보고 (관찰)

- (문서·경미, O-1) `frontend/src/lib/download.ts:4-7` 주석이 "백엔드가 바디(스트림) 반환"이라 기술 — 실제는 nginx가 X-Accel-Redirect 소비 후 스트리밍. **코드 동작은 정상**(런타임에 nginx 경유 시 바디가 실려 blob 정상 수신). 주석만 부정확. → frontend 담당 주석 보정 권장(차단 아님).
- (관찰) 내부 presign TTL 30s: 대용량+고부하 시 발급~소비 지연 여유 부족 가능성. 위 (c) 런타임으로 검증; 필요 시 `INTERNAL_PRESIGN_TTL` 상향(계약 허용 범위 10~30s 초과 시 storage-layout 갱신 필요).
- (런타임 전제) X-Accel-Redirect 비노출·SigV4 Host 정합은 **nginx 경유 실제 기동**에서만 종단 확인 가능(MockMvc는 백엔드 출력까지만 검증). 위 (a)(b)(d) 절차로 완결.

## 런타임 E2E 검증 결과 (2026-06-17, docker compose 실기동)

`docker compose up -d --build` 후 `/tmp/v16_runtime_test.sh`(nginx 게이트웨이 :7915 경유)로 종단 검증 — **최종 15/15 PASS, 0 FAIL**.

| 시나리오 | 결과 |
| --- | --- |
| (a-1) 활성 공유 다운로드 200 + 바이트 일치 + Content-Disposition(한글 RFC5987) + Content-Type `text/plain;charset=UTF-8` | ✅ |
| (a-2) 공유 비활성화(`DELETE /api/share/{id}` 204) → **즉시 재다운로드 410 DISABLED**(TTL 대기 없음, 우회 0%) | ✅ |
| (b) host `localhost:9000` 직접 접근 연결 거부(포트 비노출, curl rc=7) | ✅ |
| (b) `/_minio/...` 외부 직접 호출 404(internal) | ✅ |
| (d) 공유 메타 응답에 presign 쿼리·MinIO 호스트 비노출, `downloadUrl`=게이트웨이 경로 | ✅ |
| (d) 다운로드 응답에 `X-Accel-Redirect` 클라이언트 비노출 | ✅ |
| 인증 파일 다운로드(Bearer) 200 + 바이트 일치 / 미인증 401 | ✅ |

**검증 중 발견·수정한 nginx 결함 2건**(상세는 `04-infra/compose-notes.md` "v1.6 런타임 검증 수정"):
1. `/_minio/` `Host` 헤더가 named upstream 이름(언더스코어)으로 나가 MinIO `invalid hostname` → `Host minio:9000` 고정.
2. 인증 다운로드 시 클라이언트 `Authorization` 헤더가 MinIO 로 전달돼 `multiple authentication types` → `proxy_set_header Authorization ""` 로 제거.

운영 주의: 단일 파일 bind-mount(`nginx.conf`)는 atomic-save 시 inode 교체로 reload 가 안 먹음 → `--force-recreate nginx` 로 적용. TTL 은 검증 전 30→60s 로 상향(순수 마진, 보안 무관).
