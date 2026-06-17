import { useMemo, useState } from "react";
import {
  Folder,
  File as FileIcon,
  MoreVertical,
  Download,
  Pencil,
  FolderInput,
  Trash2,
  Share2,
  History,
  UploadCloud,
  ChevronRight,
  Home,
  FolderPlus,
  Search as SearchIcon,
} from "lucide-react";
import {
  useDeleteFolder,
  useFolders,
  useCreateFolder,
  useUpdateFolder,
} from "@/hooks/useFolders";
import {
  useFiles,
  useUpdateFile,
  useDeleteFile,
} from "@/hooks/useFiles";
import { filesApi } from "@/api/files";
import { downloadAuthenticated } from "@/lib/download";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/components/ui/toast";
import { formatBytes, formatDate } from "@/lib/utils";
import { UploadPanel } from "@/components/explorer/UploadPanel";
import { RenameDialog } from "@/components/explorer/RenameDialog";
import { MoveDialog } from "@/components/explorer/MoveDialog";
import { ShareDialog } from "@/components/explorer/ShareDialog";
import { VersionsDialog } from "@/components/explorer/VersionsDialog";
import { UploadVersionDialog } from "@/components/explorer/UploadVersionDialog";
import type { FileListItem } from "@/types/api";

interface Crumb {
  id: number | null;
  name: string;
}

type SortMode = "default" | "recent";

export default function ExplorerPage() {
  const { toast } = useToast();
  const [trail, setTrail] = useState<Crumb[]>([
    { id: null, name: "내 드라이브" },
  ]);
  const currentFolderId = trail[trail.length - 1].id;

  // 검색
  const [searchName, setSearchName] = useState("");
  const [searchExt, setSearchExt] = useState("");
  const [sort, setSort] = useState<SortMode>("default");
  const [page, setPage] = useState(0);

  const fileParams = useMemo(
    () => ({
      folderId: searchName || searchExt ? undefined : currentFolderId,
      status: "UPLOADED" as const,
      name: searchName || undefined,
      extension: searchExt || undefined,
      sort: sort === "recent" ? ("recent" as const) : undefined,
      page,
      size: 20,
    }),
    [currentFolderId, searchName, searchExt, sort, page]
  );

  const { data: folders } = useFolders(currentFolderId);
  const { data: filePage, isLoading: filesLoading } = useFiles(fileParams);

  const createFolder = useCreateFolder(currentFolderId);
  const updateFolder = useUpdateFolder();
  const deleteFolder = useDeleteFolder(currentFolderId);
  const updateFile = useUpdateFile();
  const deleteFile = useDeleteFile();

  // 다이얼로그 상태
  const [newFolderOpen, setNewFolderOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<
    | { kind: "folder" | "file"; id: number; name: string }
    | null
  >(null);
  const [moveTarget, setMoveTarget] = useState<
    { kind: "folder" | "file"; id: number } | null
  >(null);
  const [shareTarget, setShareTarget] = useState<FileListItem | null>(null);
  const [versionsTarget, setVersionsTarget] = useState<FileListItem | null>(
    null
  );
  const [uploadVersionTarget, setUploadVersionTarget] =
    useState<FileListItem | null>(null);

  const enterFolder = (id: number, name: string) => {
    setTrail((t) => [...t, { id, name }]);
    setPage(0);
  };
  const goCrumb = (index: number) => {
    setTrail((t) => t.slice(0, index + 1));
    setPage(0);
  };

  const onDownload = async (file: FileListItem) => {
    // 계약 v1.6: 파일 다운로드는 게이트웨이 경유 인증 스트리밍.
    // /api/files/{id}/download 가 매 요청 소유자+UPLOADED 검증 후 스트리밍한다.
    // Authorization 헤더가 필요하므로 fetch-blob 패턴으로 받는다(단순 <a href> 불가).
    try {
      await downloadAuthenticated(filesApi.downloadPath(file.id));
    } catch {
      toast({ title: "다운로드 실패", variant: "destructive" });
    }
  };

  const isSearching = !!(searchName || searchExt);
  const files = filePage?.content ?? [];

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      {/* 상단 도구 */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-1 text-sm">
          {trail.map((c, i) => (
            <span key={`${c.id}-${i}`} className="flex items-center gap-1">
              {i > 0 && (
                <ChevronRight className="h-3 w-3 text-muted-foreground" />
              )}
              <button
                type="button"
                className="rounded px-1 hover:bg-accent"
                onClick={() => goCrumb(i)}
              >
                {i === 0 ? <Home className="inline h-4 w-4" /> : c.name}
              </button>
            </span>
          ))}
        </div>
        <Button size="sm" variant="outline" onClick={() => setNewFolderOpen(true)}>
          <FolderPlus /> 새 폴더
        </Button>
      </div>

      {/* 검색 */}
      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <SearchIcon className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="파일명 검색"
            value={searchName}
            onChange={(e) => {
              setSearchName(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <Input
          className="sm:w-40"
          placeholder="확장자 (예: pdf)"
          value={searchExt}
          onChange={(e) => {
            setSearchExt(e.target.value);
            setPage(0);
          }}
        />
        <Button
          size="sm"
          variant={sort === "recent" ? "default" : "outline"}
          onClick={() => {
            setSort((s) => (s === "recent" ? "default" : "recent"));
            setPage(0);
          }}
        >
          최근
        </Button>
      </div>

      <UploadPanel folderId={currentFolderId} />

      {/* 폴더 목록 (검색 중에는 숨김) */}
      {!isSearching && (
        <div className="space-y-1">
          {folders?.map((f) => (
            <div
              key={`folder-${f.id}`}
              className="flex items-center justify-between rounded-md border px-3 py-2 hover:bg-accent/50"
            >
              <button
                type="button"
                className="flex flex-1 items-center gap-2 text-left"
                onClick={() => enterFolder(f.id, f.name)}
              >
                <Folder className="h-4 w-4 text-blue-500" />
                <span className="truncate text-sm">{f.name}</span>
              </button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="h-8 w-8">
                    <MoreVertical className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem
                    onSelect={() =>
                      setRenameTarget({
                        kind: "folder",
                        id: f.id,
                        name: f.name,
                      })
                    }
                  >
                    <Pencil className="h-4 w-4" /> 이름 변경
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onSelect={() =>
                      setMoveTarget({ kind: "folder", id: f.id })
                    }
                  >
                    <FolderInput className="h-4 w-4" /> 이동
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    className="text-destructive"
                    onSelect={() =>
                      deleteFolder.mutate(f.id, {
                        onSuccess: () =>
                          toast({ title: "폴더를 휴지통으로 이동했습니다." }),
                      })
                    }
                  >
                    <Trash2 className="h-4 w-4" /> 삭제
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          ))}
        </div>
      )}

      {/* 파일 목록 */}
      <div className="space-y-1">
        {filesLoading && (
          <p className="py-4 text-center text-sm text-muted-foreground">
            불러오는 중...
          </p>
        )}
        {!filesLoading && files.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            파일이 없습니다.
          </p>
        )}
        {files.map((file) => (
          <div
            key={`file-${file.id}`}
            className="flex items-center justify-between rounded-md border px-3 py-2 hover:bg-accent/50"
          >
            <div className="flex min-w-0 flex-1 items-center gap-2">
              <FileIcon className="h-4 w-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0">
                <p className="truncate text-sm">{file.originalName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(file.fileSize)} · v{file.version} ·{" "}
                  {formatDate(file.updatedAt)}
                </p>
              </div>
            </div>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-8 w-8">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => void onDownload(file)}>
                  <Download className="h-4 w-4" /> 다운로드
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => setUploadVersionTarget(file)}
                >
                  <UploadCloud className="h-4 w-4" /> 새 버전 업로드
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => setVersionsTarget(file)}>
                  <History className="h-4 w-4" /> 버전 기록
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => setShareTarget(file)}>
                  <Share2 className="h-4 w-4" /> 공유
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onSelect={() =>
                    setRenameTarget({
                      kind: "file",
                      id: file.id,
                      name: file.originalName,
                    })
                  }
                >
                  <Pencil className="h-4 w-4" /> 이름 변경
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => setMoveTarget({ kind: "file", id: file.id })}
                >
                  <FolderInput className="h-4 w-4" /> 이동
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  className="text-destructive"
                  onSelect={() =>
                    deleteFile.mutate(file.id, {
                      onSuccess: () =>
                        toast({ title: "파일을 휴지통으로 이동했습니다." }),
                    })
                  }
                >
                  <Trash2 className="h-4 w-4" /> 삭제
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        ))}
      </div>

      {/* 페이지네이션 */}
      {filePage && filePage.totalElements > filePage.size && (
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
            {Math.max(1, Math.ceil(filePage.totalElements / filePage.size))}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={(page + 1) * filePage.size >= filePage.totalElements}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}

      {/* 다이얼로그들 */}
      <RenameDialog
        open={newFolderOpen}
        initialName=""
        title="새 폴더"
        pending={createFolder.isPending}
        onOpenChange={setNewFolderOpen}
        onSubmit={(name) =>
          createFolder.mutate(
            { parentId: currentFolderId, name },
            {
              onSuccess: () => setNewFolderOpen(false),
              onError: () =>
                toast({ title: "폴더 생성 실패", variant: "destructive" }),
            }
          )
        }
      />

      <RenameDialog
        open={!!renameTarget}
        initialName={renameTarget?.name ?? ""}
        pending={updateFolder.isPending || updateFile.isPending}
        onOpenChange={(o) => !o && setRenameTarget(null)}
        onSubmit={(name) => {
          if (!renameTarget) return;
          const onDone = {
            onSuccess: () => setRenameTarget(null),
            onError: () =>
              toast({ title: "이름 변경 실패", variant: "destructive" }),
          };
          if (renameTarget.kind === "folder") {
            updateFolder.mutate(
              { id: renameTarget.id, req: { name } },
              onDone
            );
          } else {
            updateFile.mutate(
              { id: renameTarget.id, req: { originalName: name } },
              onDone
            );
          }
        }}
      />

      <MoveDialog
        open={!!moveTarget}
        pending={updateFolder.isPending || updateFile.isPending}
        onOpenChange={(o) => !o && setMoveTarget(null)}
        onSubmit={(targetFolderId) => {
          if (!moveTarget) return;
          const onDone = {
            onSuccess: () => {
              setMoveTarget(null);
              toast({ title: "이동했습니다." });
            },
            onError: () =>
              toast({ title: "이동 실패", variant: "destructive" }),
          };
          if (moveTarget.kind === "folder") {
            updateFolder.mutate(
              { id: moveTarget.id, req: { parentId: targetFolderId } },
              onDone
            );
          } else {
            updateFile.mutate(
              { id: moveTarget.id, req: { folderId: targetFolderId } },
              onDone
            );
          }
        }}
      />

      <ShareDialog
        fileId={shareTarget?.id ?? null}
        fileName={shareTarget?.originalName}
        open={!!shareTarget}
        onOpenChange={(o) => !o && setShareTarget(null)}
      />

      <VersionsDialog
        fileId={versionsTarget?.id ?? null}
        fileName={versionsTarget?.originalName}
        currentVersion={versionsTarget?.version}
        open={!!versionsTarget}
        onOpenChange={(o) => !o && setVersionsTarget(null)}
        onUploadVersion={() => {
          if (versionsTarget) {
            setUploadVersionTarget(versionsTarget);
            setVersionsTarget(null);
          }
        }}
      />

      <UploadVersionDialog
        fileId={uploadVersionTarget?.id ?? null}
        fileName={uploadVersionTarget?.originalName}
        baseVersion={uploadVersionTarget?.version ?? null}
        open={!!uploadVersionTarget}
        onOpenChange={(o) => !o && setUploadVersionTarget(null)}
      />
    </div>
  );
}
