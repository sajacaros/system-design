## 화면: explorer
폴더 계층 탐색(브레드크럼), 파일 목록, 새 폴더, 이름변경/이동/삭제, 다운로드, 검색.

## 사용 엔드포인트/이벤트
- GET /api/folders?parentId={id|null} → [{ id, parentId, name, createdAt }]
- POST /api/folders, PATCH /api/folders/{id}, DELETE /api/folders/{id}
- GET /api/files?folderId=&status=UPLOADED&name=&extension=&sort=recent&page=&size= → Page<FileListItem>
- GET /api/files/{id} → 메타 + downloadUrl (다운로드)
- PATCH /api/files/{id} (이름변경/이동), DELETE /api/files/{id} (휴지통 이동)

## 계약 대조
- [x] 응답 타입 일치 — FileListItem { id, folderId, originalName, extension, fileSize, version, status, updatedAt } 그대로 사용.
- [x] 페이지 응답 { content, page, size, totalElements } 사용, 페이지네이션 구현.
- [x] 검색: name(부분일치)/extension/sort=recent 쿼리 파라미터. 검색 중 폴더 목록 숨김.
- [x] 인증/토큰 갱신 처리 (공용 client)
- [x] 이동 시 폴더는 parentId, 파일은 folderId 필드 사용(계약대로 구분).

## 빌드·타입체크
- 명령: `pnpm build`
- 결과: 통과

## 확인 필요
- 다운로드: GET /api/files/{id} 응답의 `downloadUrl`(presigned)을 새 탭으로 연다. 계약은 "presigned URL 또는 스트리밍 경로 중 택1, 기본 presigned". downloadUrl이 없을 경우 스트리밍 fallback(`/api/files/{id}` blob)도 준비 — 정확한 스트리밍 경로는 인프라/백엔드 확인 필요.
- 파일 검색 시 folderId 범위(현재 폴더 한정 vs 전체)는 계약에 미명시 → 현재는 전역 검색(folderId 미포함)으로 처리. 확인 필요.

---

## v1.6 변경 (2026-06-17) — 파일 다운로드 게이트웨이 경유

### 계약 핵심 변경
- `GET /api/files/{id}` 응답의 `downloadUrl` 의미 변경: presigned URL → **게이트웨이 경로** `"/api/files/{id}/download"`(UPLOADED일 때, 아니면 null).
- 신규 인증 다운로드 엔드포인트 `GET /api/files/{id}/download` — 매 요청 백엔드가 소유자+`status==UPLOADED` 검증 후 X-Accel-Redirect 스트리밍. **Bearer Access Token 필요**.

### 변경한 다운로드 트리거 위치 + 인증 처리 방식
- `src/pages/ExplorerPage.tsx` `onDownload`: 기존 `filesApi.detail()` 호출 후 `window.open(detail.downloadUrl)` 분기 제거.
  - v1.6 다운로드 경로는 **인증 필수**다. 단순 `<a href>`/`window.open`으로는 `Authorization: Bearer` 헤더가 실리지 않아 401 → `downloadAuthenticated(filesApi.downloadPath(id))`로 통일.
  - `downloadAuthenticated`(`src/lib/download.ts`): `tokenStore`의 access token으로 `fetch` → blob 수신 → `URL.createObjectURL` + 가상 `<a download>` 클릭 저장.
  - 파일명은 응답 `Content-Disposition`(RFC5987 `filename*=UTF-8''...`)에서 파싱하여 저장명에 반영.
- `src/api/files.ts`: `downloadPath(id)` 헬퍼 추가(`/api/files/{id}/download`).

### 타입
- `FileDetail.downloadUrl`: `string` → `string | null`(v1.6: UPLOADED 아니면 null). 게이트웨이 경로 의미 주석 반영.

### 계약 대조
- [x] presigned URL 직접 오픈 제거 — 인증 게이트웨이 경로를 fetch-blob로 호출.
- [x] 인증 다운로드에 Bearer 헤더 전달(토큰 만료 시 client refresh 흐름과 별개로 download.ts가 현재 access 사용).
- [x] 계약에 없는 필드 미사용.

### 빌드·타입체크
- 명령: `cd frontend && pnpm build`
- 결과: 통과.
