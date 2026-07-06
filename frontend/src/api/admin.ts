import { apiClient } from './client'

/* -- Products -- */
export const adminProductsApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get('/api/v1/products', { params: { page, size } }),

  create: (body: {
    name: string; description: string; price: number
    brand: string; category: string; imageUrl?: string; initialQuantity: number
  }) => apiClient.post('/api/v1/admin/products', body),

  update: (productId: string, body: object) =>
    apiClient.patch(`/api/v1/admin/products/${productId}`, body),

  delete: (productId: string) =>
    apiClient.delete(`/api/v1/admin/products/${productId}`),
}

/* -- Drops -- */
export const adminDropsApi = {
  getAll: (page = 0, size = 100) =>
    apiClient.get('/api/v1/admin/drops', { params: { page, size } }),

  create: (body: {
    productId: string; startAt: string; endAt: string
    totalQty: number; holdTtlSec: number
  }) => apiClient.post('/api/v1/admin/drops', body),

  update: (dropId: string, body: {
    startAt: string; endAt: string; totalQty: number; holdTtlSec: number
  }) => apiClient.put(`/api/v1/admin/drops/${dropId}`, body),

  close: (dropId: string) =>
    apiClient.post(`/api/v1/admin/drops/${dropId}/close`),

  delete: (dropId: string) =>
    apiClient.delete(`/api/v1/admin/drops/${dropId}`),
}

/* -- Raffles -- */
export const adminRafflesApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get('/api/v1/raffles', { params: { page, size } }),

  create: (body: {
    productId: string; name: string
    winnerCount: number; startedAt: string; endedAt: string
  }) => apiClient.post('/api/v1/admin/raffles', body),

  update: (raffleId: string, body: { name: string; winnerCount: number; startedAt?: string; endedAt?: string }) =>
    apiClient.put(`/api/v1/admin/raffles/${raffleId}`, body),

  penalize: (raffleId: string, userId: string) =>
    apiClient.post(`/api/v1/admin/raffles/${raffleId}/entries/${userId}/penalty`),

  updateStatus: (raffleId: string, status: string) =>
    apiClient.post(`/api/v1/admin/raffles/${raffleId}/status`, { status }),

  draw: (raffleId: string) =>
    apiClient.post(`/api/v1/admin/raffles/${raffleId}/draw`),

  getEntries: (raffleId: string, page = 0, size = 20) =>
    apiClient.get(`/api/v1/admin/raffles/${raffleId}/entries`, { params: { page, size } }),

  delete: (raffleId: string) =>
    apiClient.delete(`/api/v1/admin/raffles/${raffleId}`),
}

/* -- Payments (Admin) -- */
export const adminPaymentsApi = {
  getAll: (page = 0, size = 20, params?: { status?: string; salesType?: string }) =>
    apiClient.get('/api/v1/admin/payments', { params: { page, size, ...params } }),
}

/* -- Inventory (Admin) -- */
export const adminInventoryApi = {
  get: (productId: string) =>
    apiClient.get(`/api/v1/admin/products/${productId}/inventories`),

  update: (productId: string, totalQuantity: number, reason: string) =>
    apiClient.patch(`/api/v1/admin/products/${productId}/inventories`, { totalQuantity, reason }),
}

/* -- DLQ (Admin) -- */
export const adminDlqApi = {
  getAll: (status: 'FAILED' | 'RESOLVED' = 'FAILED', page = 0, size = 20) =>
    apiClient.get('/api/v1/admin/dlq', { params: { status, page, size } }),

  republish: (dlqId: string) =>
    apiClient.post(`/api/v1/admin/dlq/${dlqId}/republish`),
}

/* -- Outbox Events (Admin) -- */
export const adminOutboxApi = {
  retry: (eventId: string) =>
    apiClient.post(`/api/v1/admin/outbox-events/${eventId}/retry`),

  retryAll: () =>
    apiClient.post('/api/v1/admin/outbox-events/retry-all'),
}

/* -- Coupons -- */
export const adminCouponsApi = {
  getAll: (page = 0, size = 50) =>
    apiClient.get('/api/v1/coupons', { params: { page, size } }),

  create: (body: {
    name: string; discountType: string; discountValue: number
    maxDiscountAmount?: number; totalQuantity: number
    startedAt: string; expiredAt: string
  }) => apiClient.post('/api/v1/coupons', body),
}
