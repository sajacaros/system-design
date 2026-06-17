# artifacts — 산출물 지도

이 폴더는 Mini Drive 빌드의 입력·중간 결과·검증·최종 결과·개선 기록이 어디에 남는지 보여주는 지도입니다. 다음 실행이 무엇을 읽어야 하는지는 아래 표를 따릅니다.

## 산출물 계약

| 단계 | 파일/폴더 | 만드는 역할 | 다음에 읽는 역할 |
| --- | --- | --- | --- |
| 입력 정리 | `00-input.md` | Orchestrator | 모든 역할 |
| 공유 계약 ⭐ | `01-architecture/` | 설계자(architect) | 백엔드·프런트·인프라·검증 |
| 백엔드 빌드 | `02-backend/{module}.md` | backend-engineer | 검증, Orchestrator |
| 프런트 빌드 | `03-frontend/{feature}.md` | frontend-engineer | 검증, Orchestrator |
| 인프라 | `04-infra/compose-notes.md` | infra-engineer | 검증, Orchestrator |
| 검증 | `05-qa/{module}-conformance.md` | qa-reviewer | 빌더(반려), Orchestrator |
| 최종 | `final.md` | Orchestrator | 사용자, 다음 실행 |
| 개선 기록 | `improvement-log.md` | Orchestrator | 다음 개선 작업 |

## 01-architecture (단일 출처)

- `db-schema.md` — 6개 테이블 DDL, 파일 상태머신
- `api-contract.md` — 엔드포인트별 요청/응답/에러/인가
- `websocket-events.md` — STOMP 토픽 + 4개 이벤트
- `storage-layout.md` — MinIO object-key 규칙, S3 추상화

## 모듈 목록

auth · files · folders · search · share · versions · trash · sync(realtime)

## 실행 방법

`mini-drive-build-orchestrator` 스킬이 입구입니다. "Mini Drive 빌드해줘"처럼 자연어로 요청하면 Orchestrator가 `artifacts/` 상태를 보고 초기 실행/부분 재실행/새 실행을 분기합니다.
