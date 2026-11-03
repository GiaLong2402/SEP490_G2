import { api } from '@/shared/api/client';
import type { ID, Notification } from '@/shared/domain/entities';

export const notificationsApi = {
  list: () => api.get<Notification[]>('/notifications'),
  markRead: (id: ID) => api.post<void>(`/notifications/${id}/read`),
  markAllRead: () => api.post<void>('/notifications/read-all'),
};
