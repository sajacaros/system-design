import { Bell } from "lucide-react";
import {
  useMarkNotificationRead,
  useNotifications,
} from "@/hooks/useNotifications";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { formatDate } from "@/lib/utils";

export function NotificationBell() {
  const { data } = useNotifications(true);
  const markRead = useMarkNotificationRead();
  const unreadCount = data?.length ?? 0;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative">
          <Bell />
          {unreadCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-bold text-destructive-foreground">
              {unreadCount > 99 ? "99+" : unreadCount}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <div className="px-2 py-1.5 text-sm font-semibold">알림</div>
        <DropdownMenuSeparator />
        {(!data || data.length === 0) && (
          <div className="px-2 py-4 text-center text-sm text-muted-foreground">
            새 알림이 없습니다.
          </div>
        )}
        {data?.map((n) => (
          <DropdownMenuItem
            key={n.id}
            className="flex flex-col items-start gap-0.5"
            onSelect={(e) => {
              e.preventDefault();
              markRead.mutate(n.id);
            }}
          >
            <span className="text-sm">{n.message}</span>
            <span className="text-[11px] text-muted-foreground">
              {n.type} · {formatDate(n.createdAt)}
            </span>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
