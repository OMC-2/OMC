import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ordersApi } from '../../api/orders'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice, formatDate } from '../../lib/utils'
import { ShoppingCart, ChevronRight } from 'lucide-react'

const STATUS_COLOR: Record<string, string> = {
  COMPLETED: 'text-green-600 bg-green-50',
  CANCELLED: 'text-red-400 bg-red-50',
  PENDING: 'text-yellow-600 bg-yellow-50',
}

export function OrdersPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['orders'],
    queryFn: () => ordersApi.getMyOrders(),
  })

  const orders = data?.data?.data?.content ?? []

  if (isLoading) return <Spinner className="py-20" />

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">ACCOUNT</p>
        <h1 className="text-3xl font-black tracking-tight">ORDERS</h1>
        {orders.length > 0 && <p className="mt-1 text-sm text-gray-400">{orders.length}건의 주문</p>}
      </div>

      {orders.length === 0 ? (
        <div className="flex flex-col items-center py-32 text-gray-300">
          <ShoppingCart size={40} className="mb-4" />
          <p className="text-xs font-bold tracking-widest">NO ORDERS YET</p>
          <Link to="/drops" className="mt-4 text-xs font-black tracking-widest text-black hover:text-red-500 transition-colors underline">
            BROWSE DROPS →
          </Link>
        </div>
      ) : (
        <div className="divide-y divide-gray-100 border border-gray-100">
          {orders.map((order: any) => (
            <Link key={order.orderId} to={`/orders/${order.orderId}`} className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors group">
              <div className="min-w-0 flex-1">
                <p className="text-xs font-black text-gray-900 truncate">{order.productName ?? '주문'}</p>
                <p className="text-[10px] font-mono text-gray-400 mt-0.5 truncate">{order.orderId}</p>
                {order.createdAt && <p className="text-[10px] text-gray-300 mt-0.5">{formatDate(order.createdAt)}</p>}
              </div>
              <div className="ml-4 flex items-center gap-3 shrink-0">
                {(order.totalAmount || order.finalAmount) && (
                  <p className="text-xs font-black text-gray-900">{formatPrice(order.totalAmount ?? order.finalAmount)}</p>
                )}
                <span className={`px-2 py-0.5 text-[10px] font-black tracking-wider ${STATUS_COLOR[order.status] ?? 'text-gray-400 bg-gray-50'}`}>
                  {order.status}
                </span>
                <ChevronRight size={14} className="text-gray-200 group-hover:text-gray-400 transition-colors" />
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
