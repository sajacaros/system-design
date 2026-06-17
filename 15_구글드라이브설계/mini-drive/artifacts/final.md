# Mini Drive — 빌드 요약 & 시연 런북 (final)

작성: 2026-06-16 / Orchestrator: mini-drive-build-orchestrator

## 1. 빌드 결과 요약

| 영역 | 결과 | 근거 |
| --- | --- | --- |
| 계약(단일 출처) | ✅ v1.2 | `artifacts/01-architecture/*` (빌드 중 공백 5건 + qa 결함 3건 반영) |
| 백엔드 | ✅ `./gradlew build` SUCCESSFUL, 테스트 14/14 | Java 25, Spring Boot 4.0.7, JPA, Security/JWT, AWS SDK v2 S3, STOMP |
| 프런트 | ✅ `pnpm build`+`tsc --noEmit` 통과 | React+TS+Vite+shadcn, React Query, React Router, SockJS |
| 인프라 | ✅ `docker compose config` exit 0 | PG16, MinIO, Nginx, 6 서비스 |
| qa 교차 검증 | ✅ 결함 3건 적출→수정→정합 확인 | `artifacts/05-qa/*` |

## 2. 구현 범위 (PRD In-Scope 전부)

- 인증(JWT Access/Refresh 회전, 로그아웃 무효화)
- 폴더(생성/이동/이름변경/연쇄 휴지통, CYCLIC_MOVE 방지)
- 파일(업로드/다운로드 presign/이름변경/이동/상태머신/충돌 baseVersion→409→overwrite)
- 검색(이름/확장자/최근, 소유자 전역)
- 공유(토큰/만료/is_active 비활성화, 비인증 공개 조회)
- 버전(생성/히스토리/복구/특정버전 다운로드)
- 휴지통(보관/복구/영구삭제)
- 실시간(STOMP `/ws` SockJS, 4개 이벤트 + notification, 다중 디바이스 동기화)

## 3. qa가 잡은 결함과 처리

| # | 결함 | 처리 |
| --- | --- | --- |
| I-3 | presigned URL 호스트가 내부 DNS `minio:9000` → 브라우저 미해석(다운로드 전부 실패) | `MINIO_PUBLIC_ENDPOINT`(presign 전용) 분리. 백엔드 presigner 공개 endpoint 구성 + 인프라 env + 9000 published |
| I-2 | nginx `/share/{token}` 라우팅 충돌(SPA 폴백) | 공개 API를 `/api/public/share/{token}`로 이동(보안 permitAll), SPA 페이지 `/share/{token}` 유지 |
| I-1 | 공유 응답 `fileId` vs 계약 `id`, 프런트 타입 누락 | 백엔드 `id` 통일 + 프런트 타입 `id`/`extension` 보강 |

SockJS 정합·보안(JWT 회전·소유자 인가 403·공유 토큰 만료·BCrypt)은 통과.

## 4. 시연 런북 (PRD 11장 10단계)

> 접속 URL: 앱 **http://localhost:8088** (`.env`의 `HTTP_PORT`), MinIO 콘솔 **http://localhost:9001**, 백엔드 직접 `http://localhost:18080`(`BACKEND_PORT`). 루트 nginx가 `/`→frontend:80, `/api`·`/ws`→backend:8080로 프록시.
>
> ⚠️ 아래 기동 명령은 **사람 승인 게이트**입니다. 컨테이너 기동/이미지 빌드는 승인 후 실행하세요.

```bash
cp .env.example .env          # 시크릿 값 채우기(JWT_SECRET 등)
docker compose up -d --build  # [승인 필요] PG·MinIO·backend·frontend·nginx 기동
docker compose ps             # 헬스 확인
```

| 단계 | 시연 | 확인 |
| --- | --- | --- |
| 1 | 회원가입 | `http://localhost` → signup |
| 2 | 로그인 | accessToken 발급 |
| 3 | 폴더 생성 | 탐색기에서 새 폴더 |
| 4 | 파일 업로드 | 진행률 표시, status UPLOADED |
| 5 | MinIO 저장 확인 | `http://localhost:9001` 콘솔, `minidrive` 버킷 `users/{userId}/{fileId}` |
| 6 | 타 브라우저 실시간 알림 | 다른 브라우저 로그인 → 업로드 시 toast |
| 7 | 다운로드 | presigned URL(localhost:9000) 다운로드 — I-3 수정으로 동작 |
| 8 | 공유 링크 생성→타 사용자 접근 | `/share/{token}` 비인증 페이지 — I-2 수정으로 동작 |
| 9 | 파일 수정→버전 생성 | 새 버전 업로드, FILE_UPDATED |
| 10 | 이전 버전 복구 | versions에서 복구 → 다운로드 |

## 4b. 런타임 e2e 결과 (2026-06-16, 실행 스택 실측)

- **시연 10단계 10/10 통과** (`artifacts/05-qa/e2e-conformance.md`).
- I-3 해소: 모든 downloadUrl이 `localhost:9000`로 발급 → 다운로드 200 + sha256 일치.
- I-2 해소: `/api/public/share/{token}` 비인증 200 + 다운로드 동작.
- MinIO `users/1/1`+`versions/1/v1`, 401/403, 409 충돌, v1/v2/v3 이력 보존, 공유 410 DISABLED/EXPIRED, SockJS `/ws/info` 200.
- 기동 이슈 2건 해소: 프런트 Dockerfile(pnpm-workspace.yaml COPY 누락), 호스트 8080 충돌(BACKEND_PORT=18080).
- 추가 적출→해소(시연 외): A7 `folderId=null&status=DELETED` 500/메시지누출 → 200/400(누출 제거), A6 전역 휴지통 → 삭제 파일 노출·복원 동작 실측 확인. 백엔드 테스트 16/16.

## 5. 남은 항목 (정직한 기록)

- **WebSocket 실시간 toast(다중 브라우저, 1초 이내)**: curl 검증 불가 → 브라우저 2개로 **수동 확인 권장**.
- 썸네일/미리보기 생성 파이프라인: 키 규칙만 확정, 생성은 후속(보완).
- ~~새 버전 업로드 전용 UI~~: ✅ 완료(2026-06-16) — 탐색기 ⋮메뉴/버전 패널에 "새 버전 업로드" + 409 덮어쓰기 확인.
- Access TTL 900s: 시연 중 잦은 refresh 가능 — 필요 시 상향.

## 6. 다음 행동

1. (사람 승인) `docker compose up -d --build`로 기동.
2. qa-reviewer로 런타임 e2e 재검증(시연 10단계 실측).
3. 발견 사항을 `improvement-log.md`에 기록 후 보완.
