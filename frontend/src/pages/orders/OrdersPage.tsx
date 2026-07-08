import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ordersApi } from '../../api/orders'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice, formatDate } from '../../lib/utils'
import { ShoppingCart, ChevronRight, X, XCircle } from 'lucide-react'

const STATUS_COLOR: Record<string, string> = {
  APPROVED: 'text-green-600 bg-green-50',
  COMPLETED: 'text-green-600 bg-green-50',
  CANCELLED: 'text-red-400 bg-red-50',
  PENDING: 'text-yellow-600 bg-yellow-50',
  FAILED: 'text-gray-400 bg-gray-50',
}

const CANCELLABLE = ['APPROVED', 'COMPLETED']

function CancelModal({ payment, onClose }: { payment: any; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState('')

  const cancelMutation = useMutation({
    mutationFn: () => ordersApi.cancelPayment(payment.paymentId, 'USER_CANCEL', reason || '사용자 취소'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['orders'] })
      onClose()
      alert('결제가 취소되었습니다.')
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '취소 실패'),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-sm border border-gray-100 bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-black tracking-wider">결제 취소</h2>
          <button onClick={onClose}><X size={16} className="text-gray-300 hover:text-black" /></button>
        </div>

        <div className="mb-4 space-y-1 text-xs text-gray-500">
          <p><span className="font-bold text-gray-700">결제 ID</span> <span className="font-mono">{payment.paymentId?.slice(0, 16)}...</span></p>
          <p><span className="font-bold text-gray-700">금액</span> {payment.finalAmount != null ? formatPrice(payment.finalAmount) : '-'}</p>
          <p><span className="font-bold text-gray-700">유형</span> {payment.salesType}</p>
        </div>

        <div className="mb-5">
          <label className="block text-[10px] font-bold tracking-widest text-gray-400 mb-1">취소 사유</label>
          <input
            type="text"
            placeholder="취소 사유를 입력하세요 (선택)"
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full border border-gray-100 px-3 py-2 text-xs placeholder:text-gray-300 focus:outline-none focus:border-gray-300"
          />
        </div>

        <div className="flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 border border-gray-100 py-2.5 text-xs font-bold text-gray-400 hover:text-black transition-colors"
          >
            닫기
          </button>
          <button
            onClick={() => cancelMutation.mutate()}
            disabled={cancelMutation.isPending}
            className="flex-1 bg-black py-2.5 text-xs font-black text-white hover:bg-red-600 disabled:opacity-50 transition-colors"
          >
            {cancelMutation.isPending ? '취소 중...' : '취소 확인'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function OrdersPage() {
  const [cancelTarget, setCancelTarget] = useState<any | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['orders'],
    queryFn: () => ordersApi.getMyOrders(),
  })

  const payments = data?.data?.data?.content ?? []

  if (isLoading) return <Spinner className="py-20" />

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">ACCOUNT</p>
        <h1 className="text-3xl font-black tracking-tight">ORDERS</h1>
        {payments.length > 0 && <p className="mt-1 text-sm text-gray-400">{payments.length}건의 결제 내역</p>}
      </div>

      {payments.length === 0 ? (
        <div className="flex flex-col items-center py-32 text-gray-300">
          <ShoppingCart size={40} className="mb-4" />
          <p className="text-xs font-bold tracking-widest">NO ORDERS YET</p>
          <Link to="/drops" className="mt-4 text-xs font-black tracking-widest text-black hover:text-red-500 transition-colors underline">
            BROWSE DROPS -&gt;
          </Link>
        </div>
      ) : (
        <div className="divide-y divide-gray-100 border border-gray-100">
          {payments.map((p: any) => {
            const href = p.orderId ? `/orders/${p.orderId}` : '#'
            const canCancel = CANCELLABLE.includes(p.paymentStatus)

            return (
              <div key={p.paymentId} className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors group">
                <Link to={href} className="min-w-0 flex-1 flex items-center gap-0">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-xs font-black text-gray-900 truncate">
                        {p.salesType ?? '주문'}
                      </p>
                      {p.salesType && (
                        <span className="shrink-0 text-[9px] font-black px-1.5 py-0.5 bg-gray-100 text-gray-400 tracking-wider">
                          {p.salesType}
                        </span>
                      )}
                    </div>
                    <p className="text-[10px] font-mono text-gray-400 mt-0.5 truncate">{p.paymentId}</p>
                    {p.requestedAt && <p className="text-[10px] text-gray-300 mt-0.5">{formatDate(p.requestedAt)}</p>}
                  </div>
                  <div className="ml-4 flex items-center gap-3 shrink-0">
                    {p.finalAmount != null && (
                      <p className="text-xs font-black text-gray-900">{formatPrice(p.finalAmount)}</p>
                    )}
                    <span className={`px-2 py-0.5 text-[10px] font-black tracking-wider ${STATUS_COLOR[p.paymentStatus] ?? 'text-gray-400 bg-gray-50'}`}>
                      {p.paymentStatus}
                    </span>
                    <ChevronRight size={14} className="text-gray-200 group-hover:text-gray-400 transition-colors" />
                  </div>
                </Link>

                {canCancel && (
                  <button
                    onClick={() => setCancelTarget(p)}
                    className="ml-3 shrink-0 flex items-center gap-1 px-2 py-1 text-[9px] font-black tracking-wider text-gray-300 hover:text-red-500 hover:bg-red-50 border border-transparent hover:border-red-100 transition-colors"
                    title="결제 취소"
                  >
                    <XCircle size={11} />
                    취소
                  </button>
                )}
              </div>
            )
          })}
        </div>
      )}

      {cancelTarget && (
        <CancelModal payment={cancelTarget} onClose={() => setCancelTarget(null)} />
      )}
    </div>
  )
}
