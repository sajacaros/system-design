## 검증 모듈: 전체 스택 런타임 E2E (시연 10단계 + 추가)
- 검증 방식: 실행 중 도커 스택에 실제 HTTP(curl) 실측. 코드 미수정.
- 일시: 2026-06-16 (UTC) / 검증자: qa-reviewer
- 대상: 앱 nginx `http://localhost`, MinIO `http://localhost:9000`
- 계정: demo@dacon.kr (id=1), 추가 가입 qa-other (id=2)
- 기준 계약: artifacts/01-architecture/*.md (v1.1/v1.2 정합 메모 포함)

## 판정: 통과 (10/10 단계 통과, I-2/I-3/I-1 런타임 해소 확인) — 트래시 조회 관련 경미 결함 2건 별도 기록

## 단계별 판정표

| 단계 | 동작 | HTTP | 증거 | 판정 |
| --- | --- | --- | --- | --- |
| 1 | 로그인 POST /api/auth/login | 200 | `{accessToken, refreshToken, user:{id:1,email,nickname}}` 발급. JWT 디코드 sub=1 typ=access | 통과 |
| 2a | 폴더 생성 POST /api/folders | 201 | `{id:1,parentId:null,name:"qa-folder-...",createdAt}` | 통과 |
| 2b | 폴더 목록 GET /api/folders | 200 | `[{id:1,parentId:null,name,createdAt}]` | 통과 |
| 3 | 업로드 multipart POST /api/files/upload | 201 | `{id:1,folderId:1,originalName:"qa-test.txt",extension:"txt",fileSize:43,version:1,status:"UPLOADED"}` | 통과 |
| 4 | MinIO 객체 확인 (mc ls) | - | `users/1/1`(43B) **및** `versions/1/v1`(43B) 동시 존재 — 키 규칙 일치, v1 스냅샷 기록 확인 | 통과 |
| 5a | 파일 목록 GET /api/files?folderId=1 | 200 | `{content:[{id:1,...status:UPLOADED}],page:0,size:20,totalElements:1}` | 통과 |
| 5b | 파일 상세 GET /api/files/1 | 200 | 메타 + `downloadUrl` 포함, 호스트 `localhost:9000`, presign Expires=300 | 통과 |
| 6 | presigned 다운로드 (I-3 핵심) | 200 | host=localhost:9000, size=43, sha256 원본==다운로드 일치(fc5c0838...) **바이트 일치** | 통과 |
| 7a | 토큰 없이 GET /api/files | 401 | `{code:"UNAUTHENTICATED",message:"Authentication required"}` | 통과 |
| 7b | 신규 가입 POST /api/auth/signup | 201 | `{id:2,email:"qa-other-...",nickname:"qaother"}` | 통과 |
| 7c | 타 사용자 토큰으로 GET /api/files/1 | 403 | `{code:"FORBIDDEN",message:"Not the owner of this resource"}` | 통과 |
| 8a | 공유 생성 POST /api/files/1/share | 201 | `{id:1,token:"EnqF...",url:"/share/EnqF...",expiredAt:null,isActive:true}` (url 상대경로 SPA) | 통과 |
| 8b | 비인증 공개 GET /api/public/share/{token} (I-2 핵심) | 200 | `{id:1,originalName,extension,fileSize:43,downloadUrl}` — **첫 키 id (I-1 필드순서 일치)**, host localhost:9000 | 통과 |
| 8c | 공개 downloadUrl 다운로드 | 200 | size=43, 원본과 바이트 일치 | 통과 |
| 9a | 새 버전 POST /api/files/1/content baseVersion=1 | 200 | `{id:1,version:2,status:"UPLOADED"}` | 통과 |
| 9b | 잘못된 baseVersion=1 (현재 2) | 409 | `{code:"CONFLICT",message:"Version conflict: base 1 != current 2"}` | 통과 |
| 10a | 버전 복구 POST /api/files/1/restore {version:1} | 200 | `{id:1,version:3}` (버전 증가) | 통과 |
| 10b | 복구본 다운로드 검증 | 200 | size=43, 복구본 sha256 == v1 원본 일치. versions 목록 v3/v2/v1 모두 보존(유실 0%) | 통과 |

## 추가 항목 판정표

| # | 동작 | HTTP | 증거 | 판정 |
| --- | --- | --- | --- | --- |
| A1 | 공유 비활성화 DELETE /api/share/1 | 204 | 정상 비활성화 | 통과 |
| A1b | 비활성 후 공개 접근 | 410 | `{code:"DISABLED",message:"Share link disabled"}` | 통과 |
| A2 | 존재하지 않는 토큰 공개 접근 | 404 | `{code:"INVALID_LINK"}` | 통과 |
| A3 | WebSocket SockJS GET /ws/info | 200 | `{entropy,origins:["*:*"],cookie_needed:true,websocket:true}` — SockJS 등록 확인. /ws 200 | 통과(핸드셰이크 가능) |
| A4 | 휴지통 DELETE /api/files/{id} | 204 | UPLOADED 목록에서 제거됨, restore 후 복원 | 통과 |
| A4b | 휴지통 복원 POST /restore {} | 200 | `{id:2,version:1}` DELETED→UPLOADED | 통과 |
| A5 | 만료 공유(과거 expiredAt) 공개 접근 | 410 | `{code:"EXPIRED",message:"Share link expired"}` | 통과 |
| **A6** | GET /api/files?status=DELETED (folderId 미지정) | 200 | `{content:[],totalElements:0}` — **삭제된 파일이 안 보임** (folderId=1 지정 시에는 보임) | **이상(경미)** |
| **A7** | GET /api/files?folderId=null&status=DELETED | 500 | `INTERNAL_ERROR` "null" → Long 변환 실패. 계약은 `folderId={id|null}` 허용, 500/스택메시지 누출 | **이상(경미)** |

## 확인된 통과 항목 (핵심)

- **I-2 (공유 라우팅 충돌) 런타임 해소**: 공개 공유가 `GET /api/public/share/{token}`에서 비인증 200으로 동작, nginx가 `/api` 프리픽스로 정상 프록시. 공유 응답 `url`은 상대경로 `/share/{token}`(SPA). 별도 nginx 블록 불필요.
- **I-3 (다운로드 호스트) 런타임 해소**: 상세/버전/공유의 모든 `downloadUrl` 호스트가 `localhost:9000`(MINIO_PUBLIC_ENDPOINT)로 발급되어 브라우저(curl) 해석·다운로드 200 + 바이트 일치. 내부 endpoint(minio:9000)와 분리 동작 확인.
- **I-1 (공유 응답 필드)**: 공개 공유 응답 첫 키가 `id`, `extension` 포함 — 계약 v1.2 일치.
- **파일 유실 0% 설계**: 업로드 시 `users/{userId}/{fileId}` + `versions/{fileId}/v1` 동시 기록, 새 버전·복구 후에도 v1/v2/v3 이력 모두 보존.
- **보안 경계**: 미인증 401, 타 소유자 403, 비활성/만료/무효 공유 각각 410 DISABLED / 410 EXPIRED / 404 INVALID_LINK 정확.
- **버전 충돌 제어**: baseVersion 불일치 시 409 CONFLICT(먼저 저장 우선).
- 에러 바디 형식 `{code,message,timestamp}` 일관(공통 규칙 일치).

## 이상/반려 목록

| # | 항목 | 증거 | 심각도 | 담당 |
| --- | --- | --- | --- | --- |
| A6 | 전역 휴지통 조회 누락: `GET /api/files?status=DELETED`(folderId 미지정)이 빈 목록 반환. 트래시는 `folderId`를 지정해야만 노출 → 프런트 휴지통 화면이 폴더 무관 전역 목록을 기대하면 빈 화면. | 실측: folderId 없이 totalElements:0, folderId=1 지정 시 해당 파일 1건 노출 | 중 | backend |
| A7 | `folderId=null` 리터럴 처리 결함: 계약은 `folderId={id|null}` 허용인데 문자열 "null" 전달 시 HTTP 500 INTERNAL_ERROR + 내부 변환 예외 메시지 누출. 400 VALIDATION으로 처리하거나 null 파싱 필요. | 실측: `Failed to convert value ... For input string: "null"` 500 | 중(에러처리/메시지 누출) | backend |

> 비고: A6/A7은 시연 10단계 외의 휴지통 조회 보조 경로로, 10단계 자체 판정에는 영향 없음. 트래시 삭제·복원 핵심 동작은 정상.

## 사람 승인/위험 보고

- **WebSocket 실시간 토스트(다중 브라우저)**: curl로 STOMP CONNECT/SUBSCRIBE 프레임의 실제 푸시 수신은 검증 불가. `/ws/info` 200 + `websocket:true`로 SockJS 핸드셰이크 가능만 확인. **다중 브라우저 toast 1초 이내 반영은 수동 확인 권장.**
- 권장 조치:
  1. A6 전역 휴지통 조회(folderId 미지정 시 DELETED 전체 반환) 동작 정의·수정 후 재검증.
  2. A7 `folderId=null` 파싱 또는 400 처리로 500/내부 메시지 누출 제거.
  3. WS 실시간 알림 다중 브라우저 수동 시연(업로드→타 세션 toast 1초 이내) 1회 기록.
