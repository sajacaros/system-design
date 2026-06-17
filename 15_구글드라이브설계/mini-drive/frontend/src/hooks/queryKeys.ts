import type { FileListParams } from "@/api/files";

export const qk = {
  folders: (parentId: number | null) => ["folders", parentId] as const,
  files: (params: FileListParams) => ["files", params] as const,
  filesAll: ["files"] as const,
  file: (id: number) => ["file", id] as const,
  versions: (id: number) => ["versions", id] as const,
  shares: ["shares"] as const,
  notifications: (unread?: boolean) =>
    ["notifications", unread ?? false] as const,
};
