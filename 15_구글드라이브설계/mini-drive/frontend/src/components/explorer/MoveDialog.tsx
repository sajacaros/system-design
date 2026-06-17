import { useState } from "react";
import { useFolders } from "@/hooks/useFolders";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Folder, ChevronRight, Home } from "lucide-react";

interface Props {
  open: boolean;
  pending?: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (targetFolderId: number | null) => void;
}

// 폴더 트리를 탐색해 이동 대상 폴더를 고른다. 계약: parentId 기반 하위 폴더 조회.
export function MoveDialog({ open, pending, onOpenChange, onSubmit }: Props) {
  const [current, setCurrent] = useState<number | null>(null);
  const [trail, setTrail] = useState<{ id: number | null; name: string }[]>([
    { id: null, name: "내 드라이브" },
  ]);
  const { data: folders } = useFolders(current);

  const navInto = (id: number, name: string) => {
    setCurrent(id);
    setTrail((t) => [...t, { id, name }]);
  };
  const navTo = (index: number) => {
    const target = trail[index];
    setCurrent(target.id);
    setTrail((t) => t.slice(0, index + 1));
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>이동</DialogTitle>
          <DialogDescription>이동할 폴더를 선택하세요.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-wrap items-center gap-1 text-sm">
          {trail.map((t, i) => (
            <span key={`${t.id}-${i}`} className="flex items-center gap-1">
              {i > 0 && <ChevronRight className="h-3 w-3 text-muted-foreground" />}
              <button
                className="hover:underline"
                onClick={() => navTo(i)}
                type="button"
              >
                {i === 0 ? <Home className="inline h-3.5 w-3.5" /> : t.name}
              </button>
            </span>
          ))}
        </div>
        <ul className="max-h-60 space-y-1 overflow-y-auto rounded-md border p-1">
          {folders && folders.length === 0 && (
            <li className="px-2 py-3 text-center text-sm text-muted-foreground">
              하위 폴더 없음
            </li>
          )}
          {folders?.map((f) => (
            <li key={f.id}>
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent"
                onClick={() => navInto(f.id, f.name)}
              >
                <Folder className="h-4 w-4 text-muted-foreground" />
                {f.name}
              </button>
            </li>
          ))}
        </ul>
        <DialogFooter>
          <Button onClick={() => onSubmit(current)} disabled={pending}>
            {pending ? "이동 중..." : "여기로 이동"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
