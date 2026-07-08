import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminPaymentsApi } from '../../api/admin'
import { formatPrice, formatDate } from '../../lib/utils'
import { CreditCard, ChevronDown, X } from 'lucide-react'

const STATUS_COLOR: Record<string, string> = {
  APPROVED: 'text-green-400 bg-green-400/10',
  COMPLETED: 'text-green-400 bg-green-400/10',
  CANCELLED: 'text-red-400 bg-red-400/10',
  PENDING: 'text-yellow-400 bg-yellow-400/10',
  FAILED: 'text-white/30 bg-white/5',
}

const STATUSES = ['ALL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'PENDING', 'FAILED']
const SALES_TYPES = ['ALL', 'DROP', 'RAFFLE']

function DetailRow({ label, value }: { label: string; value?: string | number | null }) {
  if (value == null || value === '') return null
  return (
    <div className="flex justify-between gap-4 py-2 border-b border-white/5">
      <span className="text-[10px] font-bold tracking-widest text-white/30 flex-shrink-0">{label}</span>
      <span className="text-xs text-white/70 text-right break-all font-mono">{String(value)}</span>
    </div>
  )
}

function PaymentDetailModal({ payment, onClose }: { payment: any; onClose: () => void }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-lg max-h-[85vh] overflow-y-auto bg-[#111] border border-white/10 rounded-lg shadow-2xl"
        onClick={e => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="sticky top-0 bg-[#111] border-b border-white/10 px-6 py-4 flex items-center justify-between">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-white/30">PAYMENT DETAIL</p>
            <p className="text-sm font-black text-white mt-0.5 font-mono">
              {payment.paymentId?.slice(0, 8)}…
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-white/30 hover:text-white transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* 상태 배지 */}
        <div className="px-6 pt-4 pb-2 flex items-center gap-2">
          <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${STATUS_COLOR[payment.paymentStatus] ?? 'text-white/30 bg-white/5'}`}>
            {payment.paymentStatus}
          </span>
          <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${payment.salesType === 'DROP' ? 'bg-blue-500/20 text-blue-400' : 'bg-purple-500/20 text-purple-400'}`}>
            {payment.salesType}
          </span>
        </div>

        {/* 금액 요약 */}
        <div className="px-6 py-3 grid grid-cols-3 gap-px bg-white/5 mx-6 mb-4">
          <div className="bg-[#111] p-3 text-center">
            <p className="text-[9px] text-white/30 font-bold tracking-widest mb-1">원가</p>
            <p className="text-sm font-black text-white/70">{payment.originalAmount != null ? formatPrice(payment.originalAmount) : '-'}</p>
          </div>
          <div className="bg-[#111] p-3 text-center">
            <p className="text-[9px] text-white/30 font-bold tracking-widest mb-1">할인</p>
            <p className="text-sm font-black text-red-400">{payment.discountAmount > 0 ? `-${formatPrice(payment.discountAmount)}` : '-'}</p>
          </div>
          <div className="bg-[#111] p-3 text-center">
            <p className="text-[9px] text-white/30 font-bold tracking-widest mb-1">최종</p>
            <p className="text-sm font-black text-white">{payment.finalAmount != null ? formatPrice(payment.finalAmount) : '-'}</p>
          </div>
        </div>

        {/* 상세 필드 */}
        <div className="px-6 pb-6 space-y-0">
          <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mb-2">ID 정보</p>
          <DetailRow label="PAYMENT ID" value={payment.paymentId} />
          <DetailRow label="ORDER ID" value={payment.orderId} />
          <DetailRow label="USER ID" value={payment.userId} />
          <DetailRow label="PRODUCT ID" value={payment.productId} />
          <DetailRow label="DROP ID" value={payment.dropId} />
          <DetailRow label="RAFFLE ID" value={payment.raffleId} />
          <DetailRow label="ENTRY ID" value={payment.entryId} />
          <DetailRow label="COUPON ID" value={payment.couponId} />

          <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mt-4 mb-2">결제 정보</p>
          <DetailRow label="PROVIDER" value={payment.provider} />
          <DetailRow label="METHOD" value={payment.paymentMethod} />
          <DetailRow label="PROVIDER PAYMENT ID" value={payment.providerPaymentId} />
          <DetailRow label="PROVIDER CANCEL ID" value={payment.providerCancellationId} />

          {(payment.failureCode || payment.failureMessage) && (
            <>
              <p className="text-[9px] font-bold tracking-[0.3em] text-red-400/50 mt-4 mb-2">실패 정보</p>
              <DetailRow label="FAILURE CODE" value={payment.failureCode} />
              <DetailRow label="FAILURE MESSAGE" value={payment.failureMessage} />
            </>
          )}

          {(payment.cancellationCode || payment.cancelledMessage) && (
            <>
              <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mt-4 mb-2">취소 정보</p>
              <DetailRow label="CANCEL CODE" value={payment.cancellationCode} />
              <DetailRow label="CANCEL MESSAGE" value={payment.cancelledMessage} />
            </>
          )}

          <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mt-4 mb-2">일시</p>
          <DetailRow label="REQUESTED" value={payment.requestedAt ? formatDate(payment.requestedAt) : null} />
          <DetailRow label="APPROVED" value={payment.approvedAt ? formatDate(payment.approvedAt) : null} />
          <DetailRow label="FAILED" value={payment.failedAt ? formatDate(payment.failedAt) : null} />
          <DetailRow label="CANCELLED" value={payment.canceledAt ? formatDate(payment.canceledAt) : null} />
        </div>
      </div>
    </div>
  )
}

export function AdminPaymentsPage() {
  const [status, setStatus] = useState('ALL')
  const [salesType, setSalesType] = useState('ALL')
  const [page, setPage] = useState(0)
  const [selectedPayment, setSelectedPayment] = useState<any>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-payments', status, salesType, page],
    queryFn: () => adminPaymentsApi.getAll(page, 20, {
      ...(status !== 'ALL' && { status }),
      ...(salesType !== 'ALL' && { salesType }),
    }),
  })

  const payments = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.totalPages ?? 0
  const totalElements = data?.data?.data?.totalElements ?? 0

  return (
    <div className="p-8">
      {selectedPayment && (
        <PaymentDetailModal payment={selectedPayment} onClose={() => setSelectedPayment(null)} />
      )}

      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">결제 내역</h1>
          <p className="text-xs text-white/30 mt-1">
            {totalElements > 0 ? `총 ${totalElements}건 — 행 클릭 시 상세 보기` : '조회 결과 없음'}
          </p>
        </div>
        <CreditCard size={22} className="text-white/20" />
      </div>

      {/* 필터 */}
      <div className="mb-5 flex items-center gap-3">
        <div className="relative">
          <select
            value={status}
            onChange={e => { setStatus(e.target.value); setPage(0) }}
            className="appearance-none bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white/70 pr-7 focus:outline-none focus:border-white/30 cursor-pointer"
          >
            {STATUSES.map(s => <option key={s} value={s}>{s === 'ALL' ? '전체 상태' : s}</option>)}
          </select>
          <ChevronDown size={12} className="absolute right-2 top-1/2 -translate-y-1/2 text-white/30 pointer-events-none" />
        </div>
        <div className="relative">
          <select
            value={salesType}
            onChange={e => { setSalesType(e.target.value); setPage(0) }}
            className="appearance-none bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white/70 pr-7 focus:outline-none focus:border-white/30 cursor-pointer"
          >
            {SALES_TYPES.map(t => <option key={t} value={t}>{t === 'ALL' ? '전체 유형' : t}</option>)}
          </select>
          <ChevronDown size={12} className="absolute right-2 top-1/2 -translate-y-1/2 text-white/30 pointer-events-none" />
        </div>
      </div>

      {/* 테이블 */}
      <div className="rounded-lg border border-white/5 overflow-hidden overflow-x-auto">
        <table className="w-full text-xs min-w-[800px]">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['결제 ID', '유형', '상품 ID', '원가', '할인', '최종', '결제 수단', '상태', '요청일시'].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30 whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={9} className="py-10 text-center text-white/20">로딩 중...</td></tr>
            ) : payments.length === 0 ? (
              <tr><td colSpan={9} className="py-10 text-center text-white/20">결제 내역 없음</td></tr>
            ) : payments.map((p: any) => (
              <tr
                key={p.paymentId}
                onClick={() => setSelectedPayment(p)}
                className="border-b border-white/5 hover:bg-white/5 transition-colors cursor-pointer"
              >
                <td className="px-4 py-3 font-mono text-white/40 text-[10px] max-w-[120px] truncate" title={p.paymentId}>
                  {p.paymentId?.slice(0, 8)}…
                </td>
                <td className="px-4 py-3">
                  <span className={`px-1.5 py-0.5 text-[9px] font-black tracking-wider ${p.salesType === 'DROP' ? 'bg-blue-500/20 text-blue-400' : 'bg-purple-500/20 text-purple-400'}`}>
                    {p.salesType ?? '-'}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-white/30 text-[10px] max-w-[100px] truncate" title={p.productId}>
                  {p.productId?.slice(0, 8) ?? '-'}
                </td>
                <td className="px-4 py-3 text-white/60">{p.originalAmount != null ? formatPrice(p.originalAmount) : '-'}</td>
                <td className="px-4 py-3 text-red-400">{p.discountAmount > 0 ? `-${formatPrice(p.discountAmount)}` : '-'}</td>
                <td className="px-4 py-3 text-white font-bold">{p.finalAmount != null ? formatPrice(p.finalAmount) : '-'}</td>
                <td className="px-4 py-3 text-white/40">{p.paymentMethod ?? '-'}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${STATUS_COLOR[p.paymentStatus] ?? 'text-white/30 bg-white/5'}`}>
                    {p.paymentStatus}
                  </span>
                </td>
                <td className="px-4 py-3 text-white/30 whitespace-nowrap">
                  {p.requestedAt ? formatDate(p.requestedAt) : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="px-3 py-1.5 text-xs font-bold text-white/30 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            ← 이전
          </button>
          <span className="text-xs text-white/30">{page + 1} / {totalPages}</span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage(p => p + 1)}
            className="px-3 py-1.5 text-xs font-bold text-white/30 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            다음 →
          </button>
        </div>
      )}
    </div>
  )
}
