# 검증 모듈: 계약 일치 (REST/WS 이벤트/상태머신/충돌)
검증일: 2026-06-16 · 검증자: qa-reviewer · 방법: 정적 분석(read/grep) + 백엔드·프런트 교차 감사

## 판정: 조건부 통과 (I-1 공유 응답 필드명 1건만 반려, 그 외 일치)

## 위반 목록

| # | 항목 | 증거(파일:라인) | 심각도 | 담당 |
| --- | --- | --- | --- | --- |
| C-1 | GET /share/{token} 응답 필드명 불일치 — 계약 `id`/`extension` vs 백엔드 `fileId`(+프런트 타입 누락). (= integration I-1) | `share/dto/ShareDtos.java:14-20`, 프런트 `types/api.ts:179-183` | 중 | 백엔드(우선)+프런트 |

## 확인된 통과 항목

### REST 엔드포인트 메서드/경로/응답/에러 (계약 ↔ 백엔드 ↔ 프런트 3자 대조)
- **Auth**: signup(201 `{id,email,nickname}`)·login(200 `{accessToken,refreshToken,user}`)·refresh(200, 회전)·logout(204). 에러 EMAIL_TAKEN(409)/BAD_CREDENTIALS(401)/INVALID_REFRESH(401). 모두 계약 일치. 프런트 호출 경로·타입 일치(`api/auth.ts`, `types/api.ts:40-67`).
- **Folders**: GET(`?parentId`)·POST(201)·PATCH(200)·DELETE(204). FolderResponse `{id,parentId,name,createdAt}`. 에러 NAME_CONFLICT(409)/CYCLIC_MOVE(400)/FORBIDDEN(403)/NOT_FOUND(404). 일치.
- **Files**: GET 목록(페이지 `{content,page,size,totalElements}`, 필드 `id,folderId,originalName,extension,fileSize,version,status,updatedAt`)·upload(201)·GET 상세(+downloadUrl)·POST /content(200 `{id,version,status}`)·PATCH·DELETE(204)·versions·versions/{v}/download(`{downloadUrl}`)·restore(`{id,version}`)·permanent(204). 모두 계약 일치.
- **Share**: POST share(201 `{id,token,url,expiredAt,isActive}`)·DELETE /api/share/{id}(204). url=상대 `/share/{token}`(`ShareService.java:60`) — 계약 v1.1 일치. (공개 GET 필드명만 C-1.)
- **Notifications**: GET(`?unread`)·PATCH /{id}/read(204), 필드 `{id,type,message,isRead,createdAt}`. 일치.
- **에러 바디·코드 매핑**: `common/ErrorCode.java` enum↔HttpStatus 전부 계약대로(400/401/403/404/409/410/413). `GlobalExceptionHandler`가 ApiException·Validation(400)·MaxUploadSize(413)·Auth(401) 처리.

### 프런트 계약 외 필드 사용 여부
- **위반 없음.** 프런트는 계약 타입만 소비. `types/api.ts` 주석에 "계약에 없는 응답 필드는 추가하지 않는다" 명시. ShareLink·FileDetail.downloadUrl·버전다운로드 `{downloadUrl}`·FileListItem 필드 모두 계약 범위 내(프런트 감사 에이전트 교차 확인).

### WS 4개 이벤트 발행 트리거
- FILE_UPLOADED ← upload 완료(`FileService.upload`), FILE_UPDATED ← 새 버전/이름·이동(PATCH)/버전 복구/휴지통 복원, FILE_DELETED ← 휴지통 이동(DELETE), SHARE_CREATED ← 공유 생성(`ShareService.createShare:56`). 페이로드 `SyncEvent`(`sync/SyncEvent.java:20-34`)가 계약 필드(type/fileId/fileName/folderId/version/occurredAt)와 일치, NON_NULL 직렬화. 발행 대상=owner 개인 큐 `convertAndSendToUser(.../queue/notifications)` + notification 행 저장(`SyncEventPublisher.java:25-29`). 계약 일치.
- 프런트 WS 타입(`types/ws.ts`) 페이로드와 일치.

### 상태머신·충돌 처리
- 상태 전이: PENDING→UPLOADING(`FileService.upload`)→UPLOADED, 실패 시 FAILED, 삭제 시 DELETED, 복원 시 UPLOADED. 비UPLOADED는 버전/공유 차단(`requireUploaded`). permanentDelete는 DELETED만 허용(아니면 409). 계약 상태머신 일치.
- **baseVersion→409→overwrite**: `FileService.uploadNewVersion` — overwrite=false 시 baseVersion 필수(없으면 400 VALIDATION), `baseVersion != 현재 version`이면 **409 CONFLICT**, `?overwrite=true`면 검사 우회. 프런트 `files.ts`가 `baseVersion` 폼 전송 + `?overwrite=true` 재요청 패턴 구현. 계약 일치.
- restore 분기: `{version}` 있으면 버전 복구 우선(상태 무관, version+1 복사 생성), 없으면 DELETED→UPLOADED 휴지통 복원(아니면 409). 계약 v1.1 우선순위 일치.

## 사람 승인/위험 보고

- C-1만 수정하면 계약 완전 일치. 백엔드 DTO 첫 필드를 `id`로 바꾸고 `extension` 포함 유지, 프런트 `PublicSharedFile`에 `id`/`extension` 추가 권장. (계약 v1.1이 `id`로 명시했으므로 백엔드를 계약에 맞추는 것이 단일 출처 원칙에 부합.)
- 런타임 이벤트 발행 타이밍(1초 이내 도달)은 인프라 기동 후 e2e 확인 권장.
