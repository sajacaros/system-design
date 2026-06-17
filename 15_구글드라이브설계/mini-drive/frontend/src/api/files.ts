import { api, API_BASE } from "./client";
import { tokenStore } from "./tokenStore";
import type {
  FileContentResponse,
  FileDetail,
  FileListItem,
  FileVersionItem,
  Page,
  RestoreResponse,
  UpdateFileRequest,
  UploadedFile,
} from "@/types/api";

export interface FileListParams {
  folderId?: number | null;
  status?: "UPLOADED" | "DELETED";
  name?: string;
  extension?: string;
  sort?: "recent";
  page?: number;
  size?: number;
}

function buildFileQuery(p: FileListParams): string {
  const params = new URLSearchParams();
  if (p.folderId != null) params.set("folderId", String(p.folderId));
  if (p.status) params.set("status", p.status);
  if (p.name) params.set("name", p.name);
  if (p.extension) params.set("extension", p.extension);
  if (p.sort) params.set("sort", p.sort);
  if (p.page != null) params.set("page", String(p.page));
  if (p.size != null) params.set("size", String(p.size));
  const s = params.toString();
  return s ? `?${s}` : "";
}

export const filesApi = {
  // GET /api/files
  list: (p: FileListParams) =>
    api.get<Page<FileListItem>>(`/files${buildFileQuery(p)}`),

  // GET /api/files/{id}
  detail: (id: number) => api.get<FileDetail>(`/files/${id}`),

  // POST /api/files/upload (multipart)
  upload: (
    file: File,
    folderId: number | null,
    onProgress?: (pct: number) => void,
    signal?: AbortSignal
  ): Promise<UploadedFile> => {
    const form = new FormData();
    form.append("file", file);
    if (folderId != null) form.append("folderId", String(folderId));
    return xhrUpload<UploadedFile>(
      `${API_BASE}/files/upload`,
      "POST",
      form,
      onProgress,
      signal
    );
  },

  // POST /api/files/{id}/content (새 버전, multipart) — baseVersion / overwrite 지원
  uploadVersion: (
    id: number,
    file: File,
    baseVersion: number,
    overwrite: boolean,
    onProgress?: (pct: number) => void,
    signal?: AbortSignal
  ): Promise<FileContentResponse> => {
    const form = new FormData();
    form.append("file", file);
    form.append("baseVersion", String(baseVersion));
    const q = overwrite ? "?overwrite=true" : "";
    return xhrUpload<FileContentResponse>(
      `${API_BASE}/files/${id}/content${q}`,
      "POST",
      form,
      onProgress,
      signal
    );
  },

  // PATCH /api/files/{id} — 이름변경/이동
  update: (id: number, req: UpdateFileRequest) =>
    api.patch<FileDetail>(`/files/${id}`, req),

  // DELETE /api/files/{id} — 휴지통 이동
  remove: (id: number) => api.delete<void>(`/files/${id}`),

  // GET /api/files/{id}/versions
  versions: (id: number) =>
    api.get<FileVersionItem[]>(`/files/${id}/versions`),

  // GET /api/files/{id}/download (v1.6 신규) — 게이트웨이 경유 인증 스트리밍 경로.
  // downloadAuthenticated 로 Bearer 헤더와 함께 호출.
  downloadPath: (id: number) => `${API_BASE}/files/${id}/download`,

  // GET /api/files/{id}/versions/{version}/download (v1.6) — 게이트웨이 경유 인증 스트리밍 경로.
  // 더 이상 presigned downloadUrl 을 추출하지 않고 이 경로를 직접 호출.
  versionDownloadPath: (id: number, version: number) =>
    `${API_BASE}/files/${id}/versions/${version}/download`,

  // POST /api/files/{id}/restore — 휴지통 복원 또는 특정 버전 복구
  restoreFromTrash: (id: number) =>
    api.post<RestoreResponse>(`/files/${id}/restore`, {}),
  restoreVersion: (id: number, version: number) =>
    api.post<RestoreResponse>(`/files/${id}/restore`, { version }),

  // DELETE /api/files/{id}/permanent
  permanentDelete: (id: number) =>
    api.delete<void>(`/files/${id}/permanent`),
};

// XHR 업로드 — 진행률 콜백 + abort 지원. 계약 에러 바디를 ApiClientError 형태로 보존.
function xhrUpload<T>(
  url: string,
  method: string,
  form: FormData,
  onProgress?: (pct: number) => void,
  signal?: AbortSignal
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(method, url);
    const token = tokenStore.getAccess();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);

    if (onProgress) {
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          onProgress(Math.round((e.loaded / e.total) * 100));
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const text = xhr.responseText;
          resolve((text ? JSON.parse(text) : undefined) as T);
        } catch {
          resolve(undefined as T);
        }
      } else {
        reject(parseXhrError(xhr));
      }
    };
    xhr.onerror = () =>
      reject({ name: "ApiClientError", status: 0, code: "NETWORK", message: "네트워크 오류" });
    xhr.onabort = () =>
      reject({ name: "AbortError", status: 0, code: "ABORTED", message: "취소됨" });

    if (signal) {
      signal.addEventListener("abort", () => xhr.abort());
    }

    xhr.send(form);
  });
}

function parseXhrError(xhr: XMLHttpRequest) {
  let code = "UNKNOWN";
  let message = xhr.statusText;
  try {
    const body = JSON.parse(xhr.responseText);
    if (body.code) code = body.code;
    if (body.message) message = body.message;
  } catch {
    // ignore
  }
  return { name: "ApiClientError", status: xhr.status, code, message };
}
