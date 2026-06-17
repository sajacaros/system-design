// 계약 기반 TypeScript 타입 — artifacts/01-architecture/api-contract.md 와 글자 그대로 일치.
// 계약에 없는 응답 필드는 추가하지 않는다.

// ---- 공통 ----

export type FileStatus =
  | "PENDING"
  | "UPLOADING"
  | "UPLOADED"
  | "FAILED"
  | "DELETED";

export interface ApiError {
  code: string;
  message: string;
  timestamp: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

// ---- Authentication ----

export interface AuthUser {
  id: number;
  email: string;
  nickname: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

// POST /api/auth/signup → 201 { id, email, nickname }
export type SignupResponse = AuthUser;

export interface LoginRequest {
  email: string;
  password: string;
}

// POST /api/auth/login → 200 { accessToken, refreshToken, user }
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

export interface RefreshRequest {
  refreshToken: string;
}

// POST /api/auth/refresh → 200 { accessToken, refreshToken }
export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

// ---- Folders ----

// GET /api/folders → 200 [{ id, parentId, name, createdAt }]
export interface FolderListItem {
  id: number;
  parentId: number | null;
  name: string;
  createdAt: string;
}

// POST /api/folders → 201 { id, parentId, name }
// PATCH /api/folders/{id} → 200 { id, parentId, name }
export interface Folder {
  id: number;
  parentId: number | null;
  name: string;
}

export interface CreateFolderRequest {
  parentId: number | null;
  name: string;
}

export interface UpdateFolderRequest {
  name?: string;
  parentId?: number | null;
}

// ---- Files ----

// GET /api/files → 페이지 of:
export interface FileListItem {
  id: number;
  folderId: number | null;
  originalName: string;
  extension: string | null;
  fileSize: number;
  version: number;
  status: FileStatus;
  updatedAt: string;
}

// POST /api/files/upload → 201
export interface UploadedFile {
  id: number;
  folderId: number | null;
  originalName: string;
  extension: string | null;
  fileSize: number;
  version: number;
  status: FileStatus;
}

// GET /api/files/{id} → 200 파일 메타 + downloadUrl
// 계약 v1.6: downloadUrl 은 게이트웨이 다운로드 경로(상대경로). presigned URL 아님.
// UPLOADED 일 때 "/api/files/{id}/download", 아니면 null.
export interface FileDetail {
  id: number;
  folderId: number | null;
  originalName: string;
  extension: string | null;
  fileSize: number;
  version: number;
  status: FileStatus;
  updatedAt: string;
  downloadUrl: string | null;
}

export interface UpdateFileRequest {
  originalName?: string;
  folderId?: number | null;
}

// POST /api/files/{id}/content → 200 { id, version, status }
export interface FileContentResponse {
  id: number;
  version: number;
  status: FileStatus;
}

// GET /api/files/{id}/versions → 200 [{ version, fileSize, createdAt }]
export interface FileVersionItem {
  version: number;
  fileSize: number;
  createdAt: string;
}

// POST /api/files/{id}/restore → 200 { id, version }
export interface RestoreResponse {
  id: number;
  version: number;
}

// ---- Share ----

// POST /api/files/{id}/share → 201 { id, token, url, expiredAt, isActive }
export interface ShareLink {
  id: number;
  token: string;
  url: string;
  expiredAt: string | null;
  isActive: boolean;
}

export interface CreateShareRequest {
  expiredAt?: string | null;
}

// GET /api/shares → 200 [{ id, fileId, fileName, token, url, expiredAt, isActive, createdAt }]
// 계약 v1.4: 내 공유 링크 목록(소유 파일). url 은 상대경로, 정렬 createdAt DESC.
export interface ShareListItem {
  id: number;
  fileId: number;
  fileName: string;
  token: string;
  url: string;
  expiredAt: string | null;
  isActive: boolean;
  createdAt: string;
}

// GET /api/public/share/{token} → 비인증 공개 읽기전용.
// 계약 v1.2: { id, originalName, extension, fileSize, downloadUrl }
// 계약 v1.6: downloadUrl 은 게이트웨이 다운로드 경로(상대경로)
//   "/api/public/share/{token}/download". presigned URL 아님(비인증 직접 호출 가능).
export interface PublicSharedFile {
  id: number;
  originalName: string;
  extension: string | null;
  fileSize: number;
  downloadUrl: string;
}

// ---- Notifications ----

export type NotificationType =
  | "FILE_UPLOADED"
  | "FILE_UPDATED"
  | "FILE_DELETED"
  | "SHARE_CREATED";

// GET /api/notifications → 200 [{ id, type, message, isRead, createdAt }]
export interface NotificationItem {
  id: number;
  type: NotificationType | string;
  message: string;
  isRead: boolean;
  createdAt: string;
}
