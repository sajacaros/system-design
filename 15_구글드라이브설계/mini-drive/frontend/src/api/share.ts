import { api } from "./client";
import type {
  CreateShareRequest,
  PublicSharedFile,
  ShareLink,
  ShareListItem,
} from "@/types/api";

export const shareApi = {
  // POST /api/files/{id}/share
  create: (fileId: number, req: CreateShareRequest) =>
    api.post<ShareLink>(`/files/${fileId}/share`, req),

  // GET /api/shares — 내 공유 링크 목록 (v1.4)
  list: () => api.get<ShareListItem[]>(`/shares`),

  // DELETE /api/share/{id} — 비활성화
  disable: (shareId: number) => api.delete<void>(`/share/${shareId}`),

  // GET /api/public/share/{token} — 비인증 공개 API (계약 v1.2).
  // SPA 페이지 경로 /share/{token}는 라우터가 처리하고, 그 안에서 이 API를 호출한다.
  // API_BASE(/api) 하위 상대 경로라 nginx가 백엔드로 프록시 → 라우팅 충돌 없음.
  publicGet: (token: string) =>
    api.get<PublicSharedFile>(`/public/share/${token}`, {
      auth: false,
    }),
};
