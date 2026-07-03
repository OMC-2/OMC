import { apiClient } from './client'

export const productsApi = {
  getAll: (page = 0, size = 12) =>
    apiClient.get('/api/v1/products', { params: { page, size } }),

  getById: (productId: string) =>
    apiClient.get(`/api/v1/products/${productId}`),
}
