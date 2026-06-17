# improvement-log — 개선 기록

하네스 실행 중 발견한 실패, 사용자 피드백, 다음 수정 후보를 기록합니다. 기록으로 끝내지 않고, 사용자가 원하면 `.claude/agents`·`.claude/skills`에 반영하고 변경은 `CLAUDE.md` 변경 이력에 남깁니다.

## 형식

| 날짜 | 발견/피드백 | 대상(파일·역할) | 조치/다음 후보 |
| --- | --- | --- | --- |
| 2026-06-16 | 하네스 초기 구성 | 전체 | 첫 빌드 실행 후 계약 누락·검증 공백 점검 예정 |
| 2026-06-16 | qa-reviewer에 Write 도구 추가 | `.claude/agents/qa-reviewer.md` | 검증 리포트를 `artifacts/05-qa/`에 직접 남기려면 Write 필요(초기 정의 누락) |
| 2026-06-16 | 계약 v1.1 정합 | `01-architecture/*` | Phase B 빌더 보고 공백(다운로드/공유응답/검색범위/SockJS/표명) 단일 출처로 확정 |

| 2026-06-16 | qa 통합 결함 3건 수정 | backend/·frontend/·인프라 | I-3 presign 공개 endpoint 분리, I-2 공유 API `/api/public/share`로 이동, I-1 공유 필드 `id` 통일 |
| 2026-06-16 | Phase B~D 완료 | 전체 | 백엔드 14/14·프런트 build·인프라 config 통과, 정합 확인. 런타임 e2e는 기동 승인 대기 |
| 2026-06-16 | 프런트 Dockerfile 수정 | `frontend/Dockerfile` | 기동 시 pnpm-workspace.yaml 미COPY로 esbuild ignored-builds exit1 → COPY 추가 + rebuild esbuild |
| 2026-06-16 | 호스트 8080 충돌 회피 | `.env` BACKEND_PORT=18080 | 무관 컨테이너(policy-runtime)가 8080 점유 → 파라미터 오버라이드(시연은 nginx :80) |
| 2026-06-16 | 런타임 e2e 10/10 통과 | 전체 | 실행 스택 실측, I-2·I-3 런타임 해소 확인. 휴지통 보조조회 A6/A7 적출 |
| 2026-06-16 | 휴지통 조회 A6/A7 수정 | backend | A7 folderId=null 500+메시지누출, A6 전역 휴지통 빈목록 — 수정 후 재검증 |
| 2026-06-16 | A6/A7 런타임 해소 확인 | 전체 | 재빌드 후 실측: A7 200/400(누출 제거), A6 삭제→전역휴지통 노출→복원 검증. 테스트 16/16 |
| 2026-06-16 | 정적 서빙 이음새 결함 수정 | nginx.conf, docker-compose.yml | 루트 nginx가 빈 frontend-dist 볼륨을 서빙해 nginx 기본 페이지만 노출 → 루트 nginx가 `/`를 frontend:80(자체 SPA nginx)로 프록시하도록 변경, frontend-dist 볼륨 제거 |
| 2026-06-16 | 앱 접속 포트 변경 | `.env` HTTP_PORT=8088 | 사용자 요청(80 대신 8088). SPA·API·라우트 폴백 실측 통과 |
| 2026-06-16 | 앱 접속 포트 7915 | `.env` HTTP_PORT=7915 | 사용자 재요청. 실측 통과 |
| 2026-06-16 | '새 버전 업로드' UI 추가(보완 완료) | frontend (useUploadVersion, UploadVersionDialog, ExplorerPage, VersionsDialog) | 백엔드 POST /{id}/content는 동작했으나 전용 UI 미연결이던 보완 후보 해소. 409→덮어쓰기 확인 포함. 재빌드 배포 |

| 2026-06-16 | 공유 링크 관리 기능 추가 | 계약 v1.4, backend(GET /api/shares), frontend(/shares 페이지, 절대 URL 헬퍼) | 공유 링크 절대 URL(http 호스트) 표시+복사, 내 공유 목록·상태·비활성화 관리 페이지. 런타임 검증 통과 |

| 2026-06-16 | 공유 관리 페이지 필터 추가 | frontend(SharesPage) | 상태(활성/만료/비활성)+파일명 클라이언트 필터 |
| 2026-06-16 | 한글 텍스트 인코딩 깨짐 수정 | backend(ContentTypes, presignGet override), 계약 v1.5 | 다운로드 Content-Type에 charset=UTF-8 강제(텍스트류). 기존 파일도 재업로드 없이 교정. 런타임 검증 통과(테스트 24/0) |

## 다음 점검 후보

- 첫 빌드 후: 계약(`01-architecture/`)에 In-Scope 기능이 모두 반영됐는지
- 검증 공백: 충돌 처리(Conflict)·공유 토큰 만료의 테스트 커버리지
- 팀 규모: 한 팀원 Task가 6개를 넘으면 Phase 분할 검토
