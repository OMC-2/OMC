import { apiClient } from './client'

/* ── Products ── */
export const adminProductsApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get('/api/v1/products', { params: { page, size } }),

  create: (body: {
    name: string; description: string; price: number
    brand: string; category: string; imageUrl?: string; initialQuantity: number
  }) => apiClient.post('/api/v1/admin/products', body),

  update: (productId: string, body: object) =>
    apiClient.put(`/api/v1/admin/products/${productId}`, body),

  delete: (productId: string) =>
    apiClient.delete(`/api/v1/admin/products/${productId}`),
}

/* ── Drops ── */
export const adminDropsApi = {
  getAll: (page = 0, size = 100) =>
    apiClient.get('/api/v1/drops', { params: { page, size } }),

  create: (body: {
    productId: string; startAt: string; endAt: string
    totalQty: number; holdTtlSec: number
  }) => apiClient.post('/api/v1/admin/drops', body),

  close: (dropId: string) =>
    apiClient.post(`/api/v1/admin/drops/${dropId}/close`),

  delete: (dropId: string) =>
    apiClient.delete(`/api/v1/admin/drops/${dropId}`),
}

/* ── Raffles ── */
export const adminRafflesApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get('/api/v1/raffles', { params: { page, size } }),

  create: (body: {
    productId: string; name: string
    winnerCount: number; startedAt: string; endedAt: string
  }) => apiClient.post('/api/v1/admin/raffles', body),

  update: (raffleId: string, body: { name: string; winnerCount: number }) =>
    apiClient.put(`/api/v1/admin/raffles/${raffleId}`, body),

  updateStatus: (raffleId: string, status: string) =>
    apiClient.patch(`/api/v1/admin/raffles/${raffleId}/status`, { status }),

  draw: (raffleId: string) =>
    apiClient.post(`/api/v1/admin/raffles/${raffleId}/draw`),

  getEntries: (raffleId: string, page = 0, size = 20) =>
    apiClient.get(`/api/v1/admin/raffles/${raffleId}/entries`, { params: { page, size } }),

  delete: (raffleId: string) =>
    apiClient.delete(`/api/v1/admin/raffles/${raffleId}`),
}
