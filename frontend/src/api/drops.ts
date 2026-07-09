import { apiClient, publicClient } from './client'

export const dropsApi = {
  // 공개 엔드포인트
  getAll: (page = 0, size = 12) =>
    publicClient.get('/api/v1/drops', { params: { page, size } }),

  getById: (dropId: string) =>
    publicClient.get(`/api/v1/drops/${dropId}`),

  // 인증 필요
  purchase: (dropId: string) =>
    apiClient.post(`/api/v1/drops/${dropId}/purchase`),

  // 관리자 전용
  adminCreate: (body: any) => apiClient.post('/api/v1/admin/drops', body),
  adminUpdate: (dropId: string, body: any) => apiClient.put(`/api/v1/admin/drops/${dropId}`, body),
  adminDelete: (dropId: string) => apiClient.delete(`/api/v1/admin/drops/${dropId}`),
}
