import type { ApiError, RefreshResponse } from "@/types/api";
import { tokenStore } from "./tokenStore";

// API base는 프록시 기준. 절대 호스트를 하드코딩하지 않는다.
export const API_BASE = "/api";

// 계약 공통 에러 코드 → 한국어 메시지 매핑.
const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION: "입력값이 올바르지 않습니다.",
  UNAUTHENTICATED: "인증이 필요합니다.",
  FORBIDDEN: "접근 권한이 없습니다.",
  NOT_FOUND: "대상을 찾을 수 없습니다.",
  CONFLICT: "충돌이 발생했습니다.",
  PAYLOAD_TOO_LARGE: "파일 용량이 너무 큽니다.",
  EMAIL_TAKEN: "이미 사용 중인 이메일입니다.",
  BAD_CREDENTIALS: "이메일 또는 비밀번호가 올바르지 않습니다.",
  INVALID_REFRESH: "세션이 만료되었습니다. 다시 로그인해 주세요.",
  FILE_NOT_FOUND: "파일을 찾을 수 없습니다.",
  NAME_CONFLICT: "같은 이름이 이미 존재합니다.",
  CYCLIC_MOVE: "폴더를 자기 하위로 이동할 수 없습니다.",
  INVALID_LINK: "유효하지 않은 공유 링크입니다.",
  EXPIRED: "만료된 공유 링크입니다.",
  DISABLED: "비활성화된 공유 링크입니다.",
};

export class ApiClientError extends Error {
  code: string;
  status: number;
  timestamp?: string;

  constructor(status: number, code: string, message: string, timestamp?: string) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.timestamp = timestamp;
  }

  /** 사용자에게 보여줄 메시지 (코드 매핑 우선, 없으면 서버 메시지). */
  get displayMessage(): string {
    return ERROR_MESSAGES[this.code] ?? this.message ?? "오류가 발생했습니다.";
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** multipart 등 raw body 전송 시 사용 (Content-Type 자동 미설정). */
  rawBody?: BodyInit;
  /** 인증 헤더 부착 여부 (기본 true). */
  auth?: boolean;
  /** 401 시 refresh 후 재시도 여부 (내부용). */
  _retry?: boolean;
  signal?: AbortSignal;
  /** /api 외 절대 경로 호출 시 사용. */
  absolute?: boolean;
}

// ---- refresh 인터셉터 (동시 다발 401을 단일 refresh로 합침) ----
let refreshPromise: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      tokenStore.clear();
      return false;
    }
    const data = (await res.json()) as RefreshResponse;
    tokenStore.setTokens(data.accessToken, data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

function refreshOnce(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function parseError(res: Response): Promise<ApiClientError> {
  let code = "UNKNOWN";
  let message = res.statusText;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as Partial<ApiError>;
    if (body.code) code = body.code;
    if (body.message) message = body.message;
    timestamp = body.timestamp;
  } catch {
    // body 없음
  }
  return new ApiClientError(res.status, code, message, timestamp);
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, rawBody, auth = true, signal, absolute } = opts;
  const headers: Record<string, string> = {};

  if (auth) {
    const token = tokenStore.getAccess();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  let fetchBody: BodyInit | undefined;
  if (rawBody !== undefined) {
    fetchBody = rawBody;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    fetchBody = JSON.stringify(body);
  }

  const url = absolute ? path : `${API_BASE}${path}`;
  const res = await fetch(url, { method, headers, body: fetchBody, signal });

  if (res.status === 401 && auth && !opts._retry) {
    const ok = await refreshOnce();
    if (ok) {
      return request<T>(path, { ...opts, _retry: true });
    }
    tokenStore.clear();
    throw await parseError(res);
  }

  if (!res.ok) {
    throw await parseError(res);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const contentType = res.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return (await res.json()) as T;
  }
  return undefined as T;
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: "GET" }),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: "POST", body }),
  patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: "PATCH", body }),
  delete: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: "DELETE", body }),
  /** multipart 등 raw 전송. */
  raw: <T>(path: string, method: string, rawBody: BodyInit, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method, rawBody }),
};
