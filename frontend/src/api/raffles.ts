import { apiClient } from './client'

export const rafflesApi = {
  getAll: (page = 0, size = 12) =>
    apiClient.get('/api/v1/raffles', { params: { page, size } }),

  getById: (raffleId: string) =>
    apiClient.get(`/api/v1/raffles/${raffleId}`),

  // billingKeyId, couponId 모두 optional (무료 래플 지원)
  enter: (raffleId: string, body: {
    billingKeyId?: string | null
    couponId?: string | null
    originalAmount: number
    discountAmount: number
    finalAmount: number
  }) => apiClient.post(`/api/v1/raffles/${raffleId}/entries`, body),

  // GET /api/v1/raffles/{raffleId}/winners/me — 내 당첨 여부 (auth required)
  getMyResult: (raffleId: string) =>
    apiClient.get(`/api/v1/raffles/${raffleId}/winners/me`),

  // GET /api/v1/raffles/entries/me — 내 전체 응모 내역 (auth required)
  getMyEntries: (page = 0, size = 10) =>
    apiClient.get('/api/v1/raffles/entries/me', { params: { page, size } }),
}
