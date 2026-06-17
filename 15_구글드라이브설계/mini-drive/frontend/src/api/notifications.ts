import { api } from "./client";
import type { NotificationItem } from "@/types/api";

export const notificationsApi = {
  // GET /api/notifications?unread=true
  list: (unread?: boolean) =>
    api.get<NotificationItem[]>(
      `/notifications${unread ? "?unread=true" : ""}`
    ),

  // PATCH /api/notifications/{id}/read → 204
  markRead: (id: number) =>
    api.patch<void>(`/notifications/${id}/read`, {}),
};
