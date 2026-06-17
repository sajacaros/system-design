import { useState } from "react";
import { Copy, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";

interface Props {
  /** 절대 공유 URL (shareUrl 로 이미 origin 이 조합된 값). */
  url: string;
  size?: "sm" | "icon";
  label?: string;
}

// 공유 URL 복사 버튼. 클립보드 복사 성공 시 잠시 체크 아이콘으로 전환.
export function CopyLinkButton({ url, size = "icon", label }: Props) {
  const { toast } = useToast();
  const [copied, setCopied] = useState(false);

  const onCopy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast({ title: "복사 실패", variant: "destructive" });
    }
  };

  if (size === "icon") {
    return (
      <Button variant="outline" size="icon" onClick={onCopy} title="링크 복사">
        {copied ? <Check /> : <Copy />}
      </Button>
    );
  }

  return (
    <Button variant="outline" size="sm" onClick={onCopy}>
      {copied ? <Check /> : <Copy />}
      {label ?? (copied ? "복사됨" : "복사")}
    </Button>
  );
}
