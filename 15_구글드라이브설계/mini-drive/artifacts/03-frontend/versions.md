## 화면: versions
버전 히스토리 조회, 특정 버전 다운로드, 특정 버전으로 복구.

## 사용 엔드포인트/이벤트
- GET /api/files/{id}/versions → [{ version, fileSize, createdAt }] (DESC)
- GET /api/files/{id}/versions/{version}/download → presigned URL 또는 스트림
- POST /api/files/{id}/restore (body { version }) → { id, version } (현재로 복구)
- FILE_UPDATED 이벤트 → 목록/상세/버전 invalidate

## 계약 대조
- [x] 응답 타입 일치 — FileVersionItem { version, fileSize, createdAt }, RestoreResponse { id, version }.
- [x] 복구는 body { version } 포함하여 POST /restore 호출(휴지통 복원과 동일 엔드포인트의 분기).
- [x] 인증/토큰 갱신 처리.
- [x] 복구 성공 시 files + file + versions 캐시 무효화.

## 빌드·타입체크
- 명령: `pnpm build`
- 결과: 통과

## 확인 필요
- 버전 다운로드 GET /versions/{version}/download 의 응답 형태(presigned JSON vs 바이너리 스트림) 미명시 → `downloadAuthenticated`가 content-type으로 분기 처리(JSON이면 downloadUrl 오픈, 아니면 blob). 확인 필요.

---

## v1.6 변경 (2026-06-17) — 버전 다운로드 게이트웨이 경유

### 계약 핵심 변경
- `GET /api/files/{id}/versions/{version}/download`: 기존 `{ downloadUrl }`(presigned JSON) 반환 → v1.6 경로 자체가 **게이트웨이 다운로드 경로**. 인가(소유자) 후 X-Accel-Redirect 로 해당 버전 object MinIO 직접 스트리밍. **Bearer Access Token 필요**.

### 변경한 다운로드 트리거 위치 + 인증 처리 방식
- `src/components/explorer/VersionsDialog.tsx` `onDownload`: 변경 없이 `downloadAuthenticated(filesApi.versionDownloadPath(id, version))` 그대로 사용(이미 인증 fetch-blob 경로).
- `src/lib/download.ts` `downloadAuthenticated`: presigned JSON 분기(content-type==application/json이면 downloadUrl 오픈) **제거**. v1.6는 항상 바이너리 스트림이므로 blob 저장만 수행. `Content-Disposition` 파일명 파싱 추가.
- 미사용이 된 `openDownloadUrl` 헬퍼 삭제.

### 계약 대조
- [x] presigned downloadUrl 추출 단계 제거 — 게이트웨이 경로 직접 호출.
- [x] 인증 다운로드 Bearer 헤더 전달(fetch-blob).
- [x] 계약에 없는 필드 미사용.

### 빌드·타입체크
- 명령: `cd frontend && pnpm build`
- 결과: 통과.
