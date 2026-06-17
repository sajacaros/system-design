import { useEffect, useRef, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useUploadVersion } from "@/hooks/useUploadVersion";
import { UploadCloud } from "lucide-react";

interface Props {
  fileId: number | null;
  fileName?: string;
  /** 현재 파일 version — baseVersion 으로 사용 */
  baseVersion: number | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// 탐색기/버전 패널에서 호출하는 "새 버전 업로드" 다이얼로그.
// POST /api/files/{id}/content (baseVersion). 409 시 덮어쓰기 확인 후 overwrite=true 재요청.
export function UploadVersionDialog({
  fileId,
  fileName,
  baseVersion,
  open,
  onOpenChange,
}: Props) {
  const { toast } = useToast();
  const { status, progress, error, run, cancel, reset } = useUploadVersion();
  const [selected, setSelected] = useState<File | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // 다이얼로그가 닫히면 상태 초기화.
  useEffect(() => {
    if (!open) {
      setSelected(null);
      reset();
    }
  }, [open, reset]);

  const startUpload = async (file: File, overwrite: boolean) => {
    if (fileId == null || baseVersion == null) return;
    try {
      const ok = await run({ fileId, file, baseVersion, overwrite });
      if (ok) {
        toast({
          title: "새 버전 업로드 완료",
          description: fileName ? `${fileName} 가 갱신되었습니다.` : undefined,
        });
        onOpenChange(false);
      }
      // ok=false 이고 status=conflict 면 충돌 확인 UI 노출(아래 렌더).
    } catch {
      toast({ title: "새 버전 업로드 실패", variant: "destructive" });
    }
  };

  const onPick = (file: File | null) => {
    setSelected(file);
    if (file) void startUpload(file, false);
  };

  const onOverwrite = () => {
    if (selected) void startUpload(selected, true);
  };

  const busy = status === "uploading";

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (busy) cancel();
        onOpenChange(o);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UploadCloud className="h-4 w-4" /> 새 버전 업로드
          </DialogTitle>
          <DialogDescription>
            {fileName}
            {baseVersion != null ? ` (현재 v${baseVersion})` : ""}
          </DialogDescription>
        </DialogHeader>

        <input
          ref={inputRef}
          type="file"
          className="hidden"
          onChange={(e) => onPick(e.target.files?.[0] ?? null)}
        />

        {status === "idle" && (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">
              선택한 파일이 이 파일의 다음 버전(v
              {baseVersion != null ? baseVersion + 1 : "?"})으로 저장됩니다.
            </p>
            <Button
              variant="outline"
              className="w-full"
              onClick={() => inputRef.current?.click()}
            >
              <UploadCloud className="h-4 w-4" /> 파일 선택
            </Button>
          </div>
        )}

        {busy && (
          <div className="space-y-2">
            <p className="truncate text-sm">{selected?.name}</p>
            <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
              <div
                className="h-full bg-primary transition-all"
                style={{ width: `${progress}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground">{progress}%</p>
          </div>
        )}

        {status === "conflict" && (
          <div className="space-y-2">
            <p className="text-sm">
              다른 곳에서 이미 새 버전이 저장됐습니다. 덮어쓸까요?
            </p>
            <p className="text-xs text-muted-foreground">
              덮어쓰면 현재 최신 버전 위에 선택한 파일이 새 버전으로 저장됩니다.
            </p>
          </div>
        )}

        {status === "failed" && (
          <p className="text-sm text-destructive">{error}</p>
        )}

        <DialogFooter>
          {status === "conflict" && (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                취소
              </Button>
              <Button variant="destructive" onClick={onOverwrite}>
                덮어쓰기
              </Button>
            </>
          )}
          {status === "failed" && selected && (
            <Button onClick={() => void startUpload(selected, false)}>
              다시 시도
            </Button>
          )}
          {busy && (
            <Button variant="outline" onClick={cancel}>
              취소
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
