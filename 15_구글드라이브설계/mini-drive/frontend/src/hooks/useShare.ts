import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { shareApi } from "@/api/share";
import type { CreateShareRequest } from "@/types/api";
import { qk } from "./queryKeys";

export function useCreateShare() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { fileId: number; req: CreateShareRequest }) =>
      shareApi.create(vars.fileId, vars.req),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: qk.file(vars.fileId) });
      qc.invalidateQueries({ queryKey: qk.shares });
    },
  });
}

// GET /api/shares — 내 공유 링크 목록 (v1.4)
export function useShares() {
  return useQuery({
    queryKey: qk.shares,
    queryFn: () => shareApi.list(),
  });
}

export function useDisableShare() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (shareId: number) => shareApi.disable(shareId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.shares });
    },
  });
}
