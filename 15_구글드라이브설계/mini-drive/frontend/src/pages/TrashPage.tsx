import { useState } from "react";
import { Trash2, RotateCcw, File as FileIcon } from "lucide-react";
import {
  useFiles,
  usePermanentDelete,
  useRestoreFromTrash,
} from "@/hooks/useFiles";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { formatBytes, formatDate } from "@/lib/utils";

export default function TrashPage() {
  const { toast } = useToast();
  const [page, setPage] = useState(0);
  const { data, isLoading } = useFiles({ status: "DELETED", page, size: 20 });
  const restore = useRestoreFromTrash();
  const permanent = usePermanentDelete();

  const files = data?.content ?? [];

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <div className="flex items-center gap-2">
        <Trash2 className="h-5 w-5" />
        <h1 className="text-lg font-semibold">휴지통</h1>
      </div>

      {isLoading && (
        <p className="py-4 text-center text-sm text-muted-foreground">
          불러오는 중...
        </p>
      )}
      {!isLoading && files.length === 0 && (
        <p className="py-8 text-center text-sm text-muted-foreground">
          휴지통이 비어 있습니다.
        </p>
      )}

      <div className="space-y-1">
        {files.map((file) => (
          <div
            key={file.id}
            className="flex items-center justify-between rounded-md border px-3 py-2"
          >
            <div className="flex min-w-0 flex-1 items-center gap-2">
              <FileIcon className="h-4 w-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0">
                <p className="truncate text-sm">{file.originalName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(file.fileSize)} · {formatDate(file.updatedAt)}
                </p>
              </div>
            </div>
            <div className="flex gap-1">
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  restore.mutate(file.id, {
                    onSuccess: () => toast({ title: "복구했습니다." }),
                    onError: () =>
                      toast({ title: "복구 실패", variant: "destructive" }),
                  })
                }
              >
                <RotateCcw className="h-4 w-4" /> 복구
              </Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={() =>
                  permanent.mutate(file.id, {
                    onSuccess: () => toast({ title: "영구 삭제했습니다." }),
                    onError: () =>
                      toast({ title: "삭제 실패", variant: "destructive" }),
                  })
                }
              >
                <Trash2 className="h-4 w-4" /> 영구 삭제
              </Button>
            </div>
          </div>
        ))}
      </div>

      {data && data.totalElements > data.size && (
        <div className="flex items-center justify-center gap-2 pt-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            이전
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} /{" "}
            {Math.max(1, Math.ceil(data.totalElements / data.size))}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={(page + 1) * data.size >= data.totalElements}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}
    </div>
  );
}
