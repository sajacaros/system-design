import { NavLink } from "react-router-dom";
import { HardDrive, Trash2, Link2 } from "lucide-react";
import { cn } from "@/lib/utils";

const items = [
  { to: "/", label: "내 드라이브", icon: HardDrive, end: true },
  { to: "/shares", label: "공유 링크", icon: Link2, end: false },
  { to: "/trash", label: "휴지통", icon: Trash2, end: false },
];

export function Nav() {
  return (
    <nav className="mb-4 flex gap-2 overflow-x-auto">
      {items.map((it) => (
        <NavLink
          key={it.to}
          to={it.to}
          end={it.end}
          className={({ isActive }) =>
            cn(
              "flex items-center gap-2 rounded-md px-3 py-1.5 text-sm",
              isActive
                ? "bg-primary text-primary-foreground"
                : "hover:bg-accent"
            )
          }
        >
          <it.icon className="h-4 w-4" />
          {it.label}
        </NavLink>
      ))}
    </nav>
  );
}
