# api-contract — REST API (계약 v1.6)

공통 규칙
- Base: `/api`. 인증: `Authorization: Bearer {accessToken}`(Access ≈ 30분, Refresh ≈ 14일).
- 인가: 모든 file/folder 자원은 `owner_id == 요청자`만 접근. 위반 시 403.
- 에러 바디: `{ "code": "FILE_NOT_FOUND", "message": "...", "timestamp": "..." }`. 공통 코드: 400 VALIDATION, 401 UNAUTHENTICATED, 403 FORBIDDEN, 404 NOT_FOUND, 409 CONFLICT, 413 PAYLOAD_TOO_LARGE.
- 목록: `?page=0&size=20` (기본 size 20). 응답 `{ content:[], page, size, totalElements }`.
- 시간: ISO-8601 UTC 문자열.

## Authentication

### POST /api/auth/signup
- 요청: `{ email, password, nickname }`
- 응답 201: `{ id, email, nickname }`
- 에러: 409 EMAIL_TAKEN, 400 VALIDATION

### POST /api/auth/login
- 요청: `{ email, password }`
- 응답 200: `{ accessToken, refreshToken, user: { id, email, nickname } }`
- 에러: 401 BAD_CREDENTIALS

### POST /api/auth/refresh
- 요청: `{ refreshToken }`
- 응답 200: `{ accessToken, refreshToken }` (회전: 기존 refresh 무효화, 신규 발급)
- 에러: 401 INVALID_REFRESH (만료/회수/위조)

### POST /api/auth/logout
- 인증 필요. 요청: `{ refreshToken }` → 해당 refresh revoked=true
- 응답 204

## Folders

### GET /api/folders?parentId={id|null}
- 응답 200: `[{ id, parentId, name, createdAt }]` (해당 부모의 하위 폴더)

### POST /api/folders
- 요청: `{ parentId|null, name }`
- 응답 201: `{ id, parentId, name }`
- 에러: 409 NAME_CONFLICT(동일 부모 내 동명), 403(부모 비소유)

### PATCH /api/folders/{id}
- 요청: `{ name?, parentId? }` (이름 변경/이동)
- 응답 200: `{ id, parentId, name }`
- 에러: 403, 404, 409 NAME_CONFLICT, 400 CYCLIC_MOVE(자기 하위로 이동 금지)

### DELETE /api/folders/{id}
- 휴지통 이동(하위 폴더·파일 연쇄 DELETED). 응답 204. 에러 403/404.

## Files

### GET /api/files?folderId={id|null}&status=UPLOADED
- 응답 200: 페이지 `{ id, folderId, originalName, extension, fileSize, version, status, updatedAt }`
- 기본 status=UPLOADED. 휴지통 조회는 `status=DELETED`.
- 검색: `?name=`(부분일치), `?extension=`, 최근: `?sort=recent`.
- **folderId 파싱(v1.3)**: `folderId` 미지정/공백/리터럴 `"null"`은 루트(null)로 안전 처리. 숫자가 아닌 그 외 값은 400 VALIDATION(500/내부 메시지 누출 금지).
- **전역 휴지통(v1.3)**: `status=DELETED`를 숫자 folderId 없이 호출하면 소유자의 **모든 폴더에 걸친 DELETED 파일 전체**를 반환(폴더 무관 전역 보기). 숫자 folderId 지정 시 해당 폴더 한정.

### POST /api/files/upload
- 요청: multipart `file`, `folderId|null`
- 흐름: file 행 생성(PENDING→UPLOADING) → MinIO 저장 → UPLOADED + version 1 + file_version v1 기록.
- 응답 201: `{ id, folderId, originalName, extension, fileSize, version, status }`
- 부수효과: FILE_UPLOADED 이벤트 발행 + notification 생성.
- 에러: 403(폴더 비소유), 409 NAME_CONFLICT(동일 폴더 동명, 정책: 신규 버전 아님/충돌), 413, 실패 시 status=FAILED(재시도 가능).

### GET /api/files/{id}
- 응답 200: 파일 메타 + `downloadUrl`(v1.6: **게이트웨이 다운로드 경로**, presigned URL 아님). UPLOADED일 때 `downloadUrl = "/api/files/{id}/download"`, 아니면 null.
- 에러 403/404.

### GET /api/files/{id}/download  *(v1.6 신규 — 게이트웨이 경유 다운로드)*
- 인증: 필요 (Bearer Access Token)
- 인가: **매 요청 실시간** 소유자 검증 — `file.owner_id == 요청자` AND `file.status == UPLOADED`.
- 흐름: 인가 성공 시 바디 없는 200 + 헤더만 — `X-Accel-Redirect: /_minio/<internal-presign-path+query>`, `Content-Type`(v1.5 ContentTypes 규칙), `Content-Disposition: attachment; filename*=UTF-8''<encoded>`. nginx가 X-Accel-Redirect를 소비해 MinIO에서 직접 스트리밍(storage-layout v1.6 참조).
- 에러: 401 UNAUTHENTICATED / 403 FORBIDDEN(비소유) / 404 NOT_FOUND(없음/삭제됨/UPLOADED 아님).
- 불변식: internal presign(X-Accel-Redirect 값)은 브라우저에 노출되지 않음.

### POST /api/files/{id}/content  *(새 버전 업로드 = 수정)*
- 요청: multipart `file`
- 흐름: file_version 신규 행(version+1) 추가, file.object_key/version/file_size 갱신.
- 충돌 처리: 요청에 `baseVersion` 포함. `baseVersion != 현재 version`이면 409 CONFLICT(먼저 저장 우선) → 클라이언트가 덮어쓰기/병합 선택 후 `?overwrite=true` 재요청.
- 응답 200: `{ id, version, status }`. 부수효과: FILE_UPDATED 이벤트.

### PATCH /api/files/{id}
- 요청: `{ originalName?, folderId? }` (이름 변경/이동)
- 응답 200: 파일 메타. 부수효과: FILE_UPDATED. 에러 403/404/409.

### DELETE /api/files/{id}
- 휴지통 이동(status=DELETED, deleted_at 기록). 응답 204. 부수효과: FILE_DELETED 이벤트.

### GET /api/files/{id}/versions
- 응답 200: `[{ version, fileSize, createdAt }]` (version DESC)

### GET /api/files/{id}/versions/{version}/download  *(v1.6 게이트웨이 경유로 변경)*
- 인증: 필요 (Bearer Access Token). 인가: **매 요청 실시간** `file.owner_id == 요청자`.
- ~~응답 `{ "downloadUrl": "..." }`(presigned)~~ → v1.6: 이 경로 자체가 **게이트웨이 다운로드 경로**다. 인가 후 바디 없는 200 + 헤더(`X-Accel-Redirect`, `Content-Type`, `Content-Disposition`)만 반환, nginx가 해당 `versions/{fileId}/v{version}` object를 MinIO에서 직접 스트리밍.
- 프런트는 이 URL을 `<a href>`/리다이렉트로 직접 호출(별도 downloadUrl 추출 단계 제거).
- 에러: 401 / 403(비소유) / 404(파일·버전 없음).

### POST /api/files/{id}/restore
- 동작 분기:
  - 휴지통 파일이면 status=DELETED→UPLOADED 복원, deleted_at=null.
  - 바디 `{ version }` 있으면 해당 버전을 새 버전으로 복사 생성(현재로 복구). 응답 200 `{ id, version }`. 부수효과 FILE_UPDATED.

### DELETE /api/files/{id}/permanent  *(휴지통 영구 삭제)*
- DELETED 상태만 허용. MinIO object + file_version 정리. 응답 204. 에러 409(휴지통 아님).

## Share

### POST /api/files/{id}/share
- 요청: `{ expiredAt?|null }`
- 응답 201: `{ id, token, url, expiredAt, isActive }`
- 부수효과: SHARE_CREATED 이벤트. 에러 403/404.

### GET /api/public/share/{token}  *(비인증 공개 API — 읽기 전용, 메타 조회)*  ← v1.2 / v1.6 수정
- SecurityConfig에서 인증 없이 permitAll. `/api` 프리픽스라 nginx가 이미 백엔드로 프록시(별도 `/share` 프록시 블록 불요).
- 유효(`is_active && 미만료 && file.status==UPLOADED`)하면 `{ id, originalName, extension, fileSize, downloadUrl }`. 에러 404 INVALID_LINK / 410 EXPIRED / 410 DISABLED.
- **v1.6 변경**: `downloadUrl`은 더 이상 presigned URL이 아니라 **게이트웨이 다운로드 경로** `"/api/public/share/{token}/download"`(상대경로). 브라우저에 presigned URL을 주지 않는다. (메타 조회 시점의 유효성은 참고용일 뿐 — 실제 인가는 아래 download 엔드포인트가 **매 요청 재검증**하므로, 메타 조회 후 비활성화돼도 다운로드는 차단된다.)
- **사람이 여는 공유 페이지**는 SPA 경로 `/share/{token}`(정적, `location /` 폴백)이며, 이 페이지가 위 공개 API를 호출한다. → 경로 충돌 제거.

### GET /api/public/share/{token}/download  *(v1.6 신규 — 비인증 공개 다운로드, 게이트웨이 경유)*
- 인증: 불필요(permitAll). 인가: **매 다운로드 요청마다 실시간 재검증** — token 유효(존재) AND `is_active == true` AND 미만료(`expired_at == null || now < expired_at`) AND `file.status == UPLOADED`. → 비활성화/만료가 **즉시 반영**(presign TTL 우회 제거).
- 흐름: 인가 성공 시 바디 없는 200 + 헤더만 — `X-Accel-Redirect: /_minio/<internal-presign-path+query>`, `Content-Type`(v1.5 ContentTypes 규칙), `Content-Disposition: attachment; filename*=UTF-8''<encoded>`. nginx가 X-Accel-Redirect를 소비해 MinIO에서 직접 스트리밍.
- 에러 매핑: 404 INVALID_LINK(토큰 없음/파일 없음) / 410 DISABLED(`is_active=false`) / 410 EXPIRED(만료) / 404 INVALID_LINK(file.status != UPLOADED). (인증 불필요 경로이므로 401/403은 발생하지 않음.)
- 불변식: internal presign(X-Accel-Redirect 값)은 브라우저에 노출되지 않음.

### GET /api/shares  *(내 공유 링크 목록 — v1.4)*
- 인증 필요. 요청자가 소유한 파일의 공유 링크 전체.
- 응답 200: `[{ id, fileId, fileName, token, url, expiredAt, isActive, createdAt }]`
  - `url` = 상대경로 `"/share/{token}"`(호스트는 프런트가 `window.location.origin`으로 조합).
  - 상태(활성/만료/비활성)는 `isActive`+`expiredAt`로 프런트가 계산.
- 정렬: createdAt DESC.

### DELETE /api/share/{id}
- 링크 비활성화(is_active=false). 응답 204. 에러 403/404. (관리 페이지의 "비활성화"가 이 엔드포인트 사용)

## Notifications  *(실시간 보조 — 미수신분 조회)*

### GET /api/notifications?unread=true
- 응답 200: `[{ id, type, message, isRead, createdAt }]`
### PATCH /api/notifications/{id}/read → 204

## 계약 v1.1 정합 메모 (2026-06-16, Phase B 빌드 반영)

빌더 구현 선택을 단일 출처로 확정한다.

- ~~**다운로드 = presigned URL(5분)** 확정. `GET /api/files/{id}`는 UPLOADED일 때 `downloadUrl` 포함(아니면 null). `GET /api/files/{id}/versions/{version}/download`는 `{ "downloadUrl": "..." }` 래핑 객체.~~ **→ v1.6에서 게이트웨이 경유로 전면 개정. 아래 "계약 v1.6 정합 메모" 참조.**
- **GET /share/{token} 응답 형태 확정**: `{ id, originalName, extension, fileSize, downloadUrl }` (비인증 읽기전용). 백엔드/프런트 이 필드명에 일치시킬 것 — qa 교차 검증 대상.
- **공유 url** 은 상대경로 `"/share/{token}"`. 호스트 조합은 프런트/Nginx 담당.
- **검색 범위 = 소유자 전역**: `name`/`extension` 지정 시 folderId 무시(전역). 폴더 한정 검색은 범위 외.
- **restore 우선순위**: 휴지통 파일에 `{ version }`을 함께 보내면 **버전 복구가 우선**. version 없으면 휴지통 복원.
- **폴더 휴지통**: 폴더 삭제 시 소속 파일은 DELETED 보존, 폴더 행은 제거. **폴더 복구는 범위 외**(파일 단위 복구만).

## 계약 v1.2 정합 메모 (2026-06-16, qa 통합 결함 반영)
- **I-2 공유 라우팅 충돌 해소**: 공개 공유 API를 `GET /api/public/share/{token}`로 이동(위 참조). 공유 링크 `url` = `"/share/{token}"`(SPA 페이지). nginx 추가 블록 불요.
- **I-1 공유 응답 필드**: 첫 키는 `id`(파일 id)로 통일. 프런트 타입에 `id`/`extension` 포함.
- **I-3 다운로드 호스트**: storage-layout.md v1.2 참조(presign 공개 endpoint 분리). 모든 `downloadUrl`은 브라우저가 해석 가능한 공개 호스트로 발급.

## 계약 v1.6 정합 메모 (2026-06-17, 공유 다운로드 게이트웨이 경유 개정)

공유 링크 비활성화 우회(presign TTL 잔존) 결함을 차단한다. 다운로드를 nginx 게이트웨이 단일 길목으로 통과시키고 매 요청 백엔드 실시간 인가.

- **채택 메커니즘 = X-Accel-Redirect**(근거: storage-layout v1.6). 백엔드는 인가 후 바디 없이 헤더만 반환, nginx가 MinIO에서 직접 스트리밍(백엔드 대역폭 비경유). auth_request는 대안 기록만.
- **신규/변경 다운로드 엔드포인트(게이트웨이 상대경로)**:
  - `GET /api/public/share/{token}/download` (신규, 비인증, 매 요청 is_active/만료/UPLOADED 재검증). 에러 404 INVALID_LINK / 410 DISABLED / 410 EXPIRED.
  - `GET /api/files/{id}/download` (신규, 인증, 매 요청 소유자+UPLOADED 검증).
  - `GET /api/files/{id}/versions/{version}/download` (기존 경로 유지, presigned 반환 → 게이트웨이 스트리밍으로 의미 변경).
- **`downloadUrl` 필드 의미 변경**: presigned URL → **게이트웨이 다운로드 경로**(상대경로). 더 이상 브라우저에 MinIO presigned URL을 노출하지 않는다.
  - `GET /api/public/share/{token}` 의 `downloadUrl` = `"/api/public/share/{token}/download"`.
  - `GET /api/files/{id}` 의 `downloadUrl` = `"/api/files/{id}/download"`(UPLOADED일 때, 아니면 null).
- **응답 헤더**: Content-Type(v1.5 ContentTypes 규칙 보존, 텍스트 charset=UTF-8) + Content-Disposition(한글 RFC5987 인코딩)을 백엔드 컨트롤러가 X-Accel-Redirect 응답에 직접 설정.
- **NFR/보안**: `MINIO_PUBLIC_ENDPOINT` 및 브라우저-facing presign 폐기. compose에서 MinIO 9000 host published 제거(`expose`만) — **인프라 사람 승인 게이트**. (storage-layout v1.6 참조)
- **영향 모듈**: 백엔드(다운로드 컨트롤러 3종 + StorageService presign 내부 전용화), 프런트(share/files/versions의 downloadUrl 소비처 — presigned URL 추출 대신 게이트웨이 경로를 직접 호출), 인프라(nginx `/_minio/` internal location 추가 + MinIO 포트 비노출), qa(비활성화 즉시 반영·internal presign 비노출 교차 검증).

## 확인 필요 (잔여)
- 없음 — v1.1/v1.2/v1.6 정합 메모로 빌더 보고 공백 + qa 결함 해소.
