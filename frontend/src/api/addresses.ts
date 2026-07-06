import { apiClient } from './client'

export const addressesApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get('/api/v1/users/addresses', { params: { page, size } }),

  getById: (addressId: string) =>
    apiClient.get(`/api/v1/users/addresses/${addressId}`),

  create: (body: {
    recipientName: string
    phone: string
    zipCode: string
    address: string
    addressDetail?: string
    isDefault?: boolean
  }) => apiClient.post('/api/v1/users/addresses', body),

  update: (addressId: string, body: {
    recipientName?: string
    phone?: string
    zipCode?: string
    address?: string
    addressDetail?: string
  }) => apiClient.patch(`/api/v1/users/addresses/${addressId}`, body),

  delete: (addressId: string) =>
    apiClient.delete(`/api/v1/users/addresses/${addressId}`),

  setDefault: (addressId: string) =>
    apiClient.patch(`/api/v1/users/addresses/${addressId}/default`),
}
