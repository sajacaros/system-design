## 화면: notifications
STOMP 실시간 구독 → 캐시 무효화 + 토스트, 알림 벨(미수신분 조회/읽음).

## 사용 엔드포인트/이벤트
- WS /ws (SockJS), STOMP. CONNECT 헤더 Authorization: Bearer {accessToken}.
- 구독: /user/queue/notifications
- 이벤트: FILE_UPLOADED / FILE_UPDATED / FILE_DELETED / SHARE_CREATED ({ type, occurredAt } + 개별필드)
- GET /api/notifications?unread=true → [{ id, type, message, isRead, createdAt }]
- PATCH /api/notifications/{id}/read → 204

## 계약 대조
- [x] 페이로드 타입 일치 — fileId/fileName/folderId/version 등 계약 필드만 사용.
- [x] 구독 후 캐시 무효화: FILE_* → files + file + versions invalidate, SHARE_CREATED → file invalidate, 공통 notifications invalidate.
- [x] 인증: CONNECT에 accessToken 헤더. 연결 종료 시 최신 토큰으로 재연결 헤더 갱신(reconnectDelay 3s).
- [x] 실시간 구독·캐시 무효화 — 이벤트 수신 즉시 invalidate(1초 이내 반영 목표). 다중 디바이스: 개인 큐 구독으로 동일 사용자 모든 연결 수신.

## 빌드·타입체크
- 명령: `pnpm build`
- 결과: 통과

## 확인 필요
- 토큰 만료로 WS 끊김 시 명시적 refresh 트리거는 없음(REST 401 인터셉터가 refresh하면 tokenStore가 갱신되고, WS는 reconnect 시 갱신된 토큰 사용). WS 단독 만료 상황에서 강제 refresh 필요 여부 확인.
- SockJS 사용 가정(/ws). 백엔드가 순수 WebSocket만 허용하면 webSocketFactory를 native WebSocket으로 교체 필요.
