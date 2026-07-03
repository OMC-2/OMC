import { NavLink, Outlet, Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../../store/authStore'
import { authApi } from '../../api/auth'
import { LayoutDashboard, Package, ShoppingBag, Ticket, LogOut, ShieldAlert } from 'lucide-react'

const nav = [
  { to: '/admin', label: '대시보드', icon: LayoutDashboard, end: true },
  { to: '/admin/products', label: '상품 관리', icon: Package, end: false },
  { to: '/admin/drops', label: '드롭 관리', icon: ShoppingBag, end: false },
  { to: '/admin/raffles', label: '래플 관리', icon: Ticket, end: false },
]

export function AdminLayout() {
  const { isAuthenticated, logout } = useAuthStore()

  const { data: profileData, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: () => authApi.getProfile(),
    enabled: isAuthenticated,
    retry: false,
  })

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (isLoading) return (
    <div className="flex min-h-screen items-center justify-center bg-gray-950">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-white/10 border-t-white/60" />
    </div>
  )

  const profile = profileData?.data?.data
  const role = profile?.role ?? profile?.roles?.[0]
  if (profile && role !== 'ADMIN') {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-gray-950 text-white">
        <ShieldAlert size={48} className="text-red-500" />
        <p className="text-xl font-black tracking-tight">접근 권한 없음</p>
        <p className="text-sm text-white/30">관리자 계정으로 로그인하세요.</p>
        <button onClick={logout} className="mt-2 text-xs font-bold text-white/30 underline hover:text-red-400">로그아웃</button>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-gray-950 text-white">
      {/* Sidebar */}
      <aside className="w-56 shrink-0 border-r border-white/5 flex flex-col">
        <div className="px-5 py-6 border-b border-white/5">
          <div className="flex items-center gap-1">
            <span className="text-lg font-black tracking-tighter">SOLD</span>
            <span className="text-lg font-black tracking-tighter text-red-500">OUT</span>
          </div>
          <p className="text-[10px] text-white/30 tracking-widest mt-0.5">ADMIN</p>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-0.5">
          {nav.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-2.5 rounded px-3 py-2.5 text-xs font-medium transition-colors ${
                  isActive
                    ? 'bg-white/10 text-white'
                    : 'text-white/40 hover:bg-white/5 hover:text-white/70'
                }`
              }
            >
              <Icon size={15} />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="px-3 py-4 border-t border-white/5">
          {profile && (
            <div className="mb-3 px-3">
              <p className="text-[10px] text-white/20 truncate">{profile.email}</p>
            </div>
          )}
          <button
            onClick={logout}
            className="flex items-center gap-2.5 w-full rounded px-3 py-2.5 text-xs font-medium text-white/30 hover:text-red-400 hover:bg-white/5 transition-colors"
          >
            <LogOut size={15} />로그아웃
          </button>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 overflow-auto">
        <Outlet />
      </div>
    </div>
  )
}
