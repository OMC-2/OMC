import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { ordersApi } from '../../api/orders'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice, formatDate } from '../../lib/utils'
import { ArrowLeft, Package, RotateCcw } from 'lucide-react'
import { toast } from '../../components/ui/Toast'

const STATUS_COLOR: Record<string, string> = {
  PENDING_PAYMENT: 'text-yellow-600 bg-yellow-50',
  PAID:            'text-blue-600 bg-blue-50',
  CONFIRMED:       'text-green-600 bg-green-50',
  SHIPPING:        'text-indigo-600 bg-indigo-50',
  DELIVERED:       'text-green-700 bg-green-100',
  REFUND_REQUESTED:'text-orange-600 bg-orange-50',
  REFUNDED:        'text-gray-500 bg-gray-50',
  CANCELLED:       'text-red-400 bg-red-50',
}

const STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT:  '결제 대기',
  PAID:             '결제 완료',
  CONFIRMED:        '구매 확정',
  SHIPPING:         '배송 중',
  DELIVERED:        '배송 완료',
  REFUND_REQUESTED: '환불 요청',
  REFUNDED:         '환불 완료',
  CANCELLED:        '취소됨',
}

// 결제 처리 중인 상태 — 폴링 필요
const PROCESSING_STATUSES = new Set(['PENDING_PAYMENT', 'PAID'])

export function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const qc = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => ordersApi.getById(orderId!),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.data?.data?.status
      return status && PROCESSING_STATUSES.has(status) ? 5000 : false
    },
  })
  const order = data?.data?.data

  const refundMutation = useMutation({
    mutationFn: () => ordersApi.refund(orderId!),
    onSuccess: () => {
      toast.success('환불 요청이 접수되었습니다.')
      qc.invalidateQueries({ queryKey: ['order', orderId] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '환불 요청 실패'),
  })

  if (isLoading) return <Spinner className="py-20" />
  if (!order) return <p className="text-center py-20 text-xs tracking-widest text-gray-400">주문을 찾을 수 없습니다</p>

  const statusClass = STATUS_COLOR[order.status] ?? 'text-gray-500 bg-gray-50'
  const statusLabel = STATUS_LABEL[order.status] ?? order.status
  const canRefund = order.status === 'CONFIRMED'
  const isProcessing = PROCESSING_STATUSES.has(order.status)

  return (
    <div className="mx-auto max-w-2xl">
      <Link to="/orders" className="mb-8 inline-flex items-center gap-2 text-xs font-bold tracking-wider text-gray-400 hover:text-black transition-colors">
        <ArrowLeft size={14} />주문 목록
      </Link>

      <div className="mb-6 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">ORDER DETAIL</p>
        <h1 className="text-2xl font-black tracking-tight">주문 상세</h1>
      </div>

      {isProcessing && (
        <div className="mb-4 flex items-center gap-2 bg-yellow-50 border border-yellow-100 px-4 py-3 text-xs text-yellow-700">
          <Spinner className="h-3 w-3" />
          결제 처리 중입니다. 잠시만 기다려 주세요...
        </div>
      )}

      <div className="space-y-4">
        <div className="flex items-center justify-between border border-gray-100 px-5 py-4">
          <div>
            <p className="text-[10px] font-bold tracking-widest text-gray-400 mb-0.5">ORDER ID</p>
            <p className="text-xs font-mono text-gray-600">{order.orderId}</p>
          </div>
          <span className={`px-3 py-1 text-[10px] font-black tracking-wider ${statusClass}`}>
            {statusLabel}
          </span>
        </div>

        <div className="border border-gray-100 px-5 py-4">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center bg-gray-50">
              <Package size={20} className="text-gray-300" />
            </div>
            <div>
              <p className="text-[10px] font-bold tracking-widest text-gray-400 mb-0.5">
                {order.orderType ?? '주문'}
              </p>
              <p className="text-xs font-mono text-gray-600">{order.productId}</p>
              {order.quantity > 1 && (
                <p className="text-[10px] text-gray-400 mt-0.5">수량 {order.quantity}개</p>
              )}
            </div>
          </div>
        </div>

        {order.finalAmount != null && (
          <div className="border border-gray-100 px-5 py-4 space-y-2">
            <p className="text-[10px] font-bold tracking-widest text-gray-400 mb-2">결제 정보</p>
            {order.originalAmount != null && order.originalAmount !== order.finalAmount && (
              <div className="flex justify-between text-xs">
                <span className="text-gray-400">상품 금액</span>
                <span>{formatPrice(order.originalAmount)}</span>
              </div>
            )}
            {order.discountAmount > 0 && (
              <div className="flex justify-between text-xs text-red-500">
                <span>할인</span>
                <span>-{formatPrice(order.discountAmount)}</span>
              </div>
            )}
            <div className="flex justify-between text-sm font-black border-t border-gray-100 pt-2 mt-2">
              <span>최종 결제</span>
              <span>{formatPrice(order.finalAmount)}</span>
            </div>
          </div>
        )}

        <div className="border border-gray-100 px-5 py-4 space-y-2">
          {order.createdAt && (
            <div className="flex justify-between text-xs">
              <span className="text-gray-400 font-bold tracking-wide">주문 일시</span>
              <span>{formatDate(order.createdAt)}</span>
            </div>
          )}
          {order.expiresAt && (
            <div className="flex justify-between text-xs">
              <span className="text-gray-400 font-bold tracking-wide">만료 일시</span>
              <span>{formatDate(order.expiresAt)}</span>
            </div>
          )}
        </div>

        {canRefund && (
          <button
            onClick={() => { if (window.confirm('환불을 요청하시겠습니까?')) refundMutation.mutate() }}
            disabled={refundMutation.isPending}
            className="flex w-full items-center justify-center gap-2 border border-gray-200 py-3 text-xs font-bold text-gray-400 hover:border-red-300 hover:text-red-500 disabled:opacity-50 transition-colors"
          >
            <RotateCcw size={13} />
            {refundMutation.isPending ? '처리 중...' : '환불 요청'}
          </button>
        )}
      </div>
    </div>
  )
}
