import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Download, FileWarning } from "lucide-react";
import { shareApi } from "@/api/share";
import { ApiClientError } from "@/api/client";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { formatBytes } from "@/lib/utils";
import type { PublicSharedFile } from "@/types/api";

// 비인증 공개 화면. 계약 v1.6: GET /api/public/share/{token} → 파일 메타 +
// downloadUrl(게이트웨이 다운로드 경로 "/api/public/share/{token}/download", 상대경로).
// 공개 다운로드는 비인증이라 단순 링크/네비게이션으로 충분(매 요청 백엔드가 재검증).
// 에러 404 INVALID_LINK / 410 EXPIRED / 410 DISABLED.
export default function PublicSharePage() {
  const { token } = useParams<{ token: string }>();
  const [file, setFile] = useState<PublicSharedFile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    let active = true;
    setLoading(true);
    shareApi
      .publicGet(token)
      .then((res) => {
        if (active) setFile(res);
      })
      .catch((err) => {
        if (!active) return;
        setError(
          err instanceof ApiClientError
            ? err.displayMessage
            : "공유 링크에 접근할 수 없습니다."
        );
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [token]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>공유된 파일</CardTitle>
          <CardDescription>Mini Drive 공유 링크</CardDescription>
        </CardHeader>
        <CardContent>
          {loading && (
            <p className="text-sm text-muted-foreground">불러오는 중...</p>
          )}
          {error && (
            <div className="flex flex-col items-center gap-2 py-6 text-center">
              <FileWarning className="h-8 w-8 text-destructive" />
              <p className="text-sm text-destructive">{error}</p>
            </div>
          )}
          {file && (
            <div className="space-y-4">
              <div>
                <p className="font-medium">{file.originalName}</p>
                <p className="text-sm text-muted-foreground">
                  {formatBytes(file.fileSize)}
                </p>
              </div>
              <Button asChild className="w-full">
                {/* 계약 v1.6: 게이트웨이 상대경로. Content-Disposition: attachment 로
                    브라우저가 다운로드 처리. 비인증이라 헤더 불요 — 단순 링크면 충분. */}
                <a href={file.downloadUrl} download>
                  <Download /> 다운로드
                </a>
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
