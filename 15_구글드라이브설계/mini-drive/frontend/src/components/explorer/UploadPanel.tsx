import { useRef } from "react";
import { Upload, RotateCw, X, CheckCircle2, AlertCircle } from "lucide-react";
import { useUpload } from "@/hooks/useUpload";
import { Button } from "@/components/ui/button";
import { formatBytes } from "@/lib/utils";

export function UploadPanel({ folderId }: { folderId: number | null }) {
  const { tasks, enqueue, retry, cancel, clearFinished } = useUpload(folderId);
  const inputRef = useRef<HTMLInputElement>(null);

  const active = tasks.filter(
    (t) => t.status === "uploading" || t.status === "queued"
  );

  return (
    <div className="rounded-lg border p-3">
      <div className="flex items-center justify-between gap-2">
        <input
          ref={inputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(e) => {
            if (e.target.files) enqueue(e.target.files);
            e.target.value = "";
          }}
        />
        <Button onClick={() => inputRef.current?.click()} size="sm">
          <Upload /> 파일 업로드
        </Button>
        {tasks.some((t) => t.status === "done" || t.status === "failed") && (
          <Button variant="ghost" size="sm" onClick={clearFinished}>
            완료 항목 지우기
          </Button>
        )}
      </div>

      {tasks.length > 0 && (
        <ul className="mt-3 space-y-2">
          {tasks.map((t) => (
            <li key={t.id} className="text-sm">
              <div className="flex items-center justify-between gap-2">
                <span className="truncate">{t.file.name}</span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {formatBytes(t.file.size)}
                </span>
              </div>
              <div className="mt-1 flex items-center gap-2">
                <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                  <div
                    className={`h-full transition-all ${
                      t.status === "failed"
                        ? "bg-destructive"
                        : t.status === "done"
                          ? "bg-green-500"
                          : "bg-primary"
                    }`}
                    style={{ width: `${t.progress}%` }}
                  />
                </div>
                {t.status === "uploading" && (
                  <>
                    <span className="w-9 text-right text-xs">{t.progress}%</span>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
                      onClick={() => cancel(t.id)}
                    >
                      <X className="h-3.5 w-3.5" />
                    </Button>
                  </>
                )}
                {t.status === "done" && (
                  <CheckCircle2 className="h-4 w-4 text-green-500" />
                )}
                {(t.status === "failed" || t.status === "canceled") && (
                  <>
                    <AlertCircle className="h-4 w-4 text-destructive" />
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
                      onClick={() => retry(t.id)}
                      title="재시도"
                    >
                      <RotateCw className="h-3.5 w-3.5" />
                    </Button>
                  </>
                )}
              </div>
              {t.error && (
                <p className="mt-0.5 text-xs text-destructive">{t.error}</p>
              )}
            </li>
          ))}
        </ul>
      )}
      {active.length > 0 && (
        <p className="mt-2 text-xs text-muted-foreground">
          {active.length}개 업로드 중...
        </p>
      )}
    </div>
  );
}
