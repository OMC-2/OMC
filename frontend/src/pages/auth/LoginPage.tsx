import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { useAuthStore } from '../../store/authStore'
import { Input } from '../../components/ui/Input'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login, setUser } = useAuthStore()
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authApi.login(email, password)
      const { accessToken, refreshToken } = res.data.data
      login(accessToken, refreshToken)
      try {
        const profileRes = await authApi.getProfile()
        setUser(profileRes.data.data)
      } catch (err: any) {
        // role은 JWT에서 이미 세팅됨. 여기서 실패해도 로그인 자체는 성공
        console.warn('[login] getProfile 실패:', err?.response?.status, err?.message)
      }
      navigate('/')
    } catch (err: any) {
      setError(err.response?.data?.message ?? '이메일 또는 비밀번호를 확인하세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="-mx-4 lg:-mx-8 -mt-8 min-h-[calc(100vh-4rem)] grid md:grid-cols-2">
      {/* Left - image */}
      <div className="hidden md:block relative overflow-hidden bg-black">
        <img
          src="https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&q=85"
          alt="login"
          className="absolute inset-0 h-full w-full object-cover opacity-60"
        />
        <div className="relative flex h-full flex-col justify-end p-12">
          <div className="flex items-center gap-1 mb-4">
            <span className="text-3xl font-black tracking-tighter text-white">SOLD</span>
            <span className="text-3xl font-black tracking-tighter text-red-500">OUT</span>
          </div>
          <p className="text-white/50 text-sm max-w-xs">
            한정판을 위한 가장 공정한 플랫폼.<br />지금 시작하세요.
          </p>
        </div>
      </div>

      {/* Right - form */}
      <div className="flex items-center justify-center px-8 py-16 bg-white">
        <div className="w-full max-w-sm">
          <div className="mb-10">
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">WELCOME BACK</p>
            <h1 className="text-3xl font-black tracking-tight">LOGIN</h1>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <Input
              label="EMAIL"
              type="email"
              placeholder="email@example.com"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
            />
            <Input
              label="PASSWORD"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
            />
            {error && (
              <p className="bg-red-50 border border-red-100 px-4 py-3 text-xs text-red-600">{error}</p>
            )}
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-black py-4 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-300 transition-colors mt-2"
            >
              {loading ? '로그인 중...' : 'LOGIN'}
            </button>
            <p className="text-center text-xs text-gray-400 pt-2">
              계정이 없으신가요?{' '}
              <Link to="/signup" className="font-black text-black hover:text-red-500 transition-colors">
      