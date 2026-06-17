# 00-input — 빌드 입력

## 목표

Google Drive 핵심 기능을 축소한 풀스택 파일 관리 서비스 Mini Drive를 빌드한다.
완료 기준: PRD 11장 시연 시나리오 10단계가 로컬 docker-compose에서 재현된다.

## 범위 (PRD 기준)

- In-Scope: 회원가입/로그인, 업로드/다운로드, 폴더 관리, 공유, 버전 관리, 다중 디바이스 동기화, 실시간 알림, 휴지통, 검색
- Out-of-Scope: 실시간 공동편집, 이미지/Office 편집기, 오프라인 편집, 세부 권한(RBAC)

## 기술 제약 (고정)

- Backend: Java 25, Spring Boot 4.x, Spring Security, Spring Data JPA
- Frontend: React, TypeScript, shadcn/ui, React Query, React Router, Node 24.x, pnpm
- DB: PostgreSQL · Storage: MinIO(S3 호환) · 실시간: WebSocket+STOMP
- 배포: Docker Compose, Nginx
- 레이아웃: `backend/` + `frontend/` + 루트 `docker-compose.yml`
- 빌드 범위: 전체 기능 한 번에

## 비기능 요구

- 파일 유실 0%, 업로드 실패 재시도, 버전 복구
- 파일 목록 조회 500ms 이하, 실시간 알림 1초 이하
- API 수평 확장, MinIO→AWS S3 전환 가능(S3 인터페이스 추상화)

## 사람 승인 지점

- `docker compose up`/`down -v`, 컨테이너 기동
- DB drop/reset, 파괴적 마이그레이션
- 외부 배포, 시크릿 노출 위험 작업

## 열린 질문 / 확인 필요

- (현재 없음 — PRD와 사용자 답변으로 범위·스택·레이아웃 확정됨)

## 참고

PRD 원문은 사용자 요청 메시지에 포함됨. 데이터 모델·REST API·WebSocket 이벤트·MinIO 저장 구조의 1차 출처는 PRD 7~10장이며, 설계자가 이를 `01-architecture/`로 확정한다.
