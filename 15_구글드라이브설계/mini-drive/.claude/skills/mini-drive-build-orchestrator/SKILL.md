---
name: mini-drive-build-orchestrator
description: Mini Drive 파일 관리 서비스를 풀스택으로 빌드·확장할 때 사용하는 입구 스킬이다. "Mini Drive 만들어줘", "이 기능 추가/이어서 빌드", "재빌드/다시 빌드", "{모듈}만 다시", "보완/업데이트/이전 결과 기반 개선" 같은 요청에 사용한다. 설계자·백엔드·프런트·인프라·검증 팀을 묶어 공유 계약 기준으로 빌드한다.
---

# Mini Drive Build Orchestrator

## 목적

이 Orchestrator는 Mini Drive 풀스택 빌드를 5개 역할(설계자/백엔드/프런트/인프라/검증)로 나누고, **하나의 공유 계약**을 단일 출처로 두어 중간 산출물을 이어가며 동작하는 앱을 만든다. 완료 기준은 PRD 시연 시나리오 10단계가 로컬 docker-compose에서 재현되는 것이다.

기술 제약(고정): Java 25, Spring Boot 4.x, Spring Security/Data JPA, PostgreSQL, MinIO(S3 호환), WebSocket+STOMP, React+TypeScript+shadcn/ui, React Query, React Router, Node 24.x, pnpm, Docker Compose, Nginx. 레이아웃: `backend/` + `frontend/` + 루트 `docker-compose.yml`. 빌드 범위: **전체 기능 한 번에**.

## 실행 모드

기본 후보는 **Agent Team(하이브리드)**이다.
- Phase A 계약 확정: 설계자 주도 단일 흐름.
- Phase B 병렬 빌드: Agent Team(백/프런트/인프라 동시, 파일 소유권 분리).
- Phase C 점진 검증: Producer-Reviewer(모듈 완료 즉시 검증).
- Phase D 통합/시연: Orchestrator 통합.

## 실행 모드 확인 (먼저 분기)

1. `artifacts/` 존재 여부를 확인한다.
2. 없으면 **초기 실행**(전체 계약→빌드→검증).
3. 있고 사용자가 특정 모듈/기능만 요청하면 **부분 재실행**(해당 계약·모듈·검증만, 영향 모듈 통지).
4. 새 입력으로 다시 시작하면 기존 산출물을 `artifacts/archive/{날짜시각}/`에 보존 후 새 실행.
5. 의도가 불분명하면 "이어 하기 / 부분 수정 / 새 실행" 중 무엇인지 먼저 묻는다.

후속 키워드: 재빌드, 다시 빌드, 이어서, {모듈}만, 보완, 업데이트, 이전 결과 기반, 리팩터.

## Agent Team 구성 (`TeamCreate`)

| 팀원 | Agent 파일 | 역할 | 주요 산출물 |
| --- | --- | --- | --- |
| 설계자 | `.claude/agents/mini-drive-architect.md` | 공유 계약 확정 | `artifacts/01-architecture/*` |
| 백엔드 | `.claude/agents/backend-engineer.md` | Spring Boot 구현 | `backend/`, `artifacts/02-backend/*` |
| 프런트 | `.claude/agents/frontend-engineer.md` | React 구현 | `frontend/`, `artifacts/03-frontend/*` |
| 인프라 | `.claude/agents/infra-engineer.md` | compose/PG/MinIO/Nginx | `docker-compose.yml`, `artifacts/04-infra/*` |
| 검증 | `.claude/agents/qa-reviewer.md` | 계약·보안·NFR·시연 검증 | `artifacts/05-qa/*` |

## Task 등록 계약 (`TaskCreate`)

| Task | 담당 | 입력 | 출력 | 의존 | 완료 기준 |
| --- | --- | --- | --- | --- | --- |
| T01-input | Orchestrator | 사용자/PRD | `00-input.md` | 없음 | 목표·제약·승인 지점 정리 |
| T02-contract | 설계자 | `00-input.md` | `01-architecture/*` | T01 | 4개 계약 확정, In-Scope 전부 표현 |
| T03-infra | 인프라 | 계약 | compose+`04-infra/*` | T02 | `docker compose config` 통과 |
| T04-backend-{module} | 백엔드 | 계약 | `backend/`+`02-backend/{m}.md` | T02 | 모듈 빌드·테스트 통과 |
| T05-frontend-{feature} | 프런트 | 계약 | `frontend/`+`03-frontend/{f}.md` | T02 | 타입체크·빌드 통과 |
| T06-qa-{module} | 검증 | 빌드 산출물 | `05-qa/{m}-conformance.md` | 해당 T04/T05 | 계약·보안·NFR 통과 |
| T07-integrate | Orchestrator | 전체 산출물 | `final.md` | T03~T06 | 시연 10단계 재현 |

모듈 목록: auth, files, folders, search, share, versions, trash, sync(realtime). 한 팀원당 Task 3-6개 유지. 너무 많으면 Phase를 나눈다.

## 실행 흐름

1. 요청 정리 → `artifacts/00-input.md`(PRD·제약·승인 지점·열린 질문) 저장.
2. `TeamCreate`로 5명 구성, `TaskCreate`로 Task 등록.
3. 설계자가 계약 확정 → `SendMessage`로 "계약 v1 완료" 브로드캐스트(빌더 차단 해제).
4. 인프라/백엔드/프런트 병렬 빌드. 각자 `TaskUpdate`로 시작·차단·완료 갱신. 파일 소유권 분리 준수.
5. Orchestrator는 Phase 전환 전·지연 의심 시 `TaskGet`으로 누락·의존 막힘 확인.
6. 모듈 완료 즉시 해당 빌더가 검증자에게 완료 메시지 → 검증자가 `05-qa/`에 리포트. 위반은 `SendMessage`로 반려.
7. 충돌 발견 시 양쪽 근거 보존 후 설계자가 계약 재정 → 영향 모듈 재빌드.
8. **사람 승인 게이트** 통과 후 통합·시연.
9. `final.md`(빌드 요약+시연 런북) 저장, `improvement-log.md` 갱신.
10. `TeamDelete`로 팀 정리.

## 사람 승인 게이트 (멈추고 확인)

- `docker compose up`/`down -v`, 볼륨·이미지 삭제, 컨테이너 기동
- DB drop/reset, 파괴적 마이그레이션
- 외부 배포, 시크릿 노출 위험 작업

이 작업들은 설정 검증까지만 자동 수행하고, 실행 전 사용자에게 영향도를 보고하고 승인을 받는다.

## 실패 처리

- 입력 부족: 추측 금지, 질문 목록 작성.
- 팀원 1명 멈춤: `TaskGet` 확인 → `SendMessage`로 1회 재시도/재할당, 부분 산출물 보존.
- 계약 충돌: 한쪽 삭제 금지, 출처·근거 병기 후 설계자 재정.
- 과반 실패: 진행 중단, 현재 상태·선택지 사용자 보고.
- 검증 반려: 담당 빌더가 수정 후 재검증, qa 리포트 갱신.

## 데이터 전달

진행 상태는 `TaskCreate/TaskUpdate/TaskGet`, 실시간 조율은 `SendMessage`, 다음 단계가 반드시 읽을 내용은 `artifacts/` 파일. 메시지에만 중요한 결정을 남기지 않는다.
