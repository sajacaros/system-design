## 모듈: files

## 구현 범위
- 엔드포인트:
  - `GET /api/files?folderId&status=UPLOADED&name&extension&sort=recent&page&size` → PageResponse{content,page,size,totalElements}
  - `POST /api/files/upload`(multipart file,folderId) → 201, PENDING→UPLOADING→UPLOADED, version 1 + file_version v1
  - `GET /api/files/{id}` → 메타 + downloadUrl(presigned, 5분)
  - `POST /api/files/{id}/content`(multipart, baseVersion, ?overwrite=true) → 새 버전(version+1)
  - `PATCH /api/files/{id}`(originalName?, folderId?) → 이름변경/이동
  - `DELETE /api/files/{id}` → 휴지통 이동(status=DELETED, deleted_at)
- 엔티티/테이블: `file`(상태머신, object_key, version, deleted_at), `file_version`.
- 이벤트: FILE_UPLOADED(업로드), FILE_UPDATED(새 버전/PATCH), FILE_DELETED(삭제). notification 동시 저장.
- 스토리지: 업로드 시 `users/{uid}/{fileId}` + `versions/{fileId}/v1` 동시 기록. object_key=현재 키.

## 계약 대조
- [x] 요청/응답 필드 일치(FileResponse: id,folderId,originalName,extension,fileSize,version,status,updatedAt)
- [x] 인가: 모든 file 자원 owner_id==요청자, 위반 403; 미인증 401
- [x] 상태머신: PENDING→UPLOADING→UPLOADED, 실패 시 FAILED(재시도 가능)
- [x] 충돌: baseVersion!=현재 version → 409 CONFLICT, ?overwrite=true 재요청 시 진행
- [x] NAME_CONFLICT: 동일 폴더 동명(미삭제) 409
- [x] PAYLOAD_TOO_LARGE: MaxUploadSizeExceededException → 413

## 빌드·테스트
- 명령: `./gradlew build` (전체 재실행)
- 결과: 통과 14 / 실패 0 (전체). FileFlowTest 4건 통과.

## v1.2 결함 수정 (2026-06-16, qa I-3 반영, 재검증 완료)
- **I-3 presign 공개 호스트**: presigned 다운로드 URL이 도커 내부 호스트(`minio:9000`)로 발급돼 브라우저에서 접속 불가하던 문제 해소.
  - `StorageProperties.publicEndpoint` 추가 (env `MINIO_PUBLIC_ENDPOINT`, application.yml `storage.public-endpoint`, 기본 `http://localhost:9000`; 미설정 시 내부 endpoint로 폴백).
  - `S3Config.s3Presigner`를 `getPublicEndpoint()`로 구성 → presignGet 발급 URL 호스트가 공개 endpoint. put/get/delete의 `S3Client`는 내부 `MINIO_ENDPOINT`(minio:9000) 유지.
  - 효과: 파일 상세 다운로드(`GET /api/files/{id}`)·버전 다운로드(`GET /api/files/{id}/versions/{version}/download`)·공유 다운로드(`GET /api/public/share/{token}`)의 모든 `downloadUrl`이 브라우저 해석 가능한 공개 호스트로 발급.
  - AWS S3 전환 시 두 endpoint 동일 → 자연 일치.

## v1.3 결함 수정 (2026-06-16, UTF-8 텍스트 인코딩 깨짐 / 재검증 완료)
- **증상**: 웹에서 한글(UTF-8) 텍스트 파일 다운로드/미리보기 시 인코딩 깨짐. 저장 바이트·파일명은 무손상.
- **원인**: presigned 다운로드 응답이 `Content-Type: text/plain`(charset 없음) + `X-Content-Type-Options: nosniff`로 나가 브라우저가 UTF-8을 인식 못 하고 플랫폼 기본 인코딩으로 렌더.
- **수정 (둘 다 적용)**:
  1. **presignGet 응답 Content-Type override**: `StorageService.presignGet`에 `responseContentType` 인자 오버로드 추가(기존 2-인자 시그니처는 `default`로 보존). `S3StorageService`는 AWS SDK v2 `GetObjectRequest.responseContentType(...)`로 발급 → presigned URL이 올바른 Content-Type을 강제. **이미 업로드된 객체도 재업로드 없이 교정됨.**
  2. **업로드 시 객체 Content-Type 정규화**: put 시 `ContentTypes.normalizeForStorage(client-type, filename)`로 텍스트류인데 charset이 없으면 `; charset=UTF-8` 부착하여 저장(보조).
- **확장자→MIME 헬퍼**: 신규 `com.minidrive.storage.ContentTypes`.
  - 텍스트류(`; charset=UTF-8` 보장): `txt, log, md, markdown, csv, tsv, json, xml, yaml, yml, html, htm, css, js, ts, sql, ini, conf, properties, sh, py, java, svg`.
  - 바이너리(charset 없음): `pdf, png, jpg, jpeg, gif, webp, bmp, zip, gz, tar, mp3, mp4, doc(x), xls(x), ppt(x)`.
  - 미지정/미상 확장자: `application/octet-stream`(바이너리 보수적 처리).
- **호출부 일관 반영**: `FileService.get`(파일 상세 downloadUrl), `FileService.versionDownloadUrl`(특정 버전 다운로드), `ShareService.resolve`(공유 다운로드) 모두 `ContentTypes.forFilename(originalName)`을 presign에 전달. presign 공개 호스트(`MINIO_PUBLIC_ENDPOINT`) 정합 유지(v1.2 그대로).
- **presign 응답 Content-Type 변화 예**: `hangul.txt` → `text/plain; charset=UTF-8`, `data.csv` → `text/csv; charset=UTF-8`, `config.json` → `application/json; charset=UTF-8`; `photo.png` → `image/png`(charset 없음), `mystery.xyz` → `application/octet-stream`.
- **수정 파일**:
  - `storage/ContentTypes.java`(신규), `storage/StorageService.java`, `storage/S3StorageService.java`, `file/FileService.java`, `share/ShareService.java`, `support/FakeStorageService.java`(테스트).
  - 테스트 추가: `storage/ContentTypesTest.java`(charset 매핑 단위검증), `file/FileFlowTest#get_textFile_downloadUrl_forcesUtf8ContentType`(presign 응답에 charset 포함 확인).
- **빌드**: `./gradlew build` 통과, 테스트 24 / 실패 0.
- **계약 영향(확인 필요 — 직접 수정 안 함)**: `api-contract.md`/`storage-layout.md`의 다운로드(presigned) 설명에 "텍스트류 다운로드는 응답 Content-Type에 `charset=UTF-8`을 강제(presign override)" 문구 보강 권장. `storage-layout.md`의 StorageService 시그니처 목록에 `presignGet(key, ttl, responseContentType)` 오버로드 반영 권장. 설계자/오케스트레이터가 01-architecture에 반영.

## 확인 필요
- 검색(name/extension)이 폴더 전역인지 현재 폴더 한정인지 계약 미명시 → name/extension 지정 시 owner 전역 검색으로 구현(folderId 무시). 폴더 한정 검색이 필요하면 계약 보강 요청.
- 다운로드: presigned URL(5분) 채택(계약 "확인 필요"의 기본안). 서버 스트리밍 미구현.
- presignGet 응답 Content-Type override 및 `presignGet(key,ttl,responseContentType)` 오버로드를 계약(storage-layout/api-contract)에 명문화할지 — 설계자 확인 필요.

## v1.6 다운로드 게이트웨이 경유 개정 (2026-06-17, presigned 브라우저 노출 폐기)
- **신규 엔드포인트**: `GET /api/files/{id}/download` (인증). `FileController`에 메서드 1개 추가.
  - **매 요청 실시간 인가**: `FileService.resolveForDownload(ownerId, id)` — `file.owner_id == 요청자` AND `status==UPLOADED`. 위반 시 403 FORBIDDEN(비소유) / 404 FILE_NOT_FOUND(없음/삭제/UPLOADED 아님). 미인증 401.
- **변경 엔드포인트**: `GET /api/files/{id}/versions/{version}/download` — 기존 `{downloadUrl}`(presigned) 반환 → **게이트웨이 스트리밍으로 의미 변경**(바디 없는 200 + 헤더). `FileService.resolveVersionForDownload`가 owner 재검증 후 `VersionDownloadTarget(objectKey, originalName)` 반환, 컨트롤러가 `GatewayDownload.redirect(...)`. 404(파일/버전 없음)/403(비소유)/401.
- **응답 헤더 규약(공통, `GatewayDownload`)**:
  - `X-Accel-Redirect: /_minio/<objectPath>?<presignedQuery>` (internal prefix `/_minio/` 고정). objectPath=버킷+키, presignedQuery=내부 전용 presign SigV4 쿼리.
  - `Content-Type`: v1.5 ContentTypes 규칙(텍스트류 `; charset=UTF-8`). presign override 대신 **컨트롤러가 헤더 직접 설정**.
  - `Content-Disposition: attachment; filename*=UTF-8''<RFC5987 percent-encoded originalName>` (한글 보존, `GatewayDownload.rfc5987`).
  - 바디 비움. internal presign은 X-Accel-Redirect 헤더에만 실려 nginx가 소비 → 브라우저 비노출.
- **`downloadUrl` 의미 변경**: `GET /api/files/{id}` 의 `downloadUrl` = `"/api/files/{id}/download"`(UPLOADED일 때, 아니면 null). presigned URL 미발급.
- **내부 presign 단기화**: `StorageProperties.internalPresignTtlSeconds`(기본 30s)로 `GatewayDownload`가 `presignGet(key, ttl, contentType)` 호출. presign URL에서 path+query만 추출해 `/_minio/`로 조립(호스트/스킴 무관).
- **v1.2 정리(cleanup)**:
  - `MINIO_PUBLIC_ENDPOINT` 폐기 — `StorageProperties.publicEndpoint` 필드/getter/setter 제거, `application.yml`의 `storage.public-endpoint` 라인 제거.
  - `S3Config.s3Presigner`가 **내부 endpoint(`storage.endpoint`=minio:9000)** 로 단일화(공개 presigner 분리 제거). put/get/presign 모두 내부 endpoint 서명.
  - `application.yml`의 `presign-download-ttl-seconds`(5분) → `internal-presign-ttl-seconds`(30초). 테스트 yml 동일 반영.
  - `FileService`에서 presign 호출 제거(`storageProps`/`presignTtl`/`Duration`/`StorageProperties` import 정리). `FileDtos.DownloadUrlResponse` 제거(미사용).
- **수정 파일**: `file/FileController.java`, `file/FileService.java`, `file/dto/FileDtos.java`, `storage/GatewayDownload.java`(신규), `storage/S3Config.java`, `storage/StorageProperties.java`, `resources/application.yml`, `test/resources/application.yml`.

### 계약 대조 (v1.6)
- [x] `GET /api/files/{id}/download`(인증, owner+UPLOADED 매 요청) 추가 — 403/404/401
- [x] `GET /api/files/{id}/versions/{version}/download` 게이트웨이 스트리밍으로 변경 — 403/404/401
- [x] `GET /api/files/{id}` downloadUrl = 게이트웨이 경로(UPLOADED) / null
- [x] X-Accel-Redirect `/_minio/...` + Content-Type(v1.5) + Content-Disposition(RFC5987), 바디 없음
- [x] internal presign 브라우저 비노출, TTL 초단기(30s)
- [x] MINIO_PUBLIC_ENDPOINT/공개 presigner 제거(presign 내부 endpoint 단일화)

### 신규/갱신 테스트
- `FileFlowTest#get_uploadedFile_downloadUrl_isGatewayPath`: 상세의 downloadUrl == `/api/files/{id}/download`(presigned 아님).
- `FileFlowTest#download_textFile_setsXAccelRedirect_utf8ContentType_and_disposition`: 한글 파일명 `한글.txt` → X-Accel-Redirect(`/_minio/`), `Content-Type: text/plain; charset=UTF-8`, `Content-Disposition: attachment; filename*=UTF-8''%ED%95%9C%EA%B8%80.txt`, 빈 바디.
- `FileFlowTest#download_nonOwner_403_and_missing_404`: 비소유 403 / 없는 id 404 / 미인증 401.
- `VersionTrashFlowTest#versionDownload_isGateway_xAccelRedirect_and_authz`: 버전 다운로드 게이트웨이 헤더 + 99버전 404 + 비소유 403 + 미인증 401.

## 빌드·테스트 (v1.6)
- 명령: `./gradlew build` / `./gradlew test --rerun-tasks`
- 결과: BUILD SUCCESSFUL. 전체 27 통과 / 0 실패(FileFlowTest 7, VersionTrashFlowTest 5, ShareFlowTest 6 포함).

## 확인 필요 (v1.6)
- 없음. nginx `/_minio/` internal location(`proxy_pass http://minio:9000/`) 추가 + MinIO 9000 host published 제거는 인프라 담당(사람 승인 게이트). 백엔드는 헤더 형식/게이트웨이 경로만 책임.
