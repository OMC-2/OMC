import axios from 'axios'
import { apiClient } from './client'

// 쿠폰 목록은 공개 엔드포인트 — 비로그인 유저도 조회 가능
const publicClient = axios.create({ baseURL: '' })

export const couponsApi = {
  getAll: (page = 0, size = 20) =>
    publicClient.get('/api/v1/coupons', { params: { page, size } }),

  getMyCoupons: (page = 0, size = 20) =>
    apiClient.get('/api/v1/coupons/me', { params: { page, size } }),

  issueCoupon: (couponId: string) =>
    apiClient.post(`/api/v1/coupons/${couponId}/issue`),
}
