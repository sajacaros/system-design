import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { tokenStore } from "@/api/tokenStore";
import type { WsEvent } from "@/types/ws";

// websocket-events 계약:
// - 엔드포인트 /ws (SockJS fallback), STOMP.
// - CONNECT 프레임에 Authorization: Bearer {accessToken}.
// - 개인 큐 /user/queue/notifications 구독.
// - 토큰 만료로 끊기면 refresh 후 재연결.

type EventHandler = (event: WsEvent) => void;

let client: Client | null = null;
const handlers = new Set<EventHandler>();

export function subscribeStomp(handler: EventHandler): () => void {
  handlers.add(handler);
  if (!client) connect();
  return () => {
    handlers.delete(handler);
    if (handlers.size === 0) disconnect();
  };
}

function connect() {
  const token = tokenStore.getAccess();
  if (!token) return;

  client = new Client({
    // SockJS 사용: /ws 프록시 경로.
    webSocketFactory: () => new SockJS("/ws"),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    reconnectDelay: 3000,
    onConnect: () => {
      client?.subscribe("/user/queue/notifications", (msg: IMessage) => {
        try {
          const payload = JSON.parse(msg.body) as WsEvent;
          handlers.forEach((h) => h(payload));
        } catch {
          // 무시
        }
      });
    },
    onStompError: () => {
      // 서버 오류
    },
    onWebSocketClose: () => {
      // 토큰 만료 등으로 닫힌 경우, 최신 토큰으로 재연결되도록 헤더 갱신.
      const latest = tokenStore.getAccess();
      if (client && latest) {
        client.connectHeaders = { Authorization: `Bearer ${latest}` };
      }
    },
  });

  client.activate();
}

function disconnect() {
  if (client) {
    void client.deactivate();
    client = null;
  }
}
