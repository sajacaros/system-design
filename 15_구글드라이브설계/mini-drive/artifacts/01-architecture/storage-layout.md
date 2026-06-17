# storage-layout — MinIO Object Storage (계약 v1.6)

## 추상화 (AWS S3 전환 가능)

- 모든 스토리지 접근은 `StorageService` 인터페이스를 통한다(put/get/presignGet/delete/exists).
- 구현은 AWS SDK v2 `S3Client`를 MinIO endpoint로 설정(`path-style-access=true`). MinIO→AWS S3 전환 시 endpoint/credential만 교체.
- ~~presigned URL로 다운로드 제공(서버 대역폭 절감, 수평 확장 용이).~~ **→ v1.6에서 폐기.** 다운로드는 nginx 게이트웨이 단일 길목을 통과하고, 매 요청 백엔드 실시간 인가 후 nginx가 MinIO에서 직접 스트리밍한다(아래 "다운로드 모델 v1.6" 참조).

## 버킷

- 단일 버킷 `minidrive` (애플리케이션 기동 시 없으면 생성).

## Object Key 규칙 (PRD 7장)

```
users/{userId}/{fileId}            # 현재 버전 원본
versions/{fileId}/v{n}             # 버전 n 스냅샷
thumbnails/{fileId}.png            # 썸네일(이미지 파일에 한해 선택 생성)
```

- 업로드: `users/{userId}/{fileId}` 저장 + `versions/{fileId}/v1` 동시 기록 → file.object_key=현재 버전 키.
- 새 버전: `versions/{fileId}/v{n}` 기록 후 `users/{userId}/{fileId}`를 최신으로 갱신(또는 현재 버전 키를 버전 키로 운용 — 구현은 백엔드가 일관되게 택1하고 file.object_key에 반영).
- 복구: 대상 `versions/{fileId}/v{k}`를 새 버전 `v{n}`으로 복사 → 이력 보존(유실 0%).
- 영구삭제: 해당 fileId의 users/versions/thumbnails object 일괄 삭제.

## 신뢰성 (PRD NFR)

- 파일 유실 0%: 메타(file/file_version)와 object를 같은 트랜잭션 경계에서 정합 유지. object 저장 성공 후 status=UPLOADED 확정, 실패 시 FAILED(메타 롤백 또는 재시도 큐).
- 업로드 실패 재시도: 클라이언트가 같은 file(PENDING/FAILED)로 재전송 가능.
- ~~presigned URL 만료: 다운로드 5분, 공유 링크 접근 시에도 단기 presign 재발급.~~ **→ v1.6에서 폐기.** 브라우저 노출 presign 제거. 내부 전용 presign(브라우저 비노출, 10~30초 초단기)만 잔존(아래 v1.6 참조).

## 계약 v1.2 정합 메모 (2026-06-16, qa I-3 반영)
- **presign 호스트 분리**: 컨테이너 내부 접근(put/get/delete)은 `MINIO_ENDPOINT=http://minio:9000`, **presigned URL 발급(`presignGet`)은 `MINIO_PUBLIC_ENDPOINT`(로컬 시연 기본 `http://localhost:9000`)** 를 사용한다. 브라우저가 발급 URL 호스트를 해석할 수 있어야 다운로드가 동작한다.
- 구현: presigner 전용 S3Presigner를 공개 endpoint로 별도 구성하거나, 발급된 URL의 호스트를 공개 endpoint로 치환한다. put/get 클라이언트는 내부 endpoint 유지.
- AWS S3 전환 시: 두 endpoint가 동일(실제 S3 호스트)하므로 자연히 일치.

## 계약 v1.5 정합 메모 (2026-06-16, 한글 charset 버그 수정)
- **presign 응답 Content-Type override**: `presignGet(key, ttl, responseContentType)` 오버로드로, 다운로드 presigned URL이 응답 Content-Type을 강제한다.
- 텍스트 계열 확장자(txt, log, md, csv, tsv, json, xml, yaml, html, css, js, ts, sql 등)는 `"<MIME>; charset=UTF-8"`로 서빙 → 브라우저에서 UTF-8(한글) 정상 표시. 바이너리(이미지/zip/pdf 등)는 charset 미부착, 미상 확장자는 `application/octet-stream`.
- presign 시점 override라 **기존 업로드 객체도 재업로드 없이 교정**됨. 적용: 파일 상세·버전·공유 다운로드 전부.

## 다운로드 모델 v1.6 — 게이트웨이 경유 (2026-06-17, 공유 비활성화 우회 차단)

### 배경 / 폐기 사유
- v1.2~v1.5 모델은 브라우저에 5분 TTL presigned URL을 발급하고 브라우저가 `MINIO_PUBLIC_ENDPOINT`로 MinIO에 **직접 접근**했다.
- 결함: 공유 링크를 비활성화(`share_link.is_active=false`)해도, 비활성화 **직전에 발급된 presigned URL이 TTL(300s) 동안 살아 있어** MinIO 직접 접근으로 우회된다. presigned URL은 S3/MinIO 구조상 **개별 취소 불가**. → 인가 결정과 다운로드 사이에 시간차/우회 경로가 존재.

### 채택 메커니즘: X-Accel-Redirect (권장안 채택)
근거: nginx는 SigV4 서명을 직접 생성하지 못하므로 `internal` location에서 MinIO로 직접 proxy_pass 하려면 백엔드가 서명한 URL이 필요하다. X-Accel-Redirect는 (1) 백엔드가 인가만 수행하고 바이트는 nginx↔MinIO로 흘러 백엔드 대역폭을 거치지 않으며, (2) 단일 다운로드 경로 = 단일 인가 길목(choke point)이라 매 요청 실시간 재검증이 강제되고, (3) auth_request(2왕복: 인증 서브요청 + 본 요청)보다 왕복이 적고 인가·리다이렉트 대상 결정을 한 번의 컨트롤러 호출로 끝낼 수 있어 더 단순·견고하다. → **auth_request는 대안으로만 기록(아래)**.

### 요청 흐름 (모든 다운로드 공통)
```
브라우저 → nginx (게이트웨이 다운로드 경로, 예: /api/.../download)
         → nginx가 백엔드 다운로드 컨트롤러로 proxy_pass
         → 백엔드: 매 요청 실시간 인가
              · 공유: token 유효 + is_active=true + 미만료 + file.status=UPLOADED
              · 인증 파일/버전: JWT 소유자(owner_id == 요청자)
            인가 실패 → 에러 응답(아래 에러 매핑), 바디 없음
            인가 성공 → 바디 없는 200 + 응답 헤더만 반환:
              - X-Accel-Redirect: /_minio/<presigned-path-and-query>
              - Content-Type: <v1.5 ContentTypes 규칙>
              - Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>
         → nginx: X-Accel-Redirect 감지 → internal location /_minio/ 로 재진입
         → nginx /_minio/ (internal 전용): MinIO 내부 endpoint로 proxy_pass, 스트리밍
         → 바이트는 nginx ↔ MinIO(minio:9000)에서만 흐름(백엔드 비경유)
```

### X-Accel-Redirect 내부 규약 (불변식)
- **헤더 이름**: `X-Accel-Redirect`. **값 형식**: `/_minio/<objectPath>?<presignedQuery>` — nginx `internal` location prefix는 `/_minio/`로 고정.
  - `<objectPath>` = MinIO 버킷·키 경로(예: `minidrive/users/{userId}/{fileId}`).
  - `<presignedQuery>` = 백엔드가 생성한 **내부 전용 presign**의 SigV4 쿼리스트링(`X-Amz-Signature` 등). nginx는 이 경로+쿼리를 그대로 MinIO로 전달만 한다(서명 생성 불가).
- **nginx `internal` location**: `location /_minio/ { internal; proxy_pass http://minio:9000/; ... }` — `internal` 지시어로 **외부 직접 접근 차단**(브라우저가 `/_minio/...`를 직접 쳐도 404). X-Accel-Redirect를 통한 내부 재진입만 허용.
- **MinIO 내부 endpoint**: `http://minio:9000`(도커 네트워크 내부, host published 아님).
- **내부 presign TTL**: 10~30초(기본 30초). 즉시 소비 전제. **목적은 보안 경계가 아니라 nginx→MinIO 접근 수단**일 뿐(인가는 직전 백엔드 단계에서 이미 끝났다).
- **브라우저 비노출 불변식**: 이 내부 presign(쿼리 포함)은 **절대 브라우저 응답 바디/헤더로 노출되지 않는다**. X-Accel-Redirect 헤더는 nginx가 소비하며 클라이언트로 전달하지 않는다(nginx 기본 동작). API 응답의 `downloadUrl`은 더 이상 presigned URL이 아니라 **게이트웨이 다운로드 경로**다.
- **StorageService 영향**: `presignGet(...)`은 **내부 전용**으로 남되 TTL을 초단기로 좁히고, 발급 URL의 호스트/스킴은 무관(nginx가 경로+쿼리만 사용)하다. v1.2의 `MINIO_PUBLIC_ENDPOINT` presign 호스트 치환 로직은 **불필요해지므로 제거**(아래 NFR 참조).

### 응답 헤더 규칙 (v1.5 ContentTypes 보존)
- **Content-Type**: v1.5 규칙 그대로 — 텍스트 계열 확장자는 `"<MIME>; charset=UTF-8"`, 바이너리는 charset 미부착, 미상 확장자는 `application/octet-stream`. presign override 대신 **백엔드 컨트롤러가 X-Accel-Redirect 응답에 직접 설정**한다(nginx가 백엔드 헤더를 클라이언트로 전달).
- **Content-Disposition**: `attachment; filename*=UTF-8''<RFC 5987 percent-encoded originalName>` — 한글 파일명 UTF-8 보존.
- 주의: nginx가 MinIO 응답의 Content-Type을 덮어쓰지 않도록, 백엔드가 설정한 헤더가 우선되게 구성(`proxy_hide_header Content-Type` 후 백엔드 헤더 유지 등 — 구현은 인프라 담당).

### NFR / 보안 (MinIO 포트 비노출)
- **`MINIO_PUBLIC_ENDPOINT` 폐기**: 브라우저-facing presign이 사라지므로 공개 endpoint 환경변수·치환 로직 제거.
- **MinIO 9000 host published 제거**: docker-compose에서 `ports: ["9000:9000"]` → `expose: ["9000"]`로 전환(도커 네트워크 내부에서 nginx만 접근). 브라우저는 MinIO에 직접 도달할 경로가 없어진다. **실제 compose 편집은 인프라 담당이 수행하며 사람 승인 게이트 대상**(포트 변경 = 기동 구성 변경).
- 콘솔 포트(9001)는 운영 편의상 유지 여부 인프라 재량(데이터 평면 9000과 무관).

### 대안 기록: auth_request (채택 안 함)
- nginx `auth_request`로 다운로드 경로 진입 시 백엔드 `/internal/authz` 서브요청을 보내 200/4xx로 인가, 통과 시 본 요청을 `/_minio/`로 처리. 
- 미채택 근거: (1) 서브요청 + 본요청 2왕복, (2) 인가 컨텍스트(어떤 object로 리다이렉트할지)를 서브요청 응답 헤더로 본 location에 넘기는 추가 와이어링 필요, (3) 결국 internal presign도 동일하게 필요 → X-Accel-Redirect 대비 이득 없음.

## 확인 필요
- 썸네일/미리보기 생성은 이미지 한정 선택 기능 — 초기 빌드에서는 키 규칙만 확정하고 생성 파이프라인은 후속(보완) 단계로 둘 수 있음.
