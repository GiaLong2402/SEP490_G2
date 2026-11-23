import { api } from './client'

/** Chuông portal — GET/POST /notifications (cùng contract staff). */
export const notificationsApi = {
  list: (signal) => api.get('/notifications', undefined, signal),
  markRead: (id) => api.post(`/notifications/${id}/read`),
  markAllRead: () => api.post('/notifications/read-all'),
}
