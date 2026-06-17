## 화면: upload
진행률 표시 + 실패 재시도 + 취소.

## 사용 엔드포인트/이벤트
- POST /api/files/upload (multipart: file, folderId|null) → { id, folderId, originalName, extension, fileSize, version, status }
- (새 버전) POST /api/files/{id}/content (multipart: file, baseVersion; ?overwrite=true) → { id, version, status } — API 클라이언트에 구현됨(`filesApi.uploadVersion`).

## 계약 대조
- [x] 응답 타입 일치 (UploadedFile)
- [x] 인증/토큰 갱신 처리 — XHR 업로드에 Authorization 헤더 부착(`src/api/files.ts`). 진행률을 위해 fetch 대신 XHR 사용.
- [x] 진행률(onprogress) + 실패 재시도(retry) + 취소(abort) 동작.
- [x] 업로드 성공 시 files 캐시 무효화.

## 빌드·타입체크
- 명령: `pnpm build`
- 결과: 통과

## 확인 필요
- 업로드 XHR은 401 자동 refresh 인터셉터를 거치지 않음(진행률 위해 별도 XHR). 토큰 만료 시 업로드 실패로 표시되며 재시도 시 갱신된 토큰 사용. 장기 업로드 중 만료 처리 정책 확인 필요.
- 413 PAYLOAD_TOO_LARGE 등 에러 코드는 XHR 응답 바디에서 code/message 파싱하여 표시.
- 새 버전 업로드(POST /content) UI는 미연결 — 클라이언트 함수만 준비(우선순위상 신규 업로드 우선). 버전 충돌(409 + overwrite) 흐름은 클라이언트 시그니처에 반영됨.

---

## 기능 확장: 새 버전 업로드 UI (2026-06-16)

기존 "미연결"이던 `POST /api/files/{id}/content` 전용 UI를 추가했다. 같은 이름
재업로드(`/upload` 409 NAME_CONFLICT)와 달리 이 엔드포인트로 버전을 증가시킨다.
백엔드는 런타임 검증 완료 상태.

### 사용 엔드포인트/이벤트
- POST /api/files/{id}/content (multipart `file`, `baseVersion`; 충돌 시 `?overwrite=true`)
  - 성공 200: `{ id, version, status }` → 타입 `FileContentResponse`
  - 409 CONFLICT: `baseVersion != 현재 version` (먼저 저장 우선)
- GET /api/files/{id}/versions  (성공 후 캐시 무효화)
- GET /api/files?folderId=...   (폴더 파일 목록 — 캐시 무효화)
- WS FILE_UPDATED (realtime/useRealtime.ts 가 files/file/versions 무효화 → 다중 디바이스 1초 이내 동기화)

### 추가/수정 파일
- (신규) frontend/src/hooks/useUploadVersion.ts
  - filesApi.uploadVersion(id, file, baseVersion, overwrite, onProgress, signal) 래핑
  - 상태: idle | uploading | conflict | done | failed, progress(%), cancel(abort)
  - 409(status===409 || code==="CONFLICT") → status="conflict" 노출(throw 안 함)
  - 성공 시 invalidate: ["files"], qk.file(id), qk.versions(id)
- (신규) frontend/src/components/explorer/UploadVersionDialog.tsx
  - 파일 선택 → baseVersion=현재 version 으로 즉시 업로드, 진행률 바
  - 409 시 "다른 곳에서 이미 새 버전이 저장됐습니다. 덮어쓸까요?" → 덮어쓰기(overwrite=true) 재요청
  - 성공 토스트, 실패 시 "다시 시도", 업로드 중 취소(abort)
- (수정) frontend/src/pages/ExplorerPage.tsx
  - 파일 행 드롭다운에 "새 버전 업로드" 항목(버전 기록 위) + UploadVersionDialog 렌더
  - VersionsDialog 에 currentVersion / onUploadVersion 전달
- (수정) frontend/src/components/explorer/VersionsDialog.tsx
  - currentVersion 표시 + 상단 "새 버전 업로드" 버튼(onUploadVersion 콜백)

### 계약 대조
- [x] 응답 타입 일치: FileContentResponse `{ id, version, status }` 만 사용. 계약에 없는 필드 미사용.
- [x] 요청 형식 일치: multipart `file` + `baseVersion`, 충돌 재시도 `?overwrite=true` (계약 v1 line 69-73).
- [x] baseVersion = 해당 파일 현재 `version`(FileListItem.version) 사용 — 임의 가정 없음.
- [x] 409 분기 = 계약 "먼저 저장 우선 → 클라이언트 덮어쓰기 선택" 흐름.
- [x] 인증/토큰: 기존 xhrUpload 가 tokenStore Access 토큰 Authorization 헤더 전송.
- [x] 실시간: FILE_UPDATED 핸들러(기존)로 다중 디바이스 동기화.

### 빌드·타입체크
- 명령: `pnpm build` (= `tsc --noEmit && vite build`)
- 결과: 성공. tsc 오류 0, vite build ✓ (1862 modules, ~4.6s)

### UI 사용 흐름
1) 탐색기 파일 행 → ⋮ 메뉴 → "새 버전 업로드"
   (또는 ⋮ → "버전 기록" → 패널 상단 "새 버전 업로드" 버튼)
2) "파일 선택" → 고르면 baseVersion=현재 v 로 즉시 업로드, 진행률 표시
3) 정상: 성공 토스트 + 목록/버전/메타 캐시 무효화로 v+1 즉시 반영
4) 409: "...덮어쓸까요?" → "덮어쓰기"(overwrite=true) 재요청
5) 실패: 오류 메시지 + "다시 시도", 업로드 중 "취소"(abort) 가능

### 확인 필요
- 없음. 계약 v1(line 69-73) 시그니처 일치, 기존 uploadVersion 클라이언트 함수 그대로 사용.
