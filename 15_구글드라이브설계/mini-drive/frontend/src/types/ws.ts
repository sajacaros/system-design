// websocket-events 계약 기반 페이로드 타입.
// 공통: { type, occurredAt } + 개별 필드.

export interface WsBaseEvent {
  type: string;
  occurredAt: string;
}

export interface FileUploadedEvent extends WsBaseEvent {
  type: "FILE_UPLOADED";
  fileId: number;
  fileName: string;
  folderId: number | null;
}

export interface FileDeletedEvent extends WsBaseEvent {
  type: "FILE_DELETED";
  fileId: number;
}

export interface ShareCreatedEvent extends WsBaseEvent {
  type: "SHARE_CREATED";
  fileId: number;
}

export interface FileUpdatedEvent extends WsBaseEvent {
  type: "FILE_UPDATED";
  fileId: number;
  version: number;
}

export type WsEvent =
  | FileUploadedEvent
  | FileDeletedEvent
  | ShareCreatedEvent
  | FileUpdatedEvent
  | WsBaseEvent;
