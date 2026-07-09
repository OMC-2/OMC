import axios from 'axios'

// Vite 프록시 사용 (/api → http://localhost:8080)
export const apiClient = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
})

// 인증 불필요 공개 엔드포인트용 클라이언트
export const publicClient = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
})

// 요청 인터셉터 - JWT 자동 주입
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 응답 인터셉터 - 401 시 토큰 갱신
apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        try {
          const { data } = await axios.post('/api/v1/users/token/refresh', { refreshToken })
          const newToken = data.data.accessToken
          localStorage.setItem('accessToken', newToken)
          original.headers.Authorization = `Bearer ${newToken}`
          return apiClient(original)
        } catch {
          localStorage.clear()
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  }
)
