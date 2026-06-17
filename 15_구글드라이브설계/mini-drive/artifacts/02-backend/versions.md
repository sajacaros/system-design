## 모듈: versions

## 구현 범위
- 엔드포인트:
  - `GET /api/files/{id}/versions` → [{version,fileSize,createdAt}] version DESC
  - `GET /api/files/{id}/versions/{version}/download` → {downloadUrl} presigned
  - `POST /api/files/{id}/restore` (body {version}) → 해당 버전을 새 버전으로 복사 생성, 응답 {id,version}
  - 새 버전 생성은 `POST /api/files/{id}/content`(files 모듈)
- 엔티티/테이블: `file_version`(UNIQUE(file_id,version)).
- 이벤트: 버전 복구 시 FILE_UPDATED.

## 계약 대조
- [x] version DESC 정렬, 특정 버전 presigned 다운로드
- [x] 복구 = 이전 버전을 새 버전으로 복사(이력 보존, 유실 0%): MinIO copy `versions/{id}/v{k}`→`v{n}` + 현재키 갱신
- [x] 인가 owner 검증

## 빌드·테스트
- 명령: `./gradlew test --tests '*VersionTrashFlowTest'`
- 결과: 통과 2 / 실패 0 (버전 히스토리+복구, 휴지통 규칙).

## v1.6 버전 다운로드 게이트웨이 경유 (2026-06-17)
- `GET /api/files/{id}/versions/{version}/download`: 기존 `{downloadUrl}`(presigned) 반환 → **게이트웨이 스트리밍**으로 의미 변경.
  - 인가: 매 요청 `file.owner_id == 요청자` 재검증(`FileService.resolveVersionForDownload`). 404(파일/버전 없음)/403(비소유)/401(미인증).
  - 응답: 바디 없는 200 + `X-Accel-Redirect: /_minio/<versions/{fileId}/v{version} objectPath>?<presignQuery>`, `Content-Type`(v1.5 규칙), `Content-Disposition`(RFC5987). nginx가 MinIO에서 직접 스트리밍.
  - `FileDtos.DownloadUrlResponse`(presigned 래핑) 제거. 상세는 files.md 참조.
- 테스트: `VersionTrashFlowTest#versionDownload_isGateway_xAccelRedirect_and_authz`(헤더+인가 403/404/401). 전체 27/0 통과.

## 확인 필요
- 없음.
