import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminPaymentsApi } from '../../api/admin'
import { formatPrice, formatDate } from '../../lib/utils'
import { CreditCard, ChevronDown } from 'lucide-react'

const STATUS_COLOR: Record<string, string> = {
  APPROVED: 'text-green-400 bg-green-400/10',
  COMPLETED: 'text-green-400 bg-green-400/10',
  CANCELLED: 'text-red-400 bg-red-400/10',
  PENDING: 'text-yellow-400 bg-yellow-400/10',
  FAILED: 'text-white/30 bg-white/5',
}

const STATUSES = ['ALL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'PENDING', 'FAILED']
const SALES_TYPES = ['ALL', 'DROP', 'RAFFLE']

export function AdminPaymentsPage() {
  const [status, setStatus] = useState('ALL')
  const [salesType, setSalesType] = useState('ALL')
  const [page, setPage] = useState(0)

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
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">결제 내역</h1>
          <p className="text-xs text-white/30 mt-1">
            {totalElements > 0 ? `총 ${totalElements}건` : '조회 결과 없음'}
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
              <tr key={p.paymentId} className="border-b border-white/5 hover:bg-white/5 transition-colors">
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
