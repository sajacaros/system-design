import { useState } from "react";
import { Copy, Check } from "lucide-react";
import { useCreateShare, useDisableShare } from "@/hooks/useShare";
import { ApiClientError } from "@/api/client";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";
import { shareUrl } from "@/lib/utils";
import type { ShareLink } from "@/types/api";

interface Props {
  fileId: number | null;
  fileName?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ShareDialog({ fileId, fileName, open, onOpenChange }: Props) {
  const createShare = useCreateShare();
  const disableShare = useDisableShare();
  const { toast } = useToast();
  const [expiredAt, setExpiredAt] = useState("");
  const [link, setLink] = useState<ShareLink | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setLink(null);
    setExpiredAt("");
    setError(null);
    setCopied(false);
  };

  const onCreate = () => {
    if (fileId == null) return;
    setError(null);
    // datetime-local → ISO-8601 UTC. 비우면 무기한(null).
    const iso = expiredAt ? new Date(expiredAt).toISOString() : null;
    createShare.mutate(
      { fileId, req: { expiredAt: iso } },
      {
        onSuccess: (res) => setLink(res),
        onError: (err) =>
          setError(
            err instanceof ApiClientError ? err.displayMessage : "공유 생성 실패"
          ),
      }
    );
  };

  const onCopy = async () => {
    if (!link) return;
    const url = shareUrl(link.url ?? `/share/${link.token}`);
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast({ title: "복사 실패", variant: "destructive" });
    }
  };

  const onDisable = () => {
    if (!link) return;
    disableShare.mutate(link.id, {
      onSuccess: () => {
        toast({ title: "공유 링크를 비활성화했습니다." });
        setLink({ ...link, isActive: false });
      },
      onError: (err) =>
        setError(
          err instanceof ApiClientError ? err.displayMessage : "비활성화 실패"
        ),
    });
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) reset();
        onOpenChange(o);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>공유 링크</DialogTitle>
          <DialogDescription>
            {fileName ?? "파일"}의 읽기 전용 공유 링크를 만듭니다.
          </DialogDescription>
        </DialogHeader>

        {!link ? (
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="expiredAt">만료 시각 (선택)</Label>
              <Input
                id="expiredAt"
                type="datetime-local"
                value={expiredAt}
                onChange={(e) => setExpiredAt(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">
                비우면 무기한 링크가 생성됩니다.
              </p>
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Input
                readOnly
                value={shareUrl(link.url ?? `/share/${link.token}`)}
              />
              <Button variant="outline" size="icon" onClick={onCopy}>
                {copied ? <Check /> : <Copy />}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              상태: {link.isActive ? "활성" : "비활성"} · 만료:{" "}
              {link.expiredAt ?? "무기한"}
            </p>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
        )}

        <DialogFooter>
          {!link ? (
            <Button onClick={onCreate} disabled={createShare.isPending}>
              {createShare.isPending ? "생성 중..." : "링크 생성"}
            </Button>
          ) : (
            link.isActive && (
              <Button
                variant="destructive"
                onClick={onDisable}
                disabled={disableShare.isPending}
              >
                링크 비활성화
              </Button>
            )
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
