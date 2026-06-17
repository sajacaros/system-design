## 모듈: share

## 구현 범위
- 엔드포인트:
  - `POST /api/files/{id}/share`(expiredAt?|null) → 201 {id,token,url,expiredAt,isActive}
  - `GET /api/public/share/{token}` (비인증 공개, 읽기전용) → {id,originalName,extension,fileSize,downloadUrl}  ← v1.2
  - `DELETE /api/share/{id}` → 비활성화(is_active=false), 204
  - `GET /api/shares` (인증 필요) → 200 `[{id,fileId,fileName,token,url,expiredAt,isActive,createdAt}]`  ← v1.4 (내 공유 목록)
- 엔티티/테이블: `share_link`(token URL-safe 랜덤 32바이트 Base64url, expired_at, is_active).
- 이벤트: SHARE_CREATED.
- 유효 조건: is_active && (expired_at IS NULL || expired_at > now()). UPLOADED 파일만 공유 대상.

## 계약 대조
- [x] 응답 필드 일치
- [x] 인가: 공유 생성/비활성화는 파일 owner만(403); GET /share/{token}은 공개
- [x] 404 INVALID_LINK / 410 EXPIRED / 410 DISABLED 분기
- [x] presigned 단기(5분) 다운로드

## 빌드·테스트
- 명령: `./gradlew build` (전체 재실행)
- 결과: 통과 14 / 실패 0 (전체). ShareFlowTest 3건 모두 통과 — 공개 경로 `/api/public/share/{token}` 갱신, 응답 `id`/`extension` 단언 추가.

## v1.2 결함 수정 (2026-06-16, qa I-1/I-2 반영, 재검증 완료)
- **I-2 경로 이동**: 공개 공유 조회 `GET /share/{token}` → `GET /api/public/share/{token}`.
  - `ShareController.resolve` 매핑 변경, `SecurityConfig`에서 `/share/**` permitAll 제거 → `/api/public/**` permitAll. 그 외 `/api/**`는 인증 유지.
  - 공유 링크 응답의 `url`은 계약대로 `"/share/{token}"`(SPA 페이지 경로) 유지 — API 경로와 분리.
- **I-1 응답 필드**: `PublicShareResponse` 첫 필드 `fileId` → `id`. 최종 형태 `{ id, originalName, extension, fileSize, downloadUrl }`. (positional 생성자라 ShareService 변경 불요.)
- **I-3 다운로드 호스트**: 공유 다운로드 `downloadUrl`은 공개 presigner(`MINIO_PUBLIC_ENDPOINT`) 사용으로 브라우저 해석 가능(files 모듈 참조).
- 테스트 갱신: ShareFlowTest 4곳 경로 `/api/public/share/...`로 변경 + `$.id`/`$.extension` 단언 추가.

## v1.4 기능 확장 (2026-06-16, 내 공유 링크 목록 조회)
- **신규 엔드포인트**: `GET /api/shares` (인증 필요). 신규 컨트롤러 없이 기존 `ShareController`에 메서드 1개 추가.
- **응답 형태**: `[{ id, fileId, fileName, token, url, expiredAt, isActive, createdAt }]`
  - `id` = share_link.id, `fileId` = file.id, `fileName` = file.original_name.
  - `url` = `"/share/{token}"` (상대경로, 호스트는 프런트가 `window.location.origin`으로 조합).
  - `expiredAt`/`createdAt` = ISO-8601 문자열(null 가능: expiredAt은 무기한 시 null).
  - 정렬: `createdAt DESC`.
- **인가**: `ShareLinkRepository.findOwnedSharesWithFile(ownerId)` 가 share_link ⨝ file (file_id) 조인 후 `f.owner_id = :ownerId` 필터 → 남의 공유는 쿼리 단계에서 제외(노출 불가). 인증 주체는 `CurrentUser.id()`.
- **재사용 범위**: `ShareService.listOwnShares`, `ShareDtos.ShareListItem`(record), 리포지토리 쿼리 1개 추가. POST/GET public/DELETE 동작 변경 없음.
- **회귀 확인**: 기존 ShareFlowTest 3건 + 신규 3건 = 6건 전부 통과(`TEST-...ShareFlowTest.xml` tests=6 failures=0 errors=0).

### 계약 대조 (v1.4)
- [x] 응답 필드 `{id,fileId,fileName,token,url,expiredAt,isActive,createdAt}` 일치
- [x] `url` = 상대경로 `/share/{token}`
- [x] 정렬 createdAt DESC
- [x] owner 스코프 인가(타 사용자 공유 비노출, 테스트로 검증)
- [x] 미인증 401 UNAUTHENTICATED(테스트로 검증)
- [x] 기존 엔드포인트 회귀 없음

### 신규 테스트
- `listMine_returns_only_owner_shares_sorted_desc`: 2건만 반환, 타 사용자 공유 제외, createdAt DESC 순서.
- `listMine_url_is_relative_share_path`: url == `/share/{token}`.
- `listMine_unauthenticated_401`: Authorization 헤더 없으면 401.

## 빌드·테스트 (v1.4)
- 명령: `./gradlew build`
- 결과: BUILD SUCCESSFUL. ShareFlowTest 6/6 통과.

## 확인 필요
- 없음.

## v1.6 다운로드 게이트웨이 경유 개정 (2026-06-17, 공유 비활성화 우회 차단)
- **신규 엔드포인트**: `GET /api/public/share/{token}/download` (비인증, permitAll). `ShareController`에 메서드 1개 추가.
  - **매 요청 실시간 재검증**: `ShareService.resolveForDownload(token)` — token 존재 AND `is_active=true` AND 미만료 AND `file.status=UPLOADED`. 기존 `resolve`의 검증을 `validateAndGetFile(token)`로 추출해 메타조회/다운로드가 동일 규칙 공유.
  - 에러 매핑: 404 INVALID_LINK(토큰/파일 없음, status!=UPLOADED) / 410 DISABLED(`is_active=false`) / 410 EXPIRED(만료). 401/403 미발생(permitAll).
- **응답 = X-Accel-Redirect(바디 없는 200)**: 인가 성공 시 `GatewayDownload.redirect(objectKey, originalName)` →
  - `X-Accel-Redirect: /_minio/<objectPath>?<presignedQuery>` (nginx internal prefix `/_minio/` 고정).
  - `Content-Type`: v1.5 ContentTypes 규칙(텍스트류 `; charset=UTF-8`, 바이너리 미부착, 미상 `application/octet-stream`).
  - `Content-Disposition: attachment; filename*=UTF-8''<RFC5987 percent-encoded originalName>`.
  - 바디 비움. 내부 presign(SigV4 쿼리)은 X-Accel-Redirect 헤더에만 실리며 nginx가 소비 → 브라우저 절대 비노출.
- **`downloadUrl` 의미 변경**: `GET /api/public/share/{token}` 응답의 `downloadUrl`이 presigned URL → 게이트웨이 경로 `"/api/public/share/{token}/download"`(상대경로). 메타조회 시점 유효성은 참고용, 실제 인가는 download가 매 요청 재검증.
- **우회 0% 보장**: 공유 비활성화/만료가 즉시 반영(presign TTL 잔존 창 제거) — download 엔드포인트가 매번 백엔드 인가를 거침.
- **정리**: `ShareService`가 더 이상 `StorageService`/`StorageProperties`/`ContentTypes`에 의존하지 않음(presign 호출 제거). 게이트웨이 응답은 `GatewayDownload`(storage 패키지, 신규)가 단일 책임.
- **수정 파일**: `share/ShareController.java`, `share/ShareService.java`, `share/dto/ShareDtos.java`(doc), `storage/GatewayDownload.java`(신규, files와 공유).

### 계약 대조 (v1.6)
- [x] `GET /api/public/share/{token}/download` 추가, 매 요청 is_active/만료/UPLOADED 재검증
- [x] 404 INVALID_LINK / 410 DISABLED / 410 EXPIRED 매핑
- [x] X-Accel-Redirect `/_minio/...` + Content-Type(v1.5) + Content-Disposition(RFC5987) 헤더, 바디 없음
- [x] `downloadUrl` = 게이트웨이 경로(presigned 아님)
- [x] internal presign 브라우저 비노출

### 신규/갱신 테스트 (ShareFlowTest)
- `create_share_then_public_resolve_and_disable`: (a) `downloadUrl == /api/public/share/{token}/download`, (b) download가 X-Accel-Redirect(`/_minio/` 시작)+`Content-Type: text/plain; charset=UTF-8`+`Content-Disposition: attachment; filename*=UTF-8''s.txt`+빈 바디, (c) 비활성화 후 download **즉시 410 DISABLED**(우회 0%).
- `public_invalidToken_404_and_expired_410`: download 경로도 404 INVALID_LINK / 410 EXPIRED 즉시 반영.

## 빌드·테스트 (v1.6)
- 명령: `./gradlew build` / `./gradlew test --rerun-tasks`
- 결과: BUILD SUCCESSFUL. ShareFlowTest 6/6 통과(전체 27/0).

## 확인 필요 (v1.6)
- 없음. nginx `/_minio/` internal location + MinIO 9000 비노출은 인프라 담당(사람 승인 게이트).
