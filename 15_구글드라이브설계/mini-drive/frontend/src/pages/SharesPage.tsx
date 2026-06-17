import { useMemo, useState } from "react";
import { Link2, Ban, Search } from "lucide-react";
import { useShares, useDisableShare } from "@/hooks/useShare";
import { ApiClientError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/components/ui/toast";
import { CopyLinkButton } from "@/components/share/CopyLinkButton";
import {
  cn,
  formatDate,
  shareUrl,
  computeShareStatus,
  type ShareStatus,
} from "@/lib/utils";
import type { ShareListItem } from "@/types/api";

const STATUS_META: Record<
  ShareStatus,
  { label: string; className: string }
> = {
  active: {
    label: "활성",
    className: "bg-green-100 text-green-700",
  },
  expired: {
    label: "만료",
    className: "bg-amber-100 text-amber-700",
  },
  disabled: {
    label: "비활성",
    className: "bg-muted text-muted-foreground",
  },
};

// 상태 필터: "all"(전체) + ShareStatus 3종.
type StatusFilter = "all" | ShareStatus;

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "active", label: "활성" },
  { value: "expired", label: "만료" },
  { value: "disabled", label: "비활성" },
];

function StatusBadge({ status }: { status: ShareStatus }) {
  const meta = STATUS_META[status];
  return (
    <span
      className={cn(
        "inline-flex rounded-full px-2 py-0.5 text-xs font-medium",
        meta.className
      )}
    >
      {meta.label}
    </span>
  );
}

export default function SharesPage() {
  const { toast } = useToast();
  const { data, isLoading, isError, error, refetch } = useShares();
  const disableShare = useDisableShare();

  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [nameQuery, setNameQuery] = useState("");

  const shares = data ?? [];

  // 클라이언트 측 필터: 이미 받은 목록만 사용(서버 재요청 없음).
  // 상태 필터(computeShareStatus)와 파일명 부분일치(대소문자 무시)를 AND 결합.
  const filteredShares = useMemo(() => {
    const q = nameQuery.trim().toLowerCase();
    return shares.filter((s) => {
      const statusOk =
        statusFilter === "all" ||
        computeShareStatus(s.isActive, s.expiredAt) === statusFilter;
      const nameOk = q === "" || s.fileName.toLowerCase().includes(q);
      return statusOk && nameOk;
    });
  }, [shares, statusFilter, nameQuery]);

  const onDisable = (s: ShareListItem) => {
    disableShare.mutate(s.id, {
      onSuccess: () => toast({ title: "공유 링크를 비활성화했습니다." }),
      onError: (err) =>
        toast({
          title:
            err instanceof ApiClientError
              ? err.displayMessage
              : "비활성화 실패",
          variant: "destructive",
        }),
    });
  };

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <div className="flex items-center gap-2">
        <Link2 className="h-5 w-5" />
        <h1 className="text-lg font-semibold">공유 링크</h1>
      </div>

      {isLoading && (
        <p className="py-8 text-center text-sm text-muted-foreground">
          불러오는 중...
        </p>
      )}

      {isError && (
        <div className="space-y-2 rounded-md border border-destructive/40 bg-destructive/5 p-4 text-center">
          <p className="text-sm text-destructive">
            {error instanceof ApiClientError
              ? error.displayMessage
              : "공유 목록을 불러오지 못했습니다."}
          </p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            다시 시도
          </Button>
        </div>
      )}

      {!isLoading && !isError && shares.length === 0 && (
        <p className="py-12 text-center text-sm text-muted-foreground">
          생성한 공유 링크가 없습니다.
        </p>
      )}

      {!isLoading && !isError && shares.length > 0 && (
        <>
          {/* 필터 바: 모바일 폭에서 줄바꿈(flex-wrap), 칩 영역은 가로 스크롤 허용. */}
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
            <div className="flex min-w-0 flex-wrap gap-1.5">
              {STATUS_FILTERS.map((f) => (
                <button
                  key={f.value}
                  type="button"
                  onClick={() => setStatusFilter(f.value)}
                  className={cn(
                    "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                    statusFilter === f.value
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-input bg-transparent text-muted-foreground hover:bg-muted"
                  )}
                >
                  {f.label}
                </button>
              ))}
            </div>
            <div className="relative w-full sm:w-64">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                type="search"
                value={nameQuery}
                onChange={(e) => setNameQuery(e.target.value)}
                placeholder="파일명 검색"
                aria-label="파일명 검색"
                className="pl-8"
              />
            </div>
            <span className="ml-auto whitespace-nowrap text-xs text-muted-foreground">
              {filteredShares.length}개
            </span>
          </div>

          {filteredShares.length === 0 ? (
            <p className="py-12 text-center text-sm text-muted-foreground">
              조건에 맞는 공유 링크가 없습니다.
            </p>
          ) : (
            <div className="overflow-x-auto rounded-md border">
          <table className="w-full min-w-[640px] text-sm">
            <thead className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">파일명</th>
                <th className="px-3 py-2 font-medium">공유 URL</th>
                <th className="px-3 py-2 font-medium">상태</th>
                <th className="px-3 py-2 font-medium">만료</th>
                <th className="px-3 py-2 font-medium">생성일</th>
                <th className="px-3 py-2 font-medium text-right">동작</th>
              </tr>
            </thead>
            <tbody>
              {filteredShares.map((s) => {
                const status = computeShareStatus(s.isActive, s.expiredAt);
                const absUrl = shareUrl(s.url ?? `/share/${s.token}`);
                return (
                  <tr key={s.id} className="border-b last:border-0">
                    <td className="max-w-[180px] px-3 py-2">
                      <span className="block truncate" title={s.fileName}>
                        {s.fileName}
                      </span>
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-2">
                        <a
                          href={absUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="block max-w-[240px] truncate text-primary underline-offset-4 hover:underline"
                          title={absUrl}
                        >
                          {absUrl}
                        </a>
                        <CopyLinkButton url={absUrl} />
                      </div>
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge status={status} />
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                      {s.expiredAt ? formatDate(s.expiredAt) : "무기한"}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                      {formatDate(s.createdAt)}
                    </td>
                    <td className="px-3 py-2 text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!s.isActive || disableShare.isPending}
                        onClick={() => onDisable(s)}
                      >
                        <Ban className="h-4 w-4" /> 비활성화
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
