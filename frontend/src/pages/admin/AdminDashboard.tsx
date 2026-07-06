import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminProductsApi, adminDropsApi, adminRafflesApi } from '../../api/admin'
import { authApi } from '../../api/auth'
import { Package, ShoppingBag, Ticket, ChevronRight, UserPlus, X } from 'lucide-react'

export function AdminDashboard() {
  const { data: products } = useQuery({ queryKey: ['admin-products'], queryFn: () => adminProductsApi.getAll() })
  const { data: drops } = useQuery({ queryKey: ['admin-drops'], queryFn: () => adminDropsApi.getAll() })
  const { data: raffles } = useQuery({ queryKey: ['admin-raffles'], queryFn: () => adminRafflesApi.getAll() })

  const [showAdminForm, setShowAdminForm] = useState(false)
  const [adminForm, setAdminForm] = useState({ email: '', password: '', nickname: '' })

  const adminSignupMutation = useMutation({
    mutationFn: () => authApi.adminSignup(adminForm.email, adminForm.password, adminForm.nickname),
    onSuccess: () => {
      alert(`어드민 계정 생성 완료: ${adminForm.email}`)
      setAdminForm({ email: '', password: '', nickname: '' })
      setShowAdminForm(false)
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '생성 실패'),
  })

  const productCount = products?.data?.data?.totalElements ?? products?.data?.data?.content?.length ?? '-'
  const dropCount = drops?.data?.data?.totalElements ?? drops?.data?.data?.content?.length ?? '-'
  const raffleCount = raffles?.data?.data?.totalElements ?? raffles?.data?.data?.content?.length ?? '-'
  const liveRaffles = (raffles?.data?.data?.content ?? []).filter((r: any) => r.status === 'OPEN').length

  const cards = [
    { label: '상품', value: productCount, sub: '전체 등록 상품', icon: Package, to: '/admin/products', color: 'text-blue-400' },
    { label: '드롭', value: dropCount, sub: '전체 드롭', icon: ShoppingBag, to: '/admin/drops', color: 'text-purple-400' },
    { label: '래플', value: raffleCount, sub: `진행중 ${liveRaffles}개`, icon: Ticket, to: '/admin/raffles', color: 'text-red-400' },
  ]

  const recentRaffles = (raffles?.data?.data?.content ?? []).slice(0, 5)

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-xl font-black tracking-tight">대시보드</h1>
        <p className="text-xs text-white/30 mt-1">SOLDOUT 관리자 패널</p>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-10">
        {cards.map(({ label, value, sub, icon: Icon, to, color }) => (
          <Link key={to} to={to} className="group rounded-lg border border-white/5 bg-white/5 hover:bg-white/5 p-6 transition-colors">
            <div className="flex items-start justify-between mb-4">
              <Icon size={20} className={color} />
              <ChevronRight size={14} className="text-white/20 group-hover:text-white/50 transition-colors" />
            </div>
            <p className="text-3xl font-black text-white">{value}</p>
            <p className="text-xs font-bold text-white/40 mt-1 tracking-wide">{label}</p>
            <p className="text-[10px] text-white/20 mt-0.5">{sub}</p>
          </Link>
        ))}
      </div>

      <div className="mb-10">
        <div className="flex items-center justify-between mb-4">
          <p className="text-xs font-black tracking-widest text-white/50">어드민 계정 생성</p>
          <button
            onClick={() => setShowAdminForm(v => !v)}
            className="flex items-center gap-1.5 text-[10px] font-black tracking-wider text-white/30 hover:text-white transition-colors"
          >
            <UserPlus size={13} />
            {showAdminForm ? '닫기' : '계정 추가'}
          </button>
        </div>

        {showAdminForm && (
          <div className="rounded-lg border border-white/10 bg-white/5 p-5">
            <p className="text-[10px] text-white/20 mb-4">
              <code className="bg-white/10 px-1.5 py-0.5 rounded">POST /api/v1/users/admin/signup</code>
              {' '}— X-Admin-Secret: test-admin-secret
            </p>
            <div className="grid grid-cols-3 gap-3 mb-4">
              {[
                { label: '이메일', key: 'email', placeholder: 'admin@example.com', type: 'email' },
                { label: '비밀번호', key: 'password', placeholder: '...', type: 'password' },
                { label: '닉네임', key: 'nickname', placeholder: '관리자', type: 'text' },
              ].map(({ label, key, placeholder, type }) => (
                <div key={key}>
                  <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">{label}</label>
                  <input
                    type={type}
                    placeholder={placeholder}
                    value={(adminForm as any)[key]}
                    onChange={e => setAdminForm(p => ({ ...p, [key]: e.target.value }))}
                    className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30"
                  />
                </div>
              ))}
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => { setShowAdminForm(false); setAdminForm({ email: '', password: '', nickname: '' }) }}
                className="flex items-center gap-1.5 border border-white/10 px-4 py-2 text-xs font-bold text-white/40 hover:text-white transition-colors"
              >
                <X size={13} />취소
              </button>
              <button
                disabled={!adminForm.email || !adminForm.password || !adminForm.nickname || adminSignupMutation.isPending}
                onClick={() => adminSignupMutation.mutate()}
                className="flex items-center gap-1.5 bg-white px-4 py-2 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors"
              >
                <UserPlus size={13} />
                {adminSignupMutation.isPending ? '생성 중...' : '어드민 생성'}
              </button>
            </div>
          </div>
        )}
      </div>

      <div>
        <div className="flex items-center justify-between mb-4">
          <p className="text-xs font-black tracking-widest text-white/50">최근 래플</p>
          <Link to="/admin/raffles" className="text-[10px] text-white/30 hover:text-white transition-colors">전체 보기 -&gt;</Link>
        </div>
        <div className="rounded-lg border border-white/5 overflow-hidden">
          {recentRaffles.length === 0 ? (
            <div className="py-10 text-center text-xs text-white/20">래플 없음</div>
          ) : (
            recentRaffles.map((raffle: any, i: number) => (
              <div key={raffle.raffleId} className={`flex items-center justify-between px-5 py-3.5 ${i < recentRaffles.length - 1 ? 'border-b border-white/5' : ''}`}>
                <div>
                  <p className="text-xs font-medium text-white/80">{raffle.name}</p>
                  <p className="text-[10px] text-white/30 mt-0.5">당첨 {raffle.winnerCount}명</p>
                </div>
                <span className={`text-[10px] font-black tracking-wider px-2 py-1 rounded-sm ${
                  raffle.status === 'OPEN' ? 'bg-red-500/20 text-red-400'
                  : raffle.status === 'DRAWN' ? 'bg-green-500/20 text-green-400'
                  : 'bg-white/5 text-white/30'
                }`}>
                  {raffle.status}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
