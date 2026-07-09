import { apiClient } from './client'

export const paymentsApi = {
  getMyPayments: (page = 0, size = 10) =>
    apiClient.get('/api/v1/payments/me', { params: { page, size } }),

  registerBillingKey: (customerKey?: string, authKey?: string) =>
    apiClient.post('/internal/v1/payments/pre-auth', { customerKey, authKey }),

  cancelPayment: (paymentId: string, cancellationCode: string, cancelReason: string) =>
    apiClient.post(`/api/v1/payments/${paymentId}/cancel`, { cancellationCode, cancelReason }),
}
