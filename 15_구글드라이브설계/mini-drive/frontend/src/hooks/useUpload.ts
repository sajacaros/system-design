import { useCallback, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { filesApi } from "@/api/files";

export type UploadStatus =
  | "queued"
  | "uploading"
  | "done"
  | "failed"
  | "canceled";

export interface UploadTask {
  id: string;
  file: File;
  progress: number;
  status: UploadStatus;
  error?: string;
}

// 업로드 진행률 + 실패 재시도. 계약: POST /api/files/upload (multipart).
export function useUpload(folderId: number | null) {
  const qc = useQueryClient();
  const [tasks, setTasks] = useState<UploadTask[]>([]);
  const controllers = useRef<Record<string, AbortController>>({});

  const patch = useCallback((id: string, p: Partial<UploadTask>) => {
    setTasks((prev) => prev.map((t) => (t.id === id ? { ...t, ...p } : t)));
  }, []);

  const run = useCallback(
    async (task: UploadTask) => {
      const controller = new AbortController();
      controllers.current[task.id] = controller;
      patch(task.id, { status: "uploading", progress: 0, error: undefined });
      try {
        await filesApi.upload(
          task.file,
          folderId,
          (pct) => patch(task.id, { progress: pct }),
          controller.signal
        );
        patch(task.id, { status: "done", progress: 100 });
        qc.invalidateQueries({ queryKey: ["files"] });
      } catch (e: unknown) {
        const err = e as { name?: string; code?: string; message?: string };
        if (err?.name === "AbortError" || err?.code === "ABORTED") {
          patch(task.id, { status: "canceled" });
        } else {
          patch(task.id, {
            status: "failed",
            error: err?.message ?? "업로드 실패",
          });
        }
      } finally {
        delete controllers.current[task.id];
      }
    },
    [folderId, patch, qc]
  );

  const enqueue = useCallback(
    (files: FileList | File[]) => {
      const list = Array.from(files);
      const newTasks: UploadTask[] = list.map((file) => ({
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        file,
        progress: 0,
        status: "queued",
      }));
      setTasks((prev) => [...prev, ...newTasks]);
      newTasks.forEach((t) => void run(t));
    },
    [run]
  );

  const retry = useCallback(
    (id: string) => {
      const task = tasks.find((t) => t.id === id);
      if (task) void run(task);
    },
    [tasks, run]
  );

  const cancel = useCallback((id: string) => {
    controllers.current[id]?.abort();
  }, []);

  const clearFinished = useCallback(() => {
    setTasks((prev) =>
      prev.filter((t) => t.status === "uploading" || t.status === "queued")
    );
  }, []);

  return { tasks, enqueue, retry, cancel, clearFinished };
}
