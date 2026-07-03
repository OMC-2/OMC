import { useQuery } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { rafflesApi } from '../../api/raffles'
import { ordersApi } from '../../api/orders'
import { couponsApi } from '../../api/coupons'
import { Spinner } from '../../components/ui/Spinner'
import { useAuthStore } from '../../store/authStore'
import { formatDate } from '../../lib/utils'
import { LogOut, ChevronRight } from 'lucide-react'

export function MyPage() {
  const { isAuthenticated, logout } = useAuthStore()

  const { data: profileData, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: () => authApi.getProfile(),
    enabled: isAuthenticated,
  })

  const { data: myEntriesData } = useQuery({
    queryKey: ['my-entries'],
    queryFn: () => rafflesApi.getMyEntries(),
    enabled: isAuthenticated,
  })

  const { data: ordersData } = useQuery({
    queryKey: ['orders'],
    queryFn: () => ordersApi.getMyOrders(),
    enabled: isAuthenticated,
  })

  const { data: couponsData } = useQuery({
    queryKey: ['my-coupons'],
    queryFn: () => couponsApi.getMyCoupons(),
    enabled: isAuthenticated,
  })

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (isLoading) return <Spinner className="py-20" />

  const profile = profileData?.data?.data
  const myEntries = myEntriesData?.data?.data?.content ?? []
  const orders = ordersData?.data?.data?.content ?? []
  const coupons = couponsData?.data?.data?.content ?? []
  const displayName = (profile?.nickname ?? profile?.username ?? profile?.email ?? 'USER').toString().toUpperCase()

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">ACCOUNT</p>
        <h1 className="text-3xl font-black tracking-tight">MY PAGE</h1>
      </div>

      {/* Profile card */}
      <div className="mb-8 bg-black p-8 relative overflow-hidden">
        <div className="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&q=60')] bg-cover opacity-10" />
        <div className="relative flex items-center justify-between">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-white/40 mb-1">MEMBER</p>
            <p className="text-2xl font-black tracking-tight text-white">{displayName}</p>
            <p className="text-sm text-white/40 mt-1">{profile?.email}</p>
          </div>
          <button
            onClick={logout}
            className="flex items-center gap-1.5 text-[10px] font-bold tracking-widest text-white/30 hover:text-red-400 transition-colors"
          >
            <LogOut size={14} />LOGOUT
          </button>
        </div>
        <div className="relative mt-6 grid grid-cols-3 gap-4">
          {[
            { label: 'RAFFLES', value: myEntries.length },
            { label: 'COUPONS', value: coupons.length },
            { label: 'ORDERS', value: orders.length },
          ].map(({ label, value }) => (
            <div key={label} className="border border-white/10 p-3 text-center">
              <p className="text-2xl font-black text-white">{value}</p>
              <p className="text-[10px] text-white/30 tracking-wider mt-1">{label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Quick menu */}
      <div className="mb-8 divide-y divide-gray-100 border border-gray-100">
        <Link to="/orders" className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
          <span className="text-xs font-black tracking-wider">ORDERS</span>
          <ChevronRight size={16} className="text-gray-300" />
        </Link>
        <Link to="/raffles" className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
          <span className="text-xs font-black tracking-wider">BROWSE RAFFLES</span>
          <ChevronRight size={16} className="text-gray-300" />
        </Link>
        <Link to="/drops" className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
          <span className="text-xs font-black tracking-wider">BROWSE DROPS</span>
          <ChevronRight size={16} className="text-gray-300" />
        </Link>
      </div>

      {/* Raffle history */}
      <div className="mb-8">
        <p className="text-xs font-black tracking-widest text-gray-900 mb-4">RAFFLE HISTORY</p>
        {myEntries.length === 0 ? (
          <div className="border border-gray-100 py-10 text-center">
            <p className="text-xs text-gray-300 tracking-wider">NO ENTRIES YET</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100 border border-gray-100">
            {myEntries.slice(0, 5).map((entry: any) => (
              <Link key={entry.entryId} to={`/raffles/${entry.raffleId}`} className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
                <div>
                  <p className="text-xs font-bold text-gray-900 truncate max-w-[200px]">{entry.raffleName ?? entry.raffleId}</p>
                  <p className="text-[10px] text-gray-400 mt-0.5">{entry.enteredAt ? formatDate(entry.enteredAt) : ''}</p>
                </div>
                <span className={`text-[10px] font-black tracking-wider px-2 py-1 ${
                  entry.result === 'WIN' ? 'text-yellow-600 bg-yellow-50' : 'text-green-600 bg-green-50'
                }`}>
                  {entry.result === 'WIN' ? 'WIN' : 'ENTERED'}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* Recent Orders */}
      {orders.length > 0 && (
        <div className="mb-8">
          <p className="text-xs font-black tracking-widest text-gray-900 mb-4">RECENT ORDERS</p>
          <div className="divide-y divide-gray-100 border border-gray-100">
            {orders.slice(0, 3).map((order: any) => (
              <div key={order.orderId} className="flex items-center justify-between px-5 py-4">
                <div>
                  <p className="text-xs font-bold text-gray-900 truncate max-w-[200px]">{order.productName ?? order.orderId}</p>
                  {order.createdAt && <p className="text-[10px] text-gray-400 mt-0.5">{formatDate(order.createdAt)}</p>}
                </div>
                <span className={`text-[10px] font-black tracking-wider px-2 py-1 ${
                  order.status === 'COMPLETED' ? 'text-green-600 bg-green-50'
                  : order.status === 'CANCELLED' ? 'text-red-400 bg-red-50'
                  : 'text-gray-500 bg-gray-50'
                }`}>
                  {order.status}
                </span>
              </div>
            ))}
          </div>
          {orders.length > 3 && (
            <Link to="/orders" className="mt-3 block text-center text-xs font-bold text-gray-400 hover:text-black transition-colors">
              전체 주문 보기 ({orders.length}) →
            </Link>
          )}
        </div>
      )}

      {/* Coupons */}
      {coupons.length > 0 && (
        <div>
          <p className="text-xs font-black tracking-widest text-gray-900 mb-4">COUPONS</p>
          <div className="space-y-3">
            {coupons.map((coupon: any) => (
              <div key={coupon.couponId} className="flex items-center justify-between border border-dashed border-gray-200 px-5 py-4">
                <div>
                  <p className="text-xs font-bold text-gray-900">{coupon.name}</p>
                  {coupon.discountAmount && <p className="text-[10px] text-gray-400 mt-0.5">{coupon.discountAmount.toLocaleString()}원 할인</p>}
                  {coupon.discountRate && <p className="text-[10px] text-gray-400 mt-0.5">{coupon.discountRate}% 할인</p>}
                  {coupon.expiresAt && <p className="text-[10px] text-gray-300 mt-0.5">~{formatDate(coupon.expiresAt)}</p>}
                </div>
                <span className={`text-[10px] font-black tracking-wider px-2 py-1 ${coupon.used ? 'text-gray-300 bg-gray-50' : 'text-red-500 bg-red-50'}`}>
                  {coupon.used ? 'USED' : 'VALID'}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
