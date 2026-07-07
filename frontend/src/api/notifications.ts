import { apiClient } from './client'

export const notificationsApi = {
  getMyNotifications: (page = 0, size = 20) =>
    apiClient.get('/api/v1/notifications', { params: { page, size } }),

  markAsRead: (notificationId: string) =>
    apiClient.patch(`/api/v1/notifications/${notificationId}/read`),
}
