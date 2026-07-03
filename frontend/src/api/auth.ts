import { apiClient } from './client'
import type { ApiResponse, LoginResponse } from '../types'

export const authApi = {
  login: (email: string, password: string) =>
    apiClient.post<ApiResponse<LoginResponse>>('/api/v1/users/login', { email, password }),

  signup: (email: string, password: string, nickname: string) =>
    apiClient.post('/api/v1/users/signup', { email, password, nickname }),

  getProfile: () =>
    apiClient.get('/api/v1/users/me'),

  refreshToken: (refreshToken: string) =>
    apiClient.post<ApiResponse<LoginResponse>>('/api/v1/users/token/refresh', { refreshToken }),
}
