import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: { userId: string; email: string; username?: string; nickname?: string; role?: string } | null
  isAuthenticated: boolean
  login: (accessToken: string, refreshToken: string) => void
  setUser: (user: AuthState['user']) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      login: (accessToken, refreshToken) => {
        localStorage.setItem('accessToken', accessToken)
        localStorage.setItem('refreshToken', refreshToken)
        // JWT payload에서 realm_access.roles로 role 즉시 추출 (getProfile 의존 제거)
        let initialUser: AuthState['user'] = null
        try {
          const payload = JSON.parse(atob(accessToken.split('.')[1]))
          const roles: string[] = payload?.realm_access?.roles ?? []
          const role = roles.includes('ADMIN') ? 'ADMIN' : 'USER'
          initialUser = { userId: '', email: payload?.email ?? '', role }
        } catch { /* JWT 파싱 실패 시 무시 */ }
   