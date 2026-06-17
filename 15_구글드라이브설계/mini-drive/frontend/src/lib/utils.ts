import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatBytes(bytes: number): string {
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const value = bytes / Math.pow(1024, i);
  return `${value.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  return d.toLocaleString();
}

// 공유 링크 상대경로("/share/{token}")에 현재 호스트(origin)를 붙여
// 사용자가 그대로 복사·사용 가능한 절대 URL로 변환한다.
// 계약: url 은 상대경로이며 호스트 조합은 프런트(window.location.origin) 담당.
export function shareUrl(relative: string | null | undefined): string {
  if (!relative) return "";
  // 이미 절대 URL이면 그대로 반환(방어적).
  if (/^https?:\/\//i.test(relative)) return relative;
  const origin = window.location.origin;
  const path = relative.startsWith("/") ? relative : `/${relative}`;
  return `${origin}${path}`;
}

// 공유 링크 상태(활성/만료/비활성)를 isActive + expiredAt 로 계산한다.
// 계약 v1.4: 상태는 프런트가 계산. 우선순위: 비활성 > 만료 > 활성.
export type ShareStatus = "active" | "expired" | "disabled";

export function computeShareStatus(
  isActive: boolean,
  expiredAt: string | null | undefined
): ShareStatus {
  if (!isActive) return "disabled";
  if (expiredAt) {
    const exp = new Date(expiredAt).getTime();
    if (!Number.isNaN(exp) && exp <= Date.now()) return "expired";
  }
  return "active";
}
