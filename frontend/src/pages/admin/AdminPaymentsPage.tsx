import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminPaymentsApi } from '../../api/admin'
import { formatPrice, formatDate } from '../../lib/utils'
import { CreditCard, X, ChevronLeft, ChevronRight } from 'lucide-react'

const STATUS_COLOR: Record<string, string> = {
  PAID:            'text-green-400 bg-green-400/10',
  APPROVED:        'text-green-400 bg-green-400/10',
  COMPLETED:       'text-green-400 bg-green-400/10',
  CANCELLED:       'text-red-400 bg-red-400/10',
  CANCELED:        'text-red-400 bg-red-400/10',
  PENDING:         'text-yellow-400 bg-yellow-400/10',
  READY:           'text-yellow-400 bg-yellow-400/10',
  CONFIRMING:      'text-blue-400 bg-blue-400/10',
  FAILED:          'text-white/30 bg-white/5',
  CONFIRM_UNKNOWN: 'text-orange-400 bg-orange-400/10',
  CANCEL_UNKNOWN:  'text-orange-300 bg-orange-300/10',
  RECOVERY_FAILED: 'text-red-500 bg-red-500/10',
}

const STATUSES = ['ALL', 'PAID', 'READY', 'CONFIRMING', 'FAILED', 'CANCELLED', 'CONFIRM_UNKNOWN', 'CANCEL_UNKNOWN']
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4" onClick={onClose}>
      <div className="relative w-full max-w-lg max-h-[85vh] overflow-y-auto bg-[#111] border border-white/10 rounded-lg shadow-2xl" onClick={e => e.stopPropagation()}>
        <div className="sticky top-0 bg-[#111] border-b border-white/10 px-6 py-4 flex items-center justify-between">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-white/30">PAYMENT DETAIL</p>
            <p className="text-sm font-black text-white mt-0.5 font-mono">{payment.paymentId?.slice(0, 8)}</p>
          </div>
          <button onClick={onClose} className="text-white/30 hover:text-white transition-colors">
            <X size={18} />
          </button>
        </div>

        <div className="px-6 pt-4 pb-2 flex items-center gap-2">
          <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${STATUS_COLOR[payment.paymentStatus] ?? 'text-white/30 bg-white/5'}`}>
            {payment.paymentStatus}
          </span>
          <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${payment.salesType === 'DROP' ? 'bg-blue-500/20 text-blue-400' : 'bg-purple-500/20 text-purple-400'}`}>
            {payment.salesType}
          </span>
        </div>

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

        <div className="px-6 pb-6 space-y-0">
          <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mb-2">ID 정보</p>
          <DetailRow label="PAYMENT ID"  value={payment.paymentId} />
          <DetailRow label="ORDER ID"    value={payment.orderId} />
          <DetailRow label="USER ID"     value={payment.userId} />
          <DetailRow label="PRODUCT ID"  value={payment.productId} />
          <DetailRow label="DROP ID"     value={payment.dropId} />
          <DetailRow label="RAFFLE ID"   value={payment.raffleId} />
          <DetailRow label="ENTRY ID"    value={payment.entryId} />
          <DetailRow label="COUPON ID"   value={payment.couponId} />
          <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mt-4 mb-2">결제 수단</p>
          <DetailRow label="PROVIDER"            value={payment.provider} />
          <DetailRow label="METHOD"              value={payment.paymentMethod} />
          <DetailRow label="PROVIDER PAYMENT ID" value={payment.providerPaymentId} />
          <DetailRow label="PROVIDER CANCEL ID"  value={payment.providerCancellationId} />
          {(payment.failureCode || payment.failureMessage) && (
            <>
              <p className="text-[9px] font-bold tracking-[0.3em] text-red-400/50 mt-4 mb-2">실패 정보</p>
              <DetailRow label="FAILURE CODE"    value={payment.failureCode} />
              <DetailRow label="FAILURE MESSAGE" value={payment.failureMessage} />
            </>
          )}
          {(payment.cancellationCode || payment.cancelledMessage) && (
            <>
              <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mt-4 mb-2">취소 정보</p>
              <DetailRow label="CANCEL CODE"    value={payment.cancellationCode} />
              <DetailRow label="CANCEL MESSAGE" value={payment.cancelledMessage} />
            </>
          )}
          <p className="text-[9px] font-bold tracking-[0.3em] text-white/20 mt-4 mb-2">일시</p>
          <DetailRow label="REQUESTED" value={payment.requestedAt ? formatDate(payment.requestedAt) : null} />
          <DetailRow label="APPROVED"  value={payment.approvedAt  ? formatDate(payment.approvedAt)  : null} />
          <DetailRow label="FAILED"    value={payment.failedAt    ? formatDate(payment.failedAt)    : null} />
          <DetailRow label="CANCELLED" value={payment.canceledAt  ? formatDate(payment.canceledAt)  : null} />
        </div>
      </div>
    </div>
  )
}

export function AdminPaymentsPage() {
  const [status, setStatus]           = useState('ALL')
  const [salesType, setSalesType]     = useState('ALL')
  const [page, setPage]               = useState(0)
  const [selectedPayment, setSelectedPayment] = useState<any>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-payments', status, salesType, page],
    queryFn: () => adminPaymentsApi.getAll(page, 20, {
      ...(status   !== 'ALL' && { paymentStatus: status }),
      ...(salesType !== 'ALL' && { salesType }),
    }),
  })

  const payments   = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.totalPages ?? 1
  const totalElements = data?.data?.data?.totalElements ?? 0

  return (
    <div className="p-8">
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <CreditCard size={20} className="text-green-400" />
          <h1 className="text-xl font-black tracking-tight">결제 내역</h1>
        </div>
        <p className="text-xs text-white/30">전체 {totalElements.toLocaleString()}건</p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-2 mb-6">
        <div className="flex gap-1">
          {STATUSES.map(s => (
            <button
              key={s}
              onClick={() => { setStatus(s); setPage(0) }}
              className={`px-3 py-1.5 text-[10px] font-black tracking-wider transition-colors ${status === s ? 'bg-white text-black' : 'border border-white/10 text-white/40 hover:border-white/30 hover:text-white/70'}`}
            >
              {s}
            </button>
          ))}
        </div>
        <div className="flex gap-1">
          {SALES_TYPES.map(t => (
            <button
              key={t}
              onClick={() => { setSalesType(t); setPage(0) }}
              className={`px-3 py-1.5 text-[10px] font-black tracking-wider transition-colors ${salesType === t ? 'bg-white text-black' : 'border border-white/10 text-white/40 hover:border-white/30 hover:text-white/70'}`}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="py-20 text-center text-xs text-white/20">불러오는 중...</div>
      ) : payments.length === 0 ? (
        <div className="py-20 text-center text-xs text-white/20">결제 내역 없음</div>
      ) : (
        <>
          <div className="border border-white/10 rounded-lg overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-white/10 text-[10px] font-black tracking-widest text-white/30">
                  <th className="px-4 py-3 text-left">PAYMENT ID</th>
                  <th className="px-4 py-3 text-left">TYPE</th>
                  <th className="px-4 py-3 text-right">금액</th>
                  <th className="px-4 py-3 text-center">상태</th>
                  <th className="px-4 py-3 text-left">일시</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {payments.map((p: any) => (
                  <tr
                    key={p.paymentId}
                    onClick={() => setSelectedPayment(p)}
                    className="hover:bg-white/5 cursor-pointer transition-colors"
                  >
                    <td className="px-4 py-3 font-mono text-white/50">{p.paymentId?.slice(0, 8)}...</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${p.salesType === 'DROP' ? 'bg-blue-500/20 text-blue-400' : 'bg-purple-500/20 text-purple-400'}`}>
                        {p.salesType ?? '-'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-black text-white/80">
                      {p.finalAmount != null ? formatPrice(p.finalAmount) : '-'}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${STATUS_COLOR[p.paymentStatus] ?? 'text-white/30 bg-white/5'}`}>
                        {p.paymentStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-white/30 text-[10px]">
                      {p.requestedAt ? formatDate(p.requestedAt) : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 mt-4">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-1.5 border border-white/10 text-white/40 hover:text-white disabled:opacity-20 transition-colors"
              >
                <ChevronLeft size={14} />
              </button>
              <span className="text-xs text-white/40 font-mono">{page + 1} / {totalPages}</span>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="p-1.5 border border-white/10 text-white/40 hover:text-white disabled:opacity-20 transition-colors"
              >
                <ChevronRight size={14} />
              </button>
            </div>
          )}
        </>
      )}

      {selectedPayment && (
        <PaymentDetailModal payment={selectedPayment} onClose={() => setSelectedPayment(null)} />
      )}
    </div>
  )
}
