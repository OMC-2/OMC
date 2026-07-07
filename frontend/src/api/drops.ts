import { apiClient } from './client'

export const dropsApi = {
  getAll: (page = 0, size = 12) =>
    apiClient.get('/api/v1/drops', { params: { page, size } }),

  getById: (dropId: string) =>
    apiClient.get(`/api/v1/drops/${dropId}`),

  // POST /api/v1/drops/{dropId}/purchase — no body, userId from JWT
  purchase: (dropId: string) =>
    apiClient.post(`/api/v1/drops/${dropId}/purchase`),
}
