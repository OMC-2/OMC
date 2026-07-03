import { apiClient } from './client'

export const couponsApi = {
  getMyCoupons: () =>
    apiClient.get('/api/v1/coupons/me'),
}
