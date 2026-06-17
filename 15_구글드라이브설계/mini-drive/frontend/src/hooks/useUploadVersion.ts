import { useCallback, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { filesApi } from "@/api/files";
import { qk } from "./queryKeys";

export type VersionUploadStatus =
  | "idle"
  | "uploading"
  | "conflict"
  | "done"
  | "failed";

interface RunArgs {
  fileId: number;
  file: File;
  baseVersion: number;
  overwrite: boolean;
}

// 새 버전 업로드 — POST /api/files/{id}/content (multipart).
// 진행률(XHR) + 409 CONFLICT 처리(덮어쓰기 재요청) + 캐시 무효화.
export function useUploadVersion() {
  const qc = useQueryClient();
  const [status, setStatus] = useState<VersionUploadStatus>("idle");
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | undefined>(undefined);
  const controllerRef = useRef<AbortController | null>(null);

  const reset = useCallback(() => {
    setStatus("idle");
    setProgress(0);
    setError(undefined);
  }, []);

  // 성공 시 resolve(true), 409 충돌 시 resolve(false), 그 외 오류는 throw.
  const run = useCallback(
    async (args: RunArgs): Promise<boolean> => {
      const { fileId, file, baseVersion, overwrite } = args;
      const controller = new AbortController();
      controllerRef.current = controller;
      setStatus("uploading");
      setProgress(0);
      setError(undefined);
      try {
        await filesApi.uploadVersion(
          fileId,
          file,
          baseVersion,
          overwrite,
          (pct) => setProgress(pct),
          controller.signal
        );
        setStatus("done");
        setProgress(100);
        // 해당 폴더 파일 목록 + 파일 메타 + 버전 히스토리 무효화.
        qc.invalidateQueries({ queryKey: ["files"] });
        qc.invalidateQueries({ queryKey: qk.file(fileId) });
        qc.invalidateQueries({ queryKey: qk.versions(fileId) });
        return true;
      } catch (e: unknown) {
        const err = e as { name?: string; status?: number; code?: string; message?: string };
        if (err?.name === "AbortError" || err?.code === "ABORTED") {
          setStatus("idle");
          return false;
        }
        // 계약: baseVersion != 현재 version 이면 409 CONFLICT.
        if (err?.status === 409 || err?.code === "CONFLICT") {
          setStatus("conflict");
          return false;
        }
        setStatus("failed");
        setError(err?.message ?? "새 버전 업로드 실패");
        throw e;
      } finally {
        controllerRef.current = null;
      }
    },
    [qc]
  );

  const cancel = useCallback(() => {
    controllerRef.current?.abort();
  }, []);

  return { status, progress, error, run, cancel, reset };
}
