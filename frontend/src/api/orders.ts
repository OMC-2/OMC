import { apiClient } from './client'

export const ordersApi = {
  // 백엔드에 주문 목록 API 없음 → 결제 내역(payments/me)으로 대체
  getMyOrders: (page = 0, size = 10) =>
    apiClient.get('/api/v1/payments/me', { params: { page, size } }),

  getById: (orderId: string) =>
    apiClient.get(`/api/v1/orders/${orderId}`),

  refund: (orderId: string) =>
    apiClient.post(`/api/v1/orders/${orderId}/refund`),

  cancelPayment: (paymentId: string, cancellationCode: string, cancelReason: string) =>
    apiClient.post(`/api/v1/payments/${paymentId}/cancel`, { cancellationCode, cancelReason }),
}
