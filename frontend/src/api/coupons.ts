import { apiClient } from './client'

export const couponsApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get('/api/v1/coupons', { params: { page, size } }),

  getMyCoupons: (page = 0, size = 20) =>
    apiClient.get('/api/v1/coupons/me', { params: { page, size } }),

  issueCoupon: (couponId: string) =>
    apiClient.post(`/api/v1/coupons/${couponId}/issue`),
}
