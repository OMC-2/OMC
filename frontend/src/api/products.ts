import { apiClient, publicClient } from './client'

export const productsApi = {
  // 공개 엔드포인트 — 비로그인 조회 가능
  getAll: (page = 0, size = 12) =>
    publicClient.get('/api/v1/products', { params: { page, size } }),

  getById: (productId: string) =>
    publicClient.get(`/api/v1/products/${productId}`),

  // 관리자 전용
  adminCreate: (body: any) => apiClient.post('/api/v1/admin/products', body),
  adminUpdate: (productId: string, body: any) => apiClient.put(`/api/v1/admin/products/${productId}`, body),
  adminDelete: (productId: string) => apiClient.delete(`/api/v1/admin/products/${productId}`),
  adminUpdateInventory: (productId: string, quantity: number) =>
    apiClient.patch(`/api/v1/admin/products/${productId}/inventory`, { quantity }),
}
