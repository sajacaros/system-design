## 화면: share
공유 링크 생성/만료지정/비활성화 + /share/{token} 공개 다운로드 화면.

## 사용 엔드포인트/이벤트
- POST /api/files/{id}/share (body { expiredAt?|null }) → { id, token, url, expiredAt, isActive }
- DELETE /api/share/{id} → 204 (비활성화)
- GET /api/public/share/{token} (비인증 공개 API, 계약 v1.2) → { id, originalName, extension, fileSize, downloadUrl }
- SHARE_CREATED 이벤트 → 해당 파일 상세 invalidate (useRealtime)

## 계약 대조
- [x] 응답 타입 일치 — ShareLink { id, token, url, expiredAt, isActive }.
- [x] 공개 조회 응답 타입 일치 — PublicSharedFile { id, originalName, extension, fileSize, downloadUrl } (계약 v1.2 I-1 반영).
- [x] 만료: datetime-local → ISO-8601 UTC 변환, 비우면 null(무기한).
- [x] 비인증 공개: client `auth:false`로 `/api/public/share/{token}` 호출(API_BASE=/api 하위 상대경로 → nginx가 백엔드 프록시, 라우팅 충돌 없음).
- [x] 에러 매핑: INVALID_LINK(404)/EXPIRED(410)/DISABLED(410) 코드→메시지.

## 빌드·타입체크
- 명령: `pnpm build` (= `tsc --noEmit && vite build`)
- 결과: 통과 (백엔드 미가동, 2026-06-16 재검증) — tsc 타입체크 0 error, vite build 성공(dist/assets/index-*.js 430.53 kB).

## qa 반려 수정 이력 (2026-06-16, 계약 v1.2)
- **I-2 공유 공개 API 경로 변경**: `GET /share/{token}`(절대경로, /api 미경유) → `GET /api/public/share/{token}`(API_BASE 하위 상대경로). `api/share.ts`의 `publicGet`에서 `absolute:true` 제거, 경로 `/public/share/${token}`로 교체. SPA 라우트 `/share/:token`(App.tsx)·SPA 페이지(PublicSharePage)는 그대로 유지하며, 페이지 내부에서 위 공개 API를 호출. → nginx `/share/` 프록시 블록 불요, 라우팅 충돌 해소.
- **I-1 공유 응답 타입 정합**: `types/api.ts`의 `PublicSharedFile`에 `id`(number)·`extension`(string|null) 추가 → 계약 `{ id, originalName, extension, fileSize, downloadUrl }`와 일치. PublicSharePage는 originalName/downloadUrl만 소비하므로 화면 영향 없음.

## 확인 필요
- 없음 (계약 v1.2 정합 메모로 공개 API 경로·응답 필드 확정).

---

## 기능 확장 (2026-06-16, 계약 v1.4)

확장 2건:
1. 공유 URL 절대화(origin 조합) + 복사 버튼 — 공유 URL 노출 지점 전체 적용.
2. 공유 링크 관리 페이지(`/shares`) + 네비게이션 진입점.

### 추가 사용 엔드포인트
- `GET /api/shares` → 200 `[{ id, fileId, fileName, token, url, expiredAt, isActive, createdAt }]` (신규, 관리 페이지 목록, createdAt DESC).
- `DELETE /api/share/{id}` → 204 (관리 페이지 "비활성화"가 사용).
- `POST /api/files/{id}/share` (기존) — 생성 결과 URL 도 절대화 적용.

### 변경/추가 내역
- 절대 URL 헬퍼 `shareUrl(relative)`: `window.location.origin` + 상대경로(`/share/{token}`) 결합.
  이미 `http(s)://` 인 값은 그대로 반환(방어적). `frontend/src/lib/utils.ts`.
- 상태 계산 헬퍼 `computeShareStatus(isActive, expiredAt)`: 비활성 > 만료 > 활성.
  만료는 `expiredAt <= now` 판정. `frontend/src/lib/utils.ts`.
- `ShareDialog.tsx`: 결과 URL 표시·복사를 `shareUrl(...)` 로 통일(인라인 origin 조합 제거 → 헬퍼 일원화).
- 재사용 복사 버튼 `CopyLinkButton` (`frontend/src/components/share/CopyLinkButton.tsx`):
  클립보드 복사 + 성공 시 체크 아이콘 토글. 실패 시 toast.
- 관리 페이지 `SharesPage` (`frontend/src/pages/SharesPage.tsx`): 표(overflow-x-auto + min-w 로 모바일 가로 스크롤).
  컬럼 — 파일명(fileName), 절대 공유 URL(새 탭 링크 + 복사), 상태 뱃지(활성/만료/비활성),
  만료(없으면 "무기한"), 생성일, 비활성화 동작. 로딩/에러(+재시도)/빈 목록 처리.
- 훅 `useShare.ts`:
  - `useShares()` — useQuery, key `["shares"]`.
  - `useDisableShare()` — useMutation, 성공 시 `["shares"]` invalidate(목록·뱃지 갱신).
  - `useCreateShare()` — 성공 시 `file(id)` + `["shares"]` invalidate.
- 타입 `ShareListItem` 추가(`types/api.ts`) — 계약 v1.4 필드와 글자 그대로 일치.
- 라우트 `/shares` 등록(`App.tsx`, 인증 가드 하위) + Nav 항목(`Nav.tsx`, Link2 아이콘).

### 계약 대조 (v1.4)
- [x] `ShareListItem` = `{ id, fileId, fileName, token, url, expiredAt, isActive, createdAt }` 정확히 일치(임의 필드 없음).
- [x] `url` 상대경로 → 프런트가 `window.location.origin` 조합(계약 명시 그대로).
- [x] 상태(활성/만료/비활성)는 `isActive`+`expiredAt` 로 프런트 계산(계약 명시).
- [x] 인증/토큰 갱신 — `/api/shares`, `/api/share/{id}` 모두 기존 `api.*` 래퍼 사용(인증 헤더·refresh 인터셉터 공유).
- [x] 실시간 — 해당 없음(STOMP 이벤트 불요). 변경은 mutation onSuccess invalidate.

### 빌드·타입체크 (v1.4)
- 명령: `pnpm build` (tsc --noEmit && vite build)
- 결과: 통과. tsc 에러 0, vite build 성공(1864 modules, dist/assets/index-*.js 439.48 kB).
  기존 화면 회귀 없음 — 공통 헬퍼/타입만 추가, 기존 시그니처 보존.

### 확인 필요 (v1.4)
- 없음. mock 사용 없음(실제 엔드포인트 그대로 호출).

---

## 기능 확장: 공유 관리 페이지 클라이언트 측 필터 (2026-06-16)

`/shares`(SharesPage.tsx)에 **클라이언트 측 필터** 추가. 백엔드 변경 없음, 서버 재요청 없음.

### 추가 내역
- 상태 필터(칩): 전체 / 활성 / 만료 / 비활성. `computeShareStatus(isActive, expiredAt)`로 각 항목 상태 판정.
  - `StatusFilter = "all" | ShareStatus` 로컬 타입. "all"은 통과, 그 외는 계산 상태와 일치 비교.
- 파일명 검색: `Input`(type=search) 텍스트로 `fileName` 부분일치, 대소문자 무시(`toLowerCase().includes`).
- 두 필터 AND 결합. `useMemo`로 `[shares, statusFilter, nameQuery]` 의존 메모이즈(이미 받은 `data ?? []`만 사용).
- 결과 개수 표시("N개"). 필터 결과 0건일 때 "조건에 맞는 공유 링크가 없습니다." 안내.
- 필터 바는 화면 상단(목록 위), `flex flex-wrap` + 칩 영역 `flex-wrap`, 검색 입력 `w-full sm:w-64`로 모바일 폭 줄바꿈 처리.

### 회귀 보존
- 기존 목록/복사(CopyLinkButton)/비활성화(onDisable)/상태 뱃지/로딩·에러(+재시도)/빈 목록("생성한 공유 링크가 없습니다.") 동작 그대로 유지.
- 테이블 렌더 소스만 `shares.map` → `filteredShares.map`으로 교체. 원목록 `shares.length === 0`(데이터 없음)과 필터 0건(데이터 있으나 조건 불일치)은 별도 메시지로 구분.

### 계약 대조
- [x] 계약에 없는 필드 미사용 — `fileName`/`isActive`/`expiredAt`만 사용(`ShareListItem` 범위 내).
- [x] 서버 재요청 없음 — `useShares()` 캐시 데이터만 클라이언트에서 필터(네트워크 호출 0).
- [x] 상태 판정은 기존 헬퍼 `computeShareStatus` 재사용(중복 로직 없음).

### 빌드·타입체크
- 명령: `pnpm build` (tsc --noEmit && vite build)
- 결과: 통과. tsc 에러 0, vite build 성공(1864 modules, dist/assets/index-aY1DYt-u.js 440.86 kB).

---

## v1.6 변경 (2026-06-17) — 공유 다운로드 게이트웨이 경유

### 계약 핵심 변경
- `GET /api/public/share/{token}` 응답의 `downloadUrl` 의미 변경: presigned MinIO URL → **게이트웨이 상대경로** `"/api/public/share/{token}/download"`.
- 신규 비인증 공개 다운로드 엔드포인트 `GET /api/public/share/{token}/download` — 매 요청 백엔드가 `is_active`/만료/`file.status==UPLOADED` 재검증(presign TTL 잔존 우회 차단). nginx 가 X-Accel-Redirect 로 MinIO 직접 스트리밍.

### 변경한 다운로드 트리거 위치
- `src/pages/PublicSharePage.tsx`: 다운로드 버튼을 `window.open(file.downloadUrl, "_blank")` → `<Button asChild><a href={file.downloadUrl} download>`로 교체.
  - 공개 다운로드는 **비인증**이라 Authorization 헤더 불요 → 단순 링크면 충분.
  - `downloadUrl`이 상대경로(`/api/public/share/{token}/download`)라 브라우저가 현재 origin(nginx 게이트웨이)으로 호출. `Content-Disposition: attachment` 로 다운로드 처리.

### 타입
- `PublicSharedFile.downloadUrl`: 타입 자체는 `string` 유지(필드 형태 불변). 주석에 게이트웨이 경로 의미 반영. 계약에 없는 필드 추가 없음.

### 계약 대조
- [x] presigned URL 직접 사용 제거 — 게이트웨이 상대경로만 호출.
- [x] 비인증 공개 다운로드는 링크 네비게이션으로 처리.
- [x] 계약에 없는 필드 미사용.

### 빌드·타입체크
- 명령: `cd frontend && pnpm build` (tsc --noEmit && vite build)
- 결과: 통과 (1864 modules, dist/assets/index-*.js 440.91 kB).
