## 모듈: sync (실시간 동기화 + notification)

## 구현 범위
- STOMP 엔드포인트 `/ws` (SockJS), 하트비트 10s/10s, userDestinationPrefix `/user`.
- CONNECT 프레임 `Authorization: Bearer {accessToken}` JWT 검증 → Principal(userId) 바인딩, 실패 시 연결 거부(ChannelInterceptor).
- 발행: `convertAndSendToUser(userId, "/queue/notifications", payload)` → 같은 사용자 모든 연결(다중 디바이스).
- 4개 이벤트 페이로드(공통 {type,occurredAt} + 개별 필드):
  - FILE_UPLOADED {fileId,fileName,folderId}
  - FILE_UPDATED {fileId,version}
  - FILE_DELETED {fileId}
  - SHARE_CREATED {fileId}
- 발행과 동시에 notification 행 저장(SyncEventPublisher).
- Notifications REST: `GET /api/notifications?unread=true` → [{id,type,message,isRead,createdAt}], `PATCH /api/notifications/{id}/read` → 204.

## 계약 대조
- [x] 이벤트 타입/필드/발행 시점(업로드·새버전/PATCH/복구·삭제·공유) 일치
- [x] convertAndSendToUser 개인 큐, 다중 디바이스
- [x] CONNECT JWT 인증
- [x] notification 동시 저장 + REST 보강 조회

## 빌드·테스트
- 명령: `./gradlew test --tests '*ContextLoadTest'` (STOMP/broker 포함 컨텍스트 부팅)
- 결과: 통과 1 / 실패 0. STOMP 실연결(다중 디바이스 전달) 검증은 인프라 기동 후 통합 테스트로 표시.

## 확인 필요
- 실제 다중 디바이스 STOMP 전달 e2e는 통합 단계(브로커/네트워크) 필요 — 단위 컨텍스트 부팅까지 확보.
