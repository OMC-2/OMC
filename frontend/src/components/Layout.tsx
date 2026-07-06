import { useState, useRef, useEffect } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { notificationsApi } from '../api/notifications'
import { ShoppingBag, Ticket, Package, User, LogOut, Menu, X, ShoppingCart, Settings, Bell, ChevronDown } from 'lucide-react'
import { formatDate } from '../lib/utils'

function NotificationBell() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const qc = useQueryClient()

  const { data } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationsApi.getMyNotifications(0, 10),
    refetchInterval: 30000,
  })

  const readMutation = useMutation({
    mutationFn: (id: string) => notificationsApi.markAsRead(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  const notifications = data?.data?.data?.content ?? []
  const unread = notifications.filter((n: any) => !n.isRead).length

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen(v => !v)} className="relative text-gray-500 hover:text-black transition-colors">
        <Bell size={19} />
        {unread > 0 && (
          <span className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[9px] font-black text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-8 z-50 w-80 border border-gray-100 bg-white shadow-lg">
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <p className="text-xs font-black tracking-widest">NOTIFICATIONS</p>
            <button onClick={() => setOpen(false)}><X size={14} className="text-gray-300" /></button>
          </div>
          {notifications.length === 0 ? (
            <div className="py-8 text-center text-xs text-gray-300 tracking-wider">알림 없음</div>
          ) : (
            <div className="max-h-80 overflow-y-auto divide-y divide-gray-50">
              {notifications.map((n: any) => (
                <div
                  key={n.notificationId}
                  className={`px-4 py-3 cursor-pointer hover:bg-gray-50 transition-colors ${!n.isRead ? 'bg-blue-50/40' : ''}`}
                  onClick={() => { if (!n.isRead) readMutation.mutate(n.notificationId) }}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className={`text-xs font-bold truncate ${!n.isRead ? 'text-gray-900' : 'text-gray-500'}`}>{n.title}</p>
                      <p className="text-[10px] text-gray-400 mt-0.5 line-clamp-2">{n.content}</p>
                      <p className="text-[10px] text-gray-300 mt-1">{n.createdAt ? formatDate(n.createdAt) : ''}</p>
                    </div>
                    {!n.isRead && <span className="mt-1 h-2 w-2 rounded-full bg-blue-500 shrink-0" />}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ── 운영정책 아코디언 섹션 ──────────────────────────────────────────────
const POLICIES = [
  {
    title: '래플 운영 정책',
    content: [
      '래플은 선착순이 아닌 무작위 추첨 방식으로 당첨자를 선정합니다.',
      '1인 1계정, 1래플당 1회만 응모 가능합니다. 중복 응모 시 자동 거부됩니다.',
      '응모 마감 후 자동 추첨이 진행되며, 결과는 등록된 이메일 및 Slack으로 개별 통보됩니다.',
      '당첨자는 통보일로부터 48시간 이내에 결제를 완료해야 하며, 미결제 시 당첨이 자동 취소됩니다.',
      '래플 응모 시 등록된 결제 수단(빌링키)이 필요하며, 당첨 시 자동 결제됩니다.',
      '쿠폰은 응모 시 1회 사용 가능하며, 취소·환불 시 쿠폰은 반환되지 않습니다.',
    ],
  },
  {
    title: '드롭 구매 정책',
    content: [
      '드롭은 지정된 일시에 선착순으로 구매 가능하며, 재고 소진 시 즉시 마감됩니다.',
      '구매 의사 확인(Hold) 상태는 설정된 대기 시간(holdTtlSec) 동안 유지되며, 이 시간 내 결제를 완료해야 합니다.',
      '대기 시간 초과 시 Hold가 자동 해제되고 재구매가 불가합니다.',
      '1인당 동일 드롭 상품 1개 구매를 원칙으로 합니다.',
      '드롭 구매 완료 후 단순 변심에 의한 취소는 결제 당일에만 가능합니다.',
    ],
  },
  {
    title: '결제 및 환불 정책',
    content: [
      '결제는 등록된 카드(빌링키)를 통해 자동으로 처리됩니다.',
      '결제 취소(USER_CANCEL)는 상품 발송 전까지 마이페이지 > 주문 내역에서 신청 가능합니다.',
      '상품 하자·오배송의 경우 수령일로부터 7일 이내 고객센터를 통해 환불 신청이 가능합니다.',
      '단순 변심 환불은 미개봉·미사용 상태에서 수령일로부터 7일 이내 신청 가능하며, 왕복 배송비는 구매자 부담입니다.',
      '한정판 특성상 당첨 취소 후 재응모는 불가하며, 당첨 포기 시 해당 래플에 재참여할 수 없습니다.',
      '결제 오류, 이중 결제 등 시스템 문제로 인한 환불은 확인 후 영업일 기준 3일 이내 처리됩니다.',
    ],
  },
  {
    title: '계정 및 개인정보 정책',
    content: [
      '회원가입 시 입력한 이메일 및 닉네임은 서비스 이용과 당첨 통보에 사용됩니다.',
      '수집 항목: 이메일, 닉네임, 결제 수단 정보(빌링키), 배송지 정보, 서비스 이용 기록.',
      '개인정보는 서비스 제공 목적 외 제3자에게 제공되지 않습니다.',
      '회원 탈퇴 시 모든 개인정보는 즉시 삭제되며, 단 관계법령에 따라 일정 기간 보관이 필요한 정보는 예외입니다.',
      '계정 공유 및 부정 사용이 확인될 경우 사전 통보 없이 이용이 제한될 수 있습니다.',
      'Slack ID는 당첨 알림 발송 목적으로만 사용되며, 선택 입력 항목입니다.',
    ],
  },
]

function PolicyAccordion() {
  const [openIdx, setOpenIdx] = useState<number | null>(null)
  return (
    <div className="border-t border-white/10 pt-8 mt-8 text-left max-w-4xl mx-auto">
      <p className="text-[10px] font-black tracking-[0.3em] text-white/30 mb-4 text-center">POLICIES</p>
      <div className="space-y-px">
        {POLICIES.map((p, i) => (
          <div key={p.title} className="border border-white/10">
            <button
              className="w-full flex items-center justify-between px-5 py-3.5 text-left"
              onClick={() => setOpenIdx(openIdx === i ? null : i)}
            >
              <span className="text-[11px] font-black tracking-wider text-white/60">{p.title}</span>
              <ChevronDown
                size={14}
                className={`text-white/30 transition-transform duration-200 ${openIdx === i ? 'rotate-180' : ''}`}
              />
            </button>
            {openIdx === i && (
              <ul className="px-5 pb-4 space-y-2 border-t border-white/5">
                {p.content.map((line, j) => (
                  <li key={j} className="flex gap-2 text-[11px] text-white/40 leading-relaxed">
                    <span className="text-white/20 shrink-0 mt-0.5">—</span>
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

export function Layout() {
  const { isAuthenticated, user, logout } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  const handleLogout = () => { logout(); navigate('/login') }

  const navLinks = [
    { to: '/products', label: 'PRODUCTS', icon: Package },
    { to: '/drops', label: 'DROPS', icon: ShoppingBag },
    { to: '/raffles', label: 'RAFFLE', icon: Ticket },
  ]

  const isActive = (path: string) => location.pathname.startsWith(path)

  return (
    <div className="min-h-screen bg-white">
      <div className="bg-black py-2 text-center text-xs tracking-widest text-white/70">
        FREE SHIPPING ON ORDERS OVER &#8361;100,000
      </div>

      <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/95 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 lg:px-8">

          <Link to="/" className="flex items-center gap-1">
            <span className="text-2xl font-black tracking-tighter text-black">SOLD</span>
            <span className="text-2xl font-black tracking-tighter text-red-500">OUT</span>
          </Link>

          <nav className="hidden md:flex items-center gap-8">
            {navLinks.map(({ to, label }) => (
              <Link
                key={to}
                to={to}
                className={`text-xs font-bold tracking-widest transition-colors ${
                  isActive(to) ? 'text-black border-b-2 border-black pb-0.5' : 'text-gray-400 hover:text-black'
                }`}
              >
                {label}
              </Link>
            ))}
          </nav>

          <div className="hidden md:flex items-center gap-4">
            {isAuthenticated ? (
              <>
                <NotificationBell />
                <Link to="/orders" className="text-gray-500 hover:text-black transition-colors">
                  <ShoppingCart size={20} />
                </Link>
                <Link
                  to="/mypage"
                  className="flex items-center gap-1.5 text-xs font-bold tracking-wide text-gray-700 hover:text-black transition-colors"
                >
                  <User size={16} />
                  {(user?.nickname ?? user?.username ?? 'MY PAGE').toUpperCase()}
                </Link>
                {user?.role === 'ADMIN' && (
                  <Link to="/admin" className="text-gray-300 hover:text-black transition-colors" title="관리자">
                    <Settings size={16} />
                  </Link>
                )}
                <button
                  onClick={handleLogout}
                  className="flex items-center gap-1 text-xs text-gray-400 hover:text-red-500 transition-colors"
                >
                  <LogOut size={15} />
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="text-xs font-bold tracking-wide text-gray-500 hover:text-black transition-colors">
                  LOGIN
                </Link>
                <Link
                  to="/signup"
                  className="rounded-none bg-black px-5 py-2 text-xs font-bold tracking-widest text-white hover:bg-gray-800 transition-colors"
                >
                  JOIN
                </Link>
              </>
            )}
          </div>

          <button className="md:hidden p-1" onClick={() => setMobileOpen(!mobileOpen)}>
            {mobileOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>

        {mobileOpen && (
          <div className="md:hidden border-t border-gray-100 bg-white">
            <div className="px-4 py-4 space-y-1">
              {navLinks.map(({ to, label, icon: Icon }) => (
                <Link
                  key={to}
                  to={to}
                  onClick={() => setMobileOpen(false)}
                  className="flex items-center gap-3 rounded px-3 py-3 text-sm font-bold tracking-wider text-gray-700 hover:bg-gray-50"
                >
                  <Icon size={16} />{label}
                </Link>
              ))}
              <div className="border-t border-gray-100 pt-3 mt-3">
                {isAuthenticated ? (
                  <>
                    <Link to="/orders" onClick={() => setMobileOpen(false)} className="flex items-center gap-3 px-3 py-3 text-sm font-bold tracking-wider text-gray-700 hover:bg-gray-50"><ShoppingCart size={16} />ORDERS</Link>
                    <Link to="/mypage" onClick={() => setMobileOpen(false)} className="flex items-center gap-3 px-3 py-3 text-sm font-bold tracking-wider text-gray-700 hover:bg-gray-50"><User size={16} />MY PAGE</Link>
                    <button onClick={() => { handleLogout(); setMobileOpen(false) }} className="flex w-full items-center gap-3 px-3 py-3 text-sm font-bold text-red-500 hover:bg-gray-50"><LogOut size={16} />LOGOUT</button>
                  </>
                ) : (
                  <>
                    <Link to="/login" onClick={() => setMobileOpen(false)} className="block px-3 py-3 text-sm font-bold tracking-wider text-gray-700">LOGIN</Link>
                    <Link to="/signup" onClick={() => setMobileOpen(false)} className="block px-3 py-3 text-sm font-bold tracking-wider text-gray-700">JOIN</Link>
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </header>

      <main className="mx-auto max-w-7xl px-4 lg:px-8 py-8">
        <Outlet />
      </main>

      <footer className="mt-20 border-t border-gray-100 bg-black py-12 px-4">
        {/* 브랜드 + 링크 */}
        <div className="mx-auto max-w-4xl">
          <div className="flex flex-col items-center mb-8">
            <div className="flex items-center gap-1 mb-3">
              <span className="text-xl font-black tracking-tighter text-white">SOLD</span>
              <span className="text-xl font-black tracking-tighter text-red-500">OUT</span>
            </div>
            <p className="text-[10px] text-white/20 tracking-widest text-center max-w-sm">
              한정판 스니커즈 &amp; 패션 아이템 드롭 &amp; 래플 플랫폼.<br />
              공정한 추첨으로 모두에게 동등한 기회를 제공합니다.
            </p>
          </div>

          {/* 빠른 링크 */}
          <div className="grid grid-cols-3 gap-4 mb-8 border-t border-white/5 pt-8 text-center">
            {[
              { label: 'PRODUCTS', to: '/products' },
              { label: 'DROPS', to: '/drops' },
              { label: 'RAFFLE', to: '/raffles' },
            ].map(({ label, to }) => (
              <Link key={to} to={to} className="text-[10px] font-black tracking-widest text-white/30 hover:text-white transition-colors">
                {label}
              </Link>
            ))}
          </div>

          {/* 운영정책 아코디언 */}
          <PolicyAccordion />

          {/* 카피라이트 */}
          <p className="mt-8 text-center text-[10px] text-white/20 tracking-widest">
            &#169; 2025 SOLDOUT. ALL RIGHTS RESERVED.
          </p>
        </div>
      </footer>
    </div>
  )
}
