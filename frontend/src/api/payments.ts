import { apiClient } from './client'

export const paymentsApi = {
  getMyPayments: (page = 0, size = 10) =>
    apiClient.get('/api/v1/payments/me', { params: { page, size } }),

  registerBillingKey: (customerKey?: string, authKey?: string) =>
    apiClient.post('/internal/v1/payments/billing-keys', { customerKey, authKey }),
}
