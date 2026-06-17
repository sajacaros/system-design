import {
  useMutation,
  useQuery,
  useQueryClient,
  keepPreviousData,
} from "@tanstack/react-query";
import { filesApi, type FileListParams } from "@/api/files";
import { qk } from "./queryKeys";
import type { UpdateFileRequest } from "@/types/api";

export function useFiles(params: FileListParams) {
  return useQuery({
    queryKey: qk.files(params),
    queryFn: () => filesApi.list(params),
    placeholderData: keepPreviousData,
  });
}

export function useFileDetail(id: number | null) {
  return useQuery({
    queryKey: id != null ? qk.file(id) : ["file", "none"],
    queryFn: () => filesApi.detail(id as number),
    enabled: id != null,
  });
}

export function useFileVersions(id: number | null) {
  return useQuery({
    queryKey: id != null ? qk.versions(id) : ["versions", "none"],
    queryFn: () => filesApi.versions(id as number),
    enabled: id != null,
  });
}

export function useUpdateFile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; req: UpdateFileRequest }) =>
      filesApi.update(vars.id, vars.req),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["files"] });
      qc.invalidateQueries({ queryKey: qk.file(vars.id) });
    },
  });
}

export function useDeleteFile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => filesApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["files"] });
    },
  });
}

export function useRestoreFromTrash() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => filesApi.restoreFromTrash(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["files"] });
    },
  });
}

export function useRestoreVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; version: number }) =>
      filesApi.restoreVersion(vars.id, vars.version),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["files"] });
      qc.invalidateQueries({ queryKey: qk.file(vars.id) });
      qc.invalidateQueries({ queryKey: qk.versions(vars.id) });
    },
  });
}

export function usePermanentDelete() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => filesApi.permanentDelete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["files"] });
    },
  });
}
