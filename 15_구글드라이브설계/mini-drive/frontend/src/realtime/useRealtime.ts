import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { subscribeStomp } from "./stompClient";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/hooks/useAuth";
import { qk } from "@/hooks/queryKeys";
import type { WsEvent } from "@/types/ws";

// 구독 → 수신 이벤트별 React Query 캐시 무효화 + 토스트 알림.
export function useRealtime() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    if (!isAuthenticated) return;

    const unsubscribe = subscribeStomp((event: WsEvent) => {
      handleEvent(event);
    });

    function handleEvent(event: WsEvent) {
      switch (event.type) {
        case "FILE_UPLOADED":
        case "FILE_UPDATED":
        case "FILE_DELETED": {
          // FILE_* → 파일 목록 + 해당 파일 상세 invalidate.
          qc.invalidateQueries({ queryKey: ["files"] });
          const fileId = (event as { fileId?: number }).fileId;
          if (typeof fileId === "number") {
            qc.invalidateQueries({ queryKey: qk.file(fileId) });
            qc.invalidateQueries({ queryKey: qk.versions(fileId) });
          }
          break;
        }
        case "SHARE_CREATED": {
          const fileId = (event as { fileId?: number }).fileId;
          if (typeof fileId === "number") {
            qc.invalidateQueries({ queryKey: qk.file(fileId) });
          }
          break;
        }
        default:
          break;
      }
      // 미수신분 보강용 알림 목록도 갱신.
      qc.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "실시간 알림", description: event.type });
    }

    return unsubscribe;
  }, [isAuthenticated, qc, toast]);
}
