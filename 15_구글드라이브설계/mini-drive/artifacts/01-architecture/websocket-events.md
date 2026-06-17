# websocket-events — 실시간 동기화 (계약 v1)

## 연결

- 엔드포인트: `/ws` (SockJS fallback 허용), STOMP 프로토콜.
- 인증: CONNECT 프레임 `Authorization: Bearer {accessToken}` 헤더의 JWT를 검증해 Principal(userId) 바인딩. 실패 시 연결 거부.
- 하트비트: 기본 10s/10s.

## 구독

- 사용자 개인 큐: `/user/queue/notifications`
  - Spring `convertAndSendToUser(userId, "/queue/notifications", payload)`로 발행.
  - 같은 사용자의 모든 연결(다중 디바이스)에 전달 → 다중 디바이스 동기화. 한 디바이스의 변경이 다른 디바이스에 1초 이내 반영되는 것이 목표.

## 이벤트 페이로드

모든 이벤트 공통: `{ type, occurredAt }` + 개별 필드.

```json
{ "type": "FILE_UPLOADED", "fileId": 1, "fileName": "report.pdf", "folderId": 3, "occurredAt": "..." }
{ "type": "FILE_DELETED",  "fileId": 1, "occurredAt": "..." }
{ "type": "SHARE_CREATED", "fileId": 1, "occurredAt": "..." }
{ "type": "FILE_UPDATED",  "fileId": 1, "version": 2, "occurredAt": "..." }
```

## 발행 시점 (백엔드)

| 이벤트 | 발행 트리거 |
| --- | --- |
| FILE_UPLOADED | 업로드 완료(status=UPLOADED) |
| FILE_UPDATED | 새 버전 업로드 / 이름변경·이동(PATCH) / 버전 복구 |
| FILE_DELETED | 휴지통 이동(DELETE) |
| SHARE_CREATED | 공유 링크 생성 |

발행 대상: 자원 owner_id. (PRD 범위상 다른 사용자 공유 구독은 없음.)
이벤트 발행과 동시에 notification 행을 저장(미수신분은 `GET /api/notifications`로 보강).

## 프런트 처리 규칙

- 구독 후 수신 이벤트 타입에 따라 React Query 캐시 무효화:
  - FILE_* → 해당 folderId 파일 목록, 파일 상세 invalidate.
  - SHARE_CREATED → 해당 파일 공유 상태 invalidate.
- 1초 이내 화면 갱신 목표. 토큰 만료로 연결 끊기면 refresh 후 재연결.

## 계약 v1.1 정합 메모 (2026-06-16, Phase B 빌드 반영)
- **전송 방식 = SockJS 필수**: 프런트가 SockJS 클라이언트를 사용하므로 백엔드는 `/ws` STOMP 엔드포인트에 `.withSockJS()`를 반드시 등록한다. (순수 WebSocket-only면 프런트 `webSocketFactory` 교체 필요 → 불일치 시 백엔드가 SockJS 등록으로 맞춘다.) **qa 교차 검증 대상.**
- WS 단독 토큰 만료: REST 401 인터셉터가 갱신한 토큰으로 재연결. WS만 끊긴 경우도 동일 토큰 재사용으로 충분(별도 강제 refresh 불요).

## 확인 필요
- 없음.
