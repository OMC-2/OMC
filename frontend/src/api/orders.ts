import { apiClient } from './client'

export const ordersApi = {
  getMyOrders: (page = 0, size = 10) =>
    apiClient.get('/api/v1/orders', { params: { page, size } }),

  getById: (orderId: string) =>
    apiClient.get(`/api/v1/orders/${orderId}`),
}
