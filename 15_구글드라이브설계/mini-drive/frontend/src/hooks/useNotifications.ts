import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationsApi } from "@/api/notifications";
import { qk } from "./queryKeys";

export function useNotifications(unread?: boolean) {
  return useQuery({
    queryKey: qk.notifications(unread),
    queryFn: () => notificationsApi.list(unread),
  });
}

export function useMarkNotificationRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => notificationsApi.markRead(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}
