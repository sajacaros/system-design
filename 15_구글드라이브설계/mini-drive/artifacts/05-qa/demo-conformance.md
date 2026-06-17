# 검증 모듈: 시연 시나리오 (PRD 11장 10단계)
검증일: 2026-06-16 · 검증자: qa-reviewer · 방법: 정적 코드 매핑(런타임 미기동)

## 판정: 조건부 통과 (다운로드/공유 관련 단계는 I-2·I-3 해소 전 막힘)

## 10단계 재현 가능성 매핑

| # | 단계 | 재현 | 근거(코드) / 막힘 사유 |
| --- | --- | --- | --- |
| 1 | 회원가입 | 가능 | POST /api/auth/signup, `SignupPage.tsx`, 프런트 폼·검증 존재 |
| 2 | 로그인 | 가능 | POST /api/auth/login, 토큰 저장(`tokenStore`), RequireAuth 가드 |
| 3 | 폴더 생성 | 가능 | POST /api/folders, `useFolders`, NAME_CONFLICT 처리 |
| 4 | 파일 업로드 | 가능(단,저장 환경 의존) | POST /api/files/upload(multipart), 진행률(`files.ts` xhr.upload.onprogress), 재시도(`useUpload.retry`). MinIO 기동 필요 |
| 5 | MinIO 저장 확인 | 조건부 | `S3StorageService.put` + 키규칙 `users/{userId}/{fileId}`·`versions/{fileId}/v{n}`(`ObjectKeys`). MinIO 콘솔(:9001)로 확인 — 기동 후 |
| 6 | 타 브라우저 실시간 알림 | 가능(기동 후) | WS `/ws`+SockJS, `convertAndSendToUser` 다중 디바이스, 프런트 `useRealtime` invalidate+toast. **SockJS 정합 확인됨** |
| 7 | 다운로드 | **막힘** | presigned URL 호스트=`minio:9000`(I-3) → 브라우저 접속 불가. 도커 환경에서 다운로드 전부 실패. 호스트 분리 수정 후 가능 |
| 8 | 공유 링크 생성 → 타 사용자(비인증) 접근 | **막힘** | 생성(POST share)·SHARE_CREATED는 OK. 그러나 비인증 `/share/{token}` 접근이 nginx 라우팅 충돌(I-2)로 SPA가 데이터를 못 받음 + 다운로드는 I-3로 실패. 두 결함 해소 후 가능 |
| 9 | 버전 생성(파일 수정) | 가능(기동 후) | POST /api/files/{id}/content, baseVersion→409→overwrite, file_version 행 추가, FILE_UPDATED |
| 10 | 이전 버전 복구 | 가능(다운로드 제외) | POST /api/files/{id}/restore `{version}`, 버전 복사 생성(유실 0%), FILE_UPDATED. 복구 후 다운로드는 I-3 의존 |

## 확인된 통과 항목
- 1~6, 9~10의 **API/상태 전이/이벤트 로직**은 코드상 완비. 백엔드 테스트 14/14 통과(빌더 보고) — auth/file/folder/share/version·trash 흐름 커버.
- 파일 유실 0% 설계: object 저장 성공 후 UPLOADED 확정, 실패 시 FAILED, 버전 복구는 복사 생성(이력 보존).

## 사람 승인/위험 보고
- **시연 차단 2건(I-2 공유 라우팅, I-3 presigned 호스트)을 기동 전 해소하지 않으면 7·8단계, 그리고 10단계의 다운로드 확인이 실패한다.** 시연 핵심(공유·다운로드)이 걸려 있어 높은 심각도.
- 6단계 실시간 알림은 SockJS·nginx /ws·CONNECT JWT 모두 정합 — 기동만 하면 재현 가능.
- 런타임 검증(실제 업로드→MinIO 객체→타 브라우저 toast→다운로드)은 **인프라 기동(사람 승인) 후** 수행 권장.
