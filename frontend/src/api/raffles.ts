import { apiClient } from './client'

export const rafflesApi = {
  getAll: (page = 0, size = 12) =>
    apiClient.get('/api/v1/raffles', { params: { page, size } }),

  getById: (raffleId: string) =>
    apiClient.get(`/api/v1/raffles/${raffleId}`),

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

  getPublicWinners: (raffleId: string) =>
    apiClient.get(`/api/v1/raffles/${raffleId}/winners`),

  getParticipantsCount: (raffleId: string) =>
    apiClient.get(`/api/v1/raffles/${raffleId}/participants-count`),
}
