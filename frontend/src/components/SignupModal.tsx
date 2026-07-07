import { useState } from 'react'
import { X, CheckCircle } from 'lucide-react'
import { authApi } from '../api/auth'
import { Input } from './ui/Input'

interface Props {
  onClose: () => void
  onLoginClick: () => void
}

export function SignupModal({ onClose, onLoginClick }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authApi.signup(email, password, nickname)
      setSuccess(true)
    } catch (err: any) {
      const res = err.response?.data
      if (res?.data && Array.isArray(res.data) && res.data.length > 0) {
        setError(res.data.map((f: any) => f.reason ?? f.message).join(' / '))
      } else {
        setError(res?.message ?? '회원가입에 실패했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="relative w-full max-w-md bg-white">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-black transition-colors"
        >
          <X size={20} />
        </button>

        {success ? (
          /* ── 완료 화면 ── */
          <div className="flex flex-col items-center justify-center px-10 py-16 text-center">
            <CheckCircle size={52} className="text-black mb-6" strokeWidth={1.5} />
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">WELCOME</p>
            <h2 className="text-2xl font-black tracking-tight mb-3">가입 완료!</h2>
            <p className="text-sm text-gray-500 mb-8">
              <span className="font-bold text-black">{email}</span>으로<br />
              가입이 완료되었습니다.
            </p>
            <button
              onClick={() => { onClose(); onLoginClick() }}
              className="w-full bg-black py-3.5 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 transition-colors"
            >
              로그인하러 가기
            </button>
          </div>
        ) : (
          /* ── 가입 폼 ── */
          <div className="px-10 py-10">
            <div className="mb-8">
              <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1.5">CREATE ACCOUNT</p>
              <h2 className="text-2xl font-black tracking-tight">JOIN OMC</h2>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                label="USERNAME"
                placeholder="닉네임"
                value={nickname}
                onChange={e => setNickname(e.target.value)}
                required
              />
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
                placeholder="8자 이상"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />

              {error && (
                <p className="bg-red-50 border border-red-100 px-4 py-3 text-xs text-red-600">
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full bg-black py-3.5 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-300 transition-colors mt-2"
              >
                {loading ? '가입 중...' : 'CREATE ACCOUNT'}
              </button>

              <p className="text-center text-xs text-gray-400 pt-1">
                이미 계정이 있으신가요?{' '}
                <button
                  type="button"
                  onClick={() => { onClose(); onLoginClick() }}
                  className="font-black text-black hover:text-red-500 transition-colors"
                >
                  LOGIN
                </button>
              </p>
            </form>
          </div>
        )}
      </div>
    </div>
  )
}
