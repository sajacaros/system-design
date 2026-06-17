import { Link, Outlet, useNavigate } from "react-router-dom";
import { HardDrive, LogOut } from "lucide-react";
import { useAuth, useLogout } from "@/hooks/useAuth";
import { useRealtime } from "@/realtime/useRealtime";
import { Button } from "@/components/ui/button";
import { NotificationBell } from "./NotificationBell";
import { Nav } from "./Nav";

export function AppLayout() {
  const { user } = useAuth();
  const logout = useLogout();
  const navigate = useNavigate();

  // 전역 실시간 구독 활성화.
  useRealtime();

  const onLogout = () => {
    logout.mutate(undefined, {
      onSettled: () => navigate("/login"),
    });
  };

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b bg-background px-4">
        <Link to="/" className="flex items-center gap-2 font-semibold">
          <HardDrive className="h-5 w-5" />
          <span>Mini Drive</span>
        </Link>
        <div className="flex items-center gap-2">
          <NotificationBell />
          <span className="hidden text-sm text-muted-foreground sm:inline">
            {user?.nickname ?? user?.email}
          </span>
          <Button variant="ghost" size="icon" onClick={onLogout} title="로그아웃">
            <LogOut />
          </Button>
        </div>
      </header>
      <main className="flex-1 p-4">
        <div className="mx-auto max-w-5xl">
          <Nav />
        </div>
        <Outlet />
      </main>
    </div>
  );
}
