import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { Input } from '../../components/ui/Input'

export function SignupPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setUsername] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authApi.signup(email, password, nickname)
      alert('회원가입 완료! 로그인해주세요.')
      navigate('/login')
    } catch (err: any) {
      setError(err.response?.data?.message ?? '회원가입에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="-mx-4 lg:-mx-8 -mt-8 min-h-[calc(100vh-4rem)] grid md:grid-cols-2">
      <div className="hidden md:block relative overflow-hidden bg-black">
        <img
          src="https://images.unsplash.com/photo-1557683316-973673baf926?w=800&q=85"
          alt="signup"
          className="absolute inset-0 h-full w-full object-cover opacity-50"
        />
        <div className="relative flex h-full flex-col justify-end p-12">
          <div className="flex items-center gap-1 mb-4">
            <span className="text-3xl font-black tracking-tighter text-white">SOLD</span>
            <span className="text-3xl font-black tracking-tighter text-red-500">OUT</span>
          </div>
          <p className="text-white/50 text-sm max-w-xs">
            가입하고 독점 래플과 드롭에 참여하세요.
          </p>
        </div>
      </div>

      <div className="flex items-center justify-center px-8 py-16 bg-white">
        <div className="w-full max-w-sm">
          <div className="mb-10">
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">CREATE ACCOUNT</p>
            <h1 className="text-3xl font-black tracking-tight">JOIN</h1>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <Input label="USERNAME" placeholder="닉네임" value={nickname} onChange={e => setUsername(e.target.value)} required />
            <Input label="EMAIL" type="email" placeholder="email@example.com" value={email} onChange={e => setEmail(e.target.value)} required />
            <Input label="PASSWORD" type="password" placeholder="••••••••" value={password} onChange={e => setPassword(e.target.value)} required />
            {error && <p className="bg-red-50 border border-red-100 px-4 py-3 text-xs text-red-600">{error}</p>}
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-black py-4 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-300 transition-colors mt-2"
            >
              {loading ? '가입 중...' : 'CREATE ACCOUNT'}
            </button>
            <p className="text-center text-xs text-gray-400 pt-2">
              이미 계정이 있으신가요?{' '}
              <Link to="/login" className="font-black text-black hover:text-red-500 transition-colors">LOGIN</Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  )
}
