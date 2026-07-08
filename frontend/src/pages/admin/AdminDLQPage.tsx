import { toast } from '../../components/ui/Toast'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminDlqApi } from '../../api/admin'
import { formatDate } from '../../lib/utils'
import { RefreshCw, AlertTriangle, CheckCircle, ChevronDown, X } from 'lucide-react'

export function AdminDLQPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<'FAILED' | 'RESOLVED'>('FAILED')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<any | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-dlq', statusFilter, page],
    queryFn: () => adminDlqApi.getAll(statusFilter, page, 20),
  })

  const messages = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.totalPages ?? 0
  const totalElements = data?.data?.data?.totalElements ?? 0

  const republishMutation = useMutation({
    mutationFn: (dlqId: string) => adminDlqApi.republish(dlqId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-dlq'] })
      setSelected(null)
      toast.success('재발행 완료. 상태가 RESOLVED로 전환됩니다.')
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '재발행 실패'),
  })

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">DLQ 관리</h1>
          <p className="text-xs text-white/30 mt-1">
            Dead Letter Queue — 실패한 메시지를 확인하고 재발행합니다
          </p>
        </div>
        <AlertTriangle size={22} className="text-yellow-400/50" />
      </div>

      {/* 필터 */}
      <div className="mb-5 flex items-center gap-3">
        <div className="relative">
          <select
            value={statusFilter}
            onChange={e => { setStatusFilter(e.target.value as 'FAILED' | 'RESOLVED'); setPage(0) }}
            className="appearance-none bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white/70 pr-7 focus:outline-none focus:border-white/30 cursor-pointer"
          >
            <option value="FAILED">FAILED</option>
            <option value="RESOLVED">RESOLVED</option>
          </select>
          <ChevronDown size={12} className="absolute right-2 top-1/2 -translate-y-1/2 text-white/30 pointer-events-none" />
        </div>
        <span className="text-xs text-white/20">{totalElements > 0 ? `${totalElements}건` : '없음'}</span>
      </div>

      {/* 테이블 */}
      <div className="rounded-lg border border-white/5 overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['토픽', '오류 클래스', '오류 메시지', '파티션', '재발행 횟수', '실패 일시', '상태', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30 whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={8} className="py-10 text-center text-white/20">로딩 중...</td></tr>
            ) : messages.length === 0 ? (
              <tr>
                <td colSpan={8} className="py-16 text-center">
                  <CheckCircle size={28} className="mx-auto mb-3 text-green-400/40" />
                  <p className="text-xs text-white/20">
                    {statusFilter === 'FAILED' ? '처리 실패 메시지 없음' : '재발행된 메시지 없음'}
                  </p>
                </td>
              </tr>
            ) : messages.map((m: any) => (
              <tr
                key={m.dlqId}
                className="border-b border-white/5 hover:bg-white/5 transition-colors cursor-pointer"
                onClick={() => setSelected(m)}
              >
                <td className="px-4 py-3 text-white/70 font-mono text-[10px] max-w-[160px] truncate" title={m.topic}>
                  {m.topic}
                </td>
                <td className="px-4 py-3 text-red-400/70 text-[10px] max-w-[140px] truncate" title={m.errorClass}>
                  {m.errorClass?.split('.').pop() ?? '-'}
                </td>
                <td className="px-4 py-3 text-white/40 max-w-[200px] truncate" title={m.errorMessage}>
                  {m.errorMessage ?? '-'}
                </td>
                <td className="px-4 py-3 text-white/30 text-center">{m.partition ?? '-'}</td>
                <td className="px-4 py-3 text-center">
                  <span className={`font-bold ${m.republishCount > 0 ? 'text-yellow-400' : 'text-white/30'}`}>
                    {m.republishCount}
                  </span>
                </td>
                <td className="px-4 py-3 text-white/30 whitespace-nowrap">
                  {m.failedAt ? formatDate(m.failedAt) : '-'}
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${
                    m.status === 'FAILED' ? 'bg-red-400/10 text-red-400' : 'bg-green-400/10 text-green-400'
                  }`}>
                    {m.status}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {m.status === 'FAILED' && (
                    <button
                      onClick={e => { e.stopPropagation(); republishMutation.mutate(m.dlqId) }}
                      disabled={republishMutation.isPending}
                      className="flex items-center gap-1 px-2 py-1 text-[9px] font-black tracking-wider bg-yellow-400/10 text-yellow-400 hover:bg-yellow-400/20 disabled:opacity-40 transition-colors"
                    >
                      <RefreshCw size={10} />
                      재발행
                    </button>
                  )}
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

      {/* 상세 모달 (payload 확인용) */}
      {selected && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-2xl rounded-lg border border-white/10 bg-gray-900 p-6 max-h-[85vh] flex flex-col">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-sm font-black tracking-wider">DLQ 메시지 상세</h2>
                <p className="text-[10px] text-white/30 mt-0.5 font-mono">{selected.dlqId}</p>
              </div>
              <button onClick={() => setSelected(null)}>
                <X size={18} className="text-white/40 hover:text-white" />
              </button>
            </div>

            <div className="space-y-3 overflow-y-auto flex-1 text-xs">
              {[
                ['토픽', selected.topic],
                ['메시지 키', selected.messageKey],
                ['오류 클래스', selected.errorClass],
                ['오류 메시지', selected.errorMessage],
                ['파티션', selected.partition],
                ['오프셋', selected.offset],
                ['재발행 횟수', selected.republishCount],
                ['실패 일시', selected.failedAt ? formatDate(selected.failedAt) : '-'],
                ['해소 일시', selected.resolvedAt ? formatDate(selected.resolvedAt) : '-'],
              ].map(([label, value]) => (
                <div key={label} className="flex gap-4">
                  <span className="w-28 shrink-0 text-white/30 font-bold">{label}</span>
                  <span className="text-white/70 font-mono break-all">{value ?? '-'}</span>
                </div>
              ))}

              <div>
                <p className="text-white/30 font-bold mb-2">Payload</p>
                <pre className="bg-black/40 border border-white/5 rounded p-3 text-[10px] text-white/50 overflow-x-auto whitespace-pre-wrap break-all max-h-48">
                  {(() => { try { return JSON.stringify(JSON.parse(selected.payload), null, 2) } catch { return selected.payload } })()}
                </pre>
              </div>
            </div>

            <div className="mt-5 flex gap-3">
              <button
                onClick={() => setSelected(null)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors"
              >
                닫기
              </button>
              {selected.status === 'FAILED' && (
                <button
                  onClick={() => republishMutation.mutate(selected.dlqId)}
                  disabled={republishMutation.isPending}
                  className="flex items-center justify-center gap-2 flex-1 bg-yellow-400/10 border border-yellow-400/20 py-2.5 text-xs font-black text-yellow-400 hover:bg-yellow-400/20 disabled:opacity-40 transition-colors"
                >
                  <RefreshCw size={13} />
                  {republishMutation.isPending ? '재발행 중...' : '재발행'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
