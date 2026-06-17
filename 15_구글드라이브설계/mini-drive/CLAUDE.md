# Mini Drive — 프로젝트 안내판

Google Drive 핵심 기능을 축소한 풀스택 파일 관리 서비스. 이 프로젝트에는 빌드 하네스가 있습니다.

## 기술 스택 (고정)

- Backend: Java 25, Spring Boot 4.x, Spring Security, Spring Data JPA
- Frontend: React, TypeScript, shadcn/ui, React Query, React Router (Node 24.x, pnpm)
- DB: PostgreSQL · Object Storage: MinIO(S3 호환) · 실시간: WebSocket+STOMP
- 배포: Docker Compose, Nginx
- 레이아웃: `backend/` + `frontend/` + 루트 `docker-compose.yml`

## 주요 위치

- 입구 스킬: `.claude/skills/mini-drive-build-orchestrator/SKILL.md`
- 작업 스킬: `.claude/skills/` (api-contract-design, backend-feature-build, frontend-feature-build, contract-conformance-qa)
- Agent(팀원 카드): `.claude/agents/` (architect, backend, frontend, infra, qa)
- 공유 계약(단일 출처): `artifacts/01-architecture/*`
- 중간·최종 산출물: `artifacts/`

## 자연어 라우팅

사용자가 스킬명을 직접 입력하지 않아도 Mini Drive 빌드·기능 작업으로 판단되면 **먼저 `mini-drive-build-orchestrator`를 사용**한다.

예:
- "Mini Drive 빌드해줘 / 만들어줘"
- "파일 공유 기능 추가해줘"
- "버전 관리 모듈만 다시 빌드해줘"
- "이전 결과 기반으로 보완해줘 / 업데이트해줘"
- "QA 검증만 다시 돌려줘"

## 핵심 규칙

- 공유 계약(`artifacts/01-architecture/*`)이 단일 출처다. 백엔드·프런트는 계약을 글자 그대로 구현하고, 계약에 없는 필드를 임의로 만들지 않는다.
- 파일 소유권 분리: 백엔드=`backend/`, 프런트=`frontend/`, 인프라=루트 설정. 같은 파일 동시 수정 금지.
- 검증은 모듈 완료 즉시(점진적). 전체 종료 후 한 번에 미루지 않는다.
- **사람 승인 게이트**: `docker compose up`/`down -v`, 컨테이너 기동, DB drop/reset, 파괴적 마이그레이션, 외부 배포는 실행 전 사람 확인.

## 하네스 변경 이력

| 날짜 | 변경 내용 | 대상 | 사유 |
| --- | --- | --- | --- |
| 2026-06-16 | 초기 구성 | 전체 | Mini Drive 풀스택 빌드 하네스 생성 |
