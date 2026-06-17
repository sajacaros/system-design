# 검증 모듈: 통합 위험 (백엔드 ↔ 프런트 ↔ 인프라 경계면)
검증일: 2026-06-16 · 검증자: qa-reviewer · 방법: 정적 분석(grep/read) + `docker compose config`(비파괴)

## 판정: 반려 (조건부 — 시연 차단 이슈 2건)

우선순위 1 통합 위험 2건 중 1건은 통과, 1건은 경미한 필드명 불일치. 추가로 정적 분석에서
**시연 차단 수준의 통합 결함 2건**(공유 라우팅 충돌, presigned URL 호스트)을 발견하여 반려한다.

## 위반 목록

| # | 항목 | 증거(파일:라인) | 심각도 | 담당 |
| --- | --- | --- | --- | --- |
| I-1 | **GET /share/{token} 응답 필드명 불일치**. 계약 v1.1은 `{ id, originalName, extension, fileSize, downloadUrl }`. 백엔드 DTO는 첫 필드가 `fileId`(→JSON `"fileId"`), 계약의 `id`와 불일치. 프런트 타입 `PublicSharedFile`은 `{ originalName, fileSize, downloadUrl }`만 정의 — `id`/`fileId`·`extension` 누락. | 백엔드 `share/dto/ShareDtos.java:14-20` (`Long fileId`), 프런트 `types/api.ts:179-183` | 중 | 백엔드(우선) + 프런트 |
| I-2 | **nginx `/share/{token}` 라우팅 충돌**. nginx에 `/api/`·`/ws` 블록만 존재, `/share/` 블록 없음 → `location /`가 SPA `index.html`로 폴백. 브라우저가 `/share/{token}` 접속 시 SPA가 뜨고, 그 SPA가 다시 `GET /share/{token}`(absolute, /api 미경유)을 fetch하면 **또 index.html(HTML)이 반환**됨. client.ts는 `content-type`이 json 아니면 `undefined` 반환 → 공유 페이지에 파일 정보가 안 뜸. | `nginx/nginx.conf:22-52`(/share 프록시 부재), 프런트 `api/share.ts:17-21`(absolute GET /share/{token}), `App.tsx:16`(SPA route /share/:token), `api/client.ts:124,144-148` | **높음** | 인프라(+설계 합의) |
| I-3 | **presigned 다운로드 URL 호스트가 브라우저에서 해석 불가**. presigner endpoint = `MINIO_ENDPOINT=http://minio:9000`(도커 내부 DNS). 발급된 URL 호스트가 `minio`라 호스트/브라우저에서 접속 불가 → 파일 상세 다운로드·버전 다운로드·공유 다운로드 **전부 실패**(도커 기동 시). | `storage/S3Config.java:21,34`(endpointOverride=props.endpoint), `storage/S3StorageService.java:92-102`(presignGet), `docker-compose.yml:86`(MINIO_ENDPOINT=http://minio:9000), `storage/StorageProperties.java:13` | **높음** | 인프라 + 백엔드 |

## 확인된 통과 항목

- **[우선순위 1-1 — 통과] SockJS 엔드포인트 정합.** 백엔드 `sync/WebSocketConfig.java:31`이 `registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS()` 등록. 프런트 `realtime/stompClient.ts:32`가 `webSocketFactory: () => new SockJS("/ws")` 사용. nginx `/ws` 프록시(`nginx.conf:34-45`)는 Upgrade/Connection 헤더 + 3600s 타임아웃으로 STOMP/SockJS 핸드셰이크 지원. CONNECT 프레임 JWT 검증(`WebSocketConfig.java:54-66`)도 구현. **전송 방식 일치 — 양쪽 수정 불요.**
- **[우선순위 1-2 — 구조 일치] downloadUrl 키 일치.** 계약·백엔드·프런트 모두 `downloadUrl`. PublicShareResponse가 `downloadUrl`을 반환(`ShareService.java:81-84`), 프런트가 `file.downloadUrl` 소비(`PublicSharePage.tsx:76`). I-1은 메타 식별자 필드명(`id` vs `fileId`)·`extension` 누락에 한정.
- **버전 다운로드 응답 래핑 일치.** 계약 `{ "downloadUrl": "..." }`, 백엔드 `DownloadUrlResponse(String downloadUrl)`, 프런트 `lib/download.ts:16-20`이 JSON이면 `body.downloadUrl` 사용 + 바이너리 스트림 폴백(이중 모드). 일치.
- **WS 캐시 무효화 경로.** 프런트 `realtime/useRealtime.ts:24-47`가 FILE_*/SHARE_CREATED 수신 시 files 목록·파일 상세·versions·notifications invalidate. 1초 이내 반영 경로 존재.
- **`docker compose config` 통과**(exit 0, 6 서비스/3 볼륨/1 네트워크). 필수 시크릿 `${VAR:?}` 가드 정상.
- 동일 출처(`/api`, `/ws` 상대경로)로 CORS 하드코딩 없음.

## 사람 승인/위험 보고

- **I-2 / I-3은 인프라 기동 전 반드시 해소**할 것. 둘 다 정적 분석으로 확정된 시연 차단 결함이며, "인프라 기동 후 확인"이 아니라 **기동 전 수정 대상**이다.
- I-2 해소안(택1, 설계 합의 필요): (a) nginx에 `location = /share/...` 정규식으로 `Accept: application/json`일 때만 백엔드 프록시 — 취약·권장 안 함, (b) **백엔드 공개 조회 경로를 `/api/public/share/{token}` 등 `/api` 하위로 이동**하고 프런트 `absolute` 호출을 그에 맞춤(가장 깔끔, 계약 v1.1 수정 동반), (c) nginx에서 `/share/`를 백엔드로 프록시하고 SPA 공유 화면 경로를 분리(예: `/s/{token}`). → 설계자·인프라·프런트 3자 합의 필요.
- I-3 해소안: presigner 전용 **공개 endpoint**(예: `http://localhost:9000` 또는 nginx 경유 `/minio/`)를 별도 환경변수로 분리해 presignGet에만 적용. 현재는 단일 `storage.endpoint`로 내부/외부를 공유 — 도커 환경에서 외부 접근 불가.
