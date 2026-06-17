---
name: api-contract-design
description: Mini Drive의 공유 계약(DB 스키마·REST API·WebSocket 이벤트·MinIO 저장키)을 작성하거나 변경할 때 사용한다. 설계자가 모든 빌더의 단일 출처가 되는 계약 문서를 일관된 형식으로 만들도록 돕는다. 코드 구현에는 사용하지 않는다.
---

# API Contract Design

## Overview

이 Skill은 설계자가 PRD를 구현 가능한 **계약**으로 바꾸도록 돕는다. 계약은 백엔드와 프런트가 글자 그대로 구현할 수 있을 만큼 구체적이어야 하며, 추측이 필요한 부분은 `확인 필요`로 남긴다. PRD의 Out-of-Scope(실시간 공동편집, RBAC, 편집기)는 계약에 포함하지 않는다.

## Workflow

1. `artifacts/00-input.md`(PRD)와 기존 계약 파일을 읽는다.
2. 4개 산출물을 순서대로 작성한다. 변경 시에는 영향받는 항목만 갱신하고 버전을 올린다.
   - `db-schema.md`: 6개 테이블(User/Folder/File/FileVersion/ShareLink/Notification) 컬럼·타입·제약·인덱스·FK, 파일 상태머신.
   - `api-contract.md`: 엔드포인트별 메서드·경로·요청·응답·에러·인가 규칙.
   - `websocket-events.md`: STOMP 토픽 + 4개 이벤트 페이로드.
   - `storage-layout.md`: object-key 규칙·버킷·S3 추상화·재시도.
3. 빠진 결정은 임의로 정하지 말고 `확인 필요`에 적는다.
4. 품질 기준으로 점검하고, 확정되면 영향 모듈을 명시한다.

## 산출물 형식

### `api-contract.md` (엔드포인트 항목)

```md
### POST /api/files/upload
- 인증: 필요 (Bearer Access Token)
- 인가: 요청자 == folder.owner
- 요청: multipart(file, folderId) — file 상태 PENDING→UPLOADING
- 응답 201: { id, originalName, extension, fileSize, status, version, folderId, createdAt }
- 에러: 401 미인증 / 403 폴더 비소유 / 409 동일파일 충돌(Conflict) / 413 용량초과
- 부수효과: 성공 시 status=UPLOADED, FILE_UPLOADED 이벤트 발행 / 실패 시 status=FAILED(재시도 가능)
```

### `db-schema.md` (테이블 항목)

```md
## File
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | bigint | PK |
| folder_id | bigint | FK→folder.id, null=루트 |
| owner_id | bigint | FK→user.id, NOT NULL |
| object_key | varchar | NOT NULL, MinIO 키 |
| original_name | varchar | NOT NULL |
| extension | varchar | |
| file_size | bigint | |
| version | int | 기본 1 |
| status | varchar | PENDING/UPLOADING/UPLOADED/DELETED/FAILED |
인덱스: (owner_id, folder_id), (original_name), (extension)
```

### `websocket-events.md`

```md
## STOMP
- 연결: /ws (SockJS) — Authorization 헤더로 JWT 검증
- 사용자 구독: /user/queue/notifications
- 이벤트: FILE_UPLOADED{fileId,fileName} / FILE_DELETED{fileId} / SHARE_CREATED{fileId} / FILE_UPDATED{fileId}
```

## 품질 기준

- PRD의 In-Scope 기능이 모두 엔드포인트/이벤트로 표현됐다.
- 모든 변경/삭제/조회 엔드포인트에 인가 규칙(소유자 검증)이 있다.
- 파일 상태머신과 충돌 처리(먼저 저장 우선, 이후 Conflict)가 명시됐다.
- 공유 링크는 만료(expired_at)·읽기전용·비활성화 규칙이 있다.
- object-key 규칙이 PRD 7장 저장 구조와 일치한다.

## 예외와 금지 행동

- 정보가 부족하면 추측하지 말고 `확인 필요`에 적고 Orchestrator에 질문한다.
- Out-of-Scope 기능을 계약에 추가하지 않는다.
- 이 Skill로 애플리케이션 코드를 작성하지 않는다.
