import { apiClient, publicClient } from './client'

export const rafflesApi = {
  // 공개 엔드포인트
  getAll: (page = 0, size = 12) =>
    publicClient.get('/api/v1/raffles', { params: { page, size } }),

  getById: (raffleId: string) =>
    publicClient.get(`/api/v1/raffles/${raffleId}`),

  getPublicWinners: (raffleId: string) =>
    publicClient.get(`/api/v1/raffles/${raffleId}/winners`),

  getParticipantsCount: (raffleId: string) =>
    publicClient.get(`/api/v1/raffles/${raffleId}/participants-count`),

  // 인증 필요
  enter: (raffleId: string, body: {
    billingKeyId?: string | null
    couponId?: string | null
    originalAmount: number
    discountAmount: number
    finalAmount: number
  }) => apiClient.post(`/api/v1/raffles/${raffleId}/entries`, body),

  getMyResult: (raffleId: string) =>
    apiClient.get(`/api/v1/raffles/${raffleId}/winners/me`),

  getMyEntries: (page = 0, size = 10) =>
    apiClient.get('/api/v1/raffles/entries/me', { params: { page, size } }),

  // 관리자 전용
  adminCreate: (body: any) => apiClient.post('/api/v1/admin/raffles', body),
  adminUpdate: (raffleId: string, body: any) => apiClient.put(`/api/v1/admin/raffles/${raffleId}`, body),
  adminDelete: (raffleId: string) => apiClient.delete(`/api/v1/admin/raffles/${raffleId}`),
  adminDraw: (raffleId: string) => apiClient.post(`/api/v1/admin/raffles/${raffleId}/draw`),
  adminUpdateStatus: (raffleId: string, status: string) =>
    apiClient.post(`/api/v1/admin/raffles/${raffleId}/status`, { status }),
  adminGetEntries: (raffleId: string, page = 0, size = 20) =>
    apiClient.get(`/api/v1/admin/raffles/${raffleId}/entries`, { params: { page, size } }),
  adminPenalizeUser: (raffleId: string, userId: string) =>
    apiClient.post(`/api/v1/admin/raffles/${raffleId}/entries/${userId}/penalty`),
}
