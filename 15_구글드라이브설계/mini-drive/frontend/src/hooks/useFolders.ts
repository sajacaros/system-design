import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { foldersApi } from "@/api/folders";
import { qk } from "./queryKeys";
import type {
  CreateFolderRequest,
  UpdateFolderRequest,
} from "@/types/api";

export function useFolders(parentId: number | null) {
  return useQuery({
    queryKey: qk.folders(parentId),
    queryFn: () => foldersApi.list(parentId),
  });
}

export function useCreateFolder(parentId: number | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateFolderRequest) => foldersApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.folders(parentId) });
    },
  });
}

export function useUpdateFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; req: UpdateFolderRequest }) =>
      foldersApi.update(vars.id, vars.req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["folders"] });
    },
  });
}

export function useDeleteFolder(parentId: number | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => foldersApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.folders(parentId) });
      qc.invalidateQueries({ queryKey: ["files"] });
    },
  });
}
