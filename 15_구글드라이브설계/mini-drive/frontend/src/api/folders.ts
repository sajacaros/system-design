import { api } from "./client";
import type {
  CreateFolderRequest,
  Folder,
  FolderListItem,
  UpdateFolderRequest,
} from "@/types/api";

export const foldersApi = {
  // GET /api/folders?parentId={id|null}
  list: (parentId: number | null) => {
    const q = parentId == null ? "" : `?parentId=${parentId}`;
    return api.get<FolderListItem[]>(`/folders${q}`);
  },

  create: (req: CreateFolderRequest) => api.post<Folder>("/folders", req),

  update: (id: number, req: UpdateFolderRequest) =>
    api.patch<Folder>(`/folders/${id}`, req),

  remove: (id: number) => api.delete<void>(`/folders/${id}`),
};
