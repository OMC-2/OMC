import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { adminOutboxApi } from '../../api/admin'
import { RefreshCw, Zap } from 'lucide-react'

export function AdminOutboxPage() {
  const [eventId, setEventId] = useState('')

  const retryAllMutation = useMutation({
    mutationFn: () => adminOutboxApi.retryAll(),
    onSuccess: (res: any) => {
      const msg = res?.data?.data ?? res?.data?.message ?? '전체 재처리 요청 완료'
      alert(typeof msg === 'string' ? msg : JSON.stringify(msg))
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '재처리 실패'),
  })

  const retryOneMutation = useMutation({
    mutationFn: () => adminOutboxApi.retry(eventId.trim()),
    onSuccess: () => {
      alert('재처리 요청 완료')
      setEventId('')
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '재처리 실패'),
  })

  return (
    <div className="p-8 max-w-2xl">
      <div className="mb-8">
        <h1 className="text-xl font-black tracking-tight">Outbox 이벤트 재처리</h1>
        <p className="text-xs text-white/30 mt-1">
          FAILED 상태 Outbox 이벤트를 INIT으로 초기화 → Poller가 다음 주기에 자동 재발행
        </p>
      </div>

      {/* 전체 재처리 */}
      <div className="mb-6 rounded-lg border border-white/10 bg-white/5 p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-sm font-black tracking-wider mb-1">전체 FAILED 재처리</h2>
            <p className="text-xs text-white/30">
              <code className="bg-white/10 px-1.5 py-0.5 rounded text-[10px]">POST /api/v1/admin/outbox-events/retry-all</code>
            </p>
            <p className="text-xs text-white/20 mt-2">FAILED 상태인 모든 Outbox 이벤트를 일괄 재처리합니다.</p>
          </div>
          <button
            onClick={() => { if (confirm('FAILED 상태 전체 Outbox 이벤트를 재처리하시겠습니까?')) retryAllMutation.mutate() }}
            disabled={retryAllMutation.isPending}
            className="shrink-0 flex items-center gap-2 bg-yellow-400/10 border border-yellow-400/20 px-4 py-2.5 text-xs font-black text-yellow-400 hover:bg-yellow-400/20 disabled:opacity-40 transition-colors"
          >
            <Zap size={13} />
            {retryAllMutation.isPending ? '처리 중...' : '전체 재처리'}
          </button>
        </div>
      </div>

      {/* 단건 재처리 */}
      <div className="rounded-lg border border-white/10 bg-white/5 p-6">
        <h2 className="text-sm font-black tracking-wider mb-1">단건 재처리</h2>
        <p className="text-xs text-white/30 mb-4">
          <code className="bg-white/10 px-1.5 py-0.5 rounded text-[10px]">POST /api/v1/admin/outbox-events/{'{eventId}'}/retry</code>
        </p>
        <div className="flex gap-3">
          <input
            type="text"
            placeholder="Event ID (UUID)"
            value={eventId}
            onChange={e => setEventId(e.target.value)}
            className="flex-1 bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30 font-mono"
          />
          <button
            onClick={() => retryOneMutation.mutate()}
            disabled={!eventId.trim() || retryOneMutation.isPending}
            className="shrink-0 flex items-center gap-2 bg-white/10 border border-white/10 px-4 py-2 text-xs font-black text-white/70 hover:bg-white/20 disabled:opacity-40 transition-colors"
          >
            <RefreshCw size={13} />
            {retryOneMutation.isPending ? '처리 중...' : '재처리'}
          </button>
        </div>
      </div>

      <div className="mt-6 rounded border border-white/5 bg-white/[0.02] px-4 py-3 text-xs text-white/20 space-y-1">
        <p>• Outbox 이벤트는 Product 서비스의 Kafka 메시지 발행 실패 시 저장됩니다.</p>
        <p>• FAILED → INIT으로 되돌려 Poller가 다음 주기에 재발행합니다.</p>
        <p>• 재처리 후 DLQ 탭에서도 확인하세요.</p>
      </div>
    </div>
  )
}
