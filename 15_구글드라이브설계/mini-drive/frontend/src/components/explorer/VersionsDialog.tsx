import { useFileVersions, useRestoreVersion } from "@/hooks/useFiles";
import { filesApi } from "@/api/files";
import { downloadAuthenticated } from "@/lib/download";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { formatBytes, formatDate } from "@/lib/utils";
import { Download, History, UploadCloud } from "lucide-react";

interface Props {
  fileId: number | null;
  fileName?: string;
  currentVersion?: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** "새 버전 업로드" 클릭 — 부모가 UploadVersionDialog 를 연다. */
  onUploadVersion?: () => void;
}

export function VersionsDialog({
  fileId,
  fileName,
  currentVersion,
  open,
  onOpenChange,
  onUploadVersion,
}: Props) {
  const { data, isLoading } = useFileVersions(open ? fileId : null);
  const restore = useRestoreVersion();
  const { toast } = useToast();

  const onRestore = (version: number) => {
    if (fileId == null) return;
    restore.mutate(
      { id: fileId, version },
      {
        onSuccess: (res) =>
          toast({
            title: "버전 복구 완료",
            description: `v${version} → 현재 v${res.version}`,
          }),
        onError: () =>
          toast({ title: "복구 실패", variant: "destructive" }),
      }
    );
  };

  const onDownload = (version: number) => {
    if (fileId == null) return;
    void downloadAuthenticated(filesApi.versionDownloadPath(fileId, version));
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <History className="h-4 w-4" /> 버전 히스토리
          </DialogTitle>
          <DialogDescription>
            {fileName}
            {currentVersion != null ? ` (현재 v${currentVersion})` : ""}
          </DialogDescription>
        </DialogHeader>
        {onUploadVersion && (
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={onUploadVersion}
          >
            <UploadCloud className="h-4 w-4" /> 새 버전 업로드
          </Button>
        )}
        {isLoading && <p className="text-sm text-muted-foreground">불러오는 중...</p>}
        {data && data.length === 0 && (
          <p className="text-sm text-muted-foreground">버전 기록이 없습니다.</p>
        )}
        <ul className="max-h-80 space-y-2 overflow-y-auto">
          {data?.map((v) => (
            <li
              key={v.version}
              className="flex items-center justify-between gap-2 rounded-md border p-2"
            >
              <div className="text-sm">
                <span className="font-medium">v{v.version}</span>
                <span className="ml-2 text-xs text-muted-foreground">
                  {formatBytes(v.fileSize)} · {formatDate(v.createdAt)}
                </span>
              </div>
              <div className="flex gap-1">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  onClick={() => onDownload(v.version)}
                  title="이 버전 다운로드"
                >
                  <Download className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onRestore(v.version)}
                  disabled={restore.isPending}
                >
                  복구
                </Button>
              </div>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  );
}
