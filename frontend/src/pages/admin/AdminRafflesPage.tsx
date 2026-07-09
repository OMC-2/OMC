import { toast } from '../../components/ui/Toast'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminRafflesApi, adminProductsApi } from '../../api/admin'
import { formatDate } from '../../lib/utils'
import { Plus, X, Trash2, Users, Pencil, AlertTriangle, Shuffle } from 'lucide-react'

const INIT = { productId: '', name: '', winnerCount: '', startedAt: '', endedAt: '' }
const EDIT_INIT = { name: '', winnerCount: '', startedAt: '', endedAt: '' }
// RaffleStatus enum: SCHEDULED | OPEN | CLOSED
const STATUS_OPTIONS = ['SCHEDULED', 'OPEN', 'CLOSED']

export function AdminRafflesPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm]         = useState(false)
  const [form, setForm]                 = useState(INIT)
  const [editTarget, setEditTarget]     = useState<{ raffleId: string } & typeof EDIT_INIT | null>(null)
  const [entriesRaffleId, setEntriesRaffleId] = useState<string | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['admin-raffles'], queryFn: () => adminRafflesApi.getAll() })
  const { data: productData, isLoading: productsLoading } = useQuery({
    queryKey: ['admin-products'],
    queryFn: () => adminProductsApi.getAll(),
    staleTime: 0,
  })
  const { data: entriesData } = useQuery({
    queryKey: ['admin-raffle-entries', entriesRaffleId],
    queryFn: () => adminRafflesApi.getEntries(entriesRaffleId!),
    enabled: !!entriesRaffleId,
    retry: false,
  })

  const raffles  = data?.data?.data?.content ?? []
  const products = productData?.data?.data?.content ?? productData?.data?.data ?? []
  const entries  = entriesData?.data?.data?.content ?? []

  const createMutation = useMutation({
    mutationFn: () => adminRafflesApi.create({
      productId: form.productId,
      name: form.name,
      winnerCount: Number(form.winnerCount),
      startedAt: form.startedAt + ':00',
      endedAt: form.endedAt + ':00',
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-raffles'] }); setShowForm(false); setForm(INIT); toast.success('래플 생성 완료') },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '래플 생성 실패'),
  })

  const drawMutation = useMutation({
    mutationFn: (id: string) => adminRafflesApi.draw(id),
    onSuccess: () => { toast.success('추첨 완료!'); qc.invalidateQueries({ queryKey: ['admin-raffles'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '추첨 실패'),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => adminRafflesApi.updateStatus(id, status),
    onSuccess: () => { toast.success('상태 변경 완료'); qc.invalidateQueries({ queryKey: ['admin-raffles'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '상태 변경 실패'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminRafflesApi.delete(id),
    onSuccess: () => { toast.success('삭제 완료'); qc.invalidateQueries({ queryKey: ['admin-raffles'] }) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '삭제 실패'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ raffleId, name, winnerCount, startedAt, endedAt }: {
      raffleId: string; name: string; winnerCount: number; startedAt?: string; endedAt?: string
    }) => adminRafflesApi.update(raffleId, {
      name, winnerCount,
      ...(startedAt ? { startedAt: startedAt + ':00' } : {}),
      ...(endedAt   ? { endedAt: endedAt + ':00' }     : {}),
    }),
    onSuccess: () => { toast.success('수정 완료'); qc.invalidateQueries({ queryKey: ['admin-raffles'] }); setEditTarget(null) },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '수정 실패'),
  })

  const penalizeMutation = useMutation({
    mutationFn: ({ raffleId, userId }: { raffleId: string; userId: string }) =>
      adminRafflesApi.penalize(raffleId, userId),
    onSuccess: () => {
      toast.success('패널티 부여 완료')
      qc.invalidateQueries({ queryKey: ['admin-raffle-entries', entriesRaffleId] })
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '패널티 부여 실패'),
  })

  const f  = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(p => ({ ...p, [k]: e.target.value }))
  const fe = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setEditTarget(p => p ? { ...p, [k]: e.target.value } : p)
  const toDatetimeLocal = (s?: string) => s ? s.slice(0, 16) : ''

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">래플 관리</h1>
          <p className="text-xs text-white/30 mt-1">{raffles.length}개</p>
        </div>
        <button
          onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-white px-4 py-2.5 text-[11px] font-black tracking-wider text-black hover:bg-red-500 hover:text-white transition-colors"
        >
          <Plus size={14} />래플 생성
        </button>
      </div>

      {/* 생성 모달 */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">래플 생성</h2>
              <button onClick={() => setShowForm(false)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">상품 *</label>
                <select value={form.productId} onChange={f('productId')}
                  className="w-full bg-gray-800 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30">
                  <option value="" className="bg-gray-800">
                    {productsLoading ? '불러오는 중...' : products.length === 0 ? '상품 없음' : '상품 선택'}
                  </option>
                  {products.map((p: any) => (
                    <option key={p.productId} value={p.productId} className="bg-gray-800 text-white">{p.name}</option>
                  ))}
                </select>
              </div>
              {[
                { label: '래플명 *',   key: 'name',        type: 'text',          placeholder: 'Nike Dunk Low 래플' },
                { label: '당첨 인원 *', key: 'winnerCount', type: 'number',        placeholder: '10' },
                { label: '시작일시 *',  key: 'startedAt',   type: 'datetime-local', placeholder: '' },
                { label: '종료일시 *',  key: 'endedAt',     type: 'datetime-local', placeholder: '' },
              ].map(({ label, key, type, placeholder }) => (
                <div key={key}>
                  <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">{label}</label>
                  <input type={type} placeholder={placeholder} value={(form as any)[key]} onChange={f(key)}
                    className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
                </div>
              ))}
            </div>
            <div className="mt-5 flex gap-3">
              <button onClick={() => setShowForm(false)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors">
                취소
              </button>
              <button
                disabled={!form.productId || !form.name || !form.winnerCount || !form.startedAt || !form.endedAt || createMutation.isPending}
                onClick={() => createMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors">
                {createMutation.isPending ? '생성 중...' : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 응모자 목록 모달 */}
      {entriesRaffleId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-2xl rounded-lg border border-white/10 bg-gray-900 p-6 max-h-[80vh] flex flex-col">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">응모자 목록 ({entries.length}명)</h2>
              <button onClick={() => setEntriesRaffleId(null)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="overflow-auto flex-1">
              {entries.length === 0 ? (
                <p className="text-center py-8 text-xs text-white/20">응모자 없음</p>
              ) : (
                <table className="w-full text-xs">
                  <thead className="sticky top-0 bg-gray-900">
                    <tr className="border-b border-white/10">
                      {['#', 'User ID', '응모일시', '결제금액', '결과', '패널티'].map(h => (
                        <th key={h} className="px-3 py-2 text-left text-[10px] text-white/30 font-bold">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((e: any, i: number) => (
                      <tr key={e.entryId} className="border-b border-white/5 hover:bg-white/5">
                        <td className="px-3 py-2 text-white/30">{i + 1}</td>
                        <td className="px-3 py-2 font-mono text-[10px] text-white/50">{e.userId?.slice(0, 12)}...</td>
                        <td className="px-3 py-2 text-white/40">{e.enteredAt ? formatDate(e.enteredAt) : '-'}</td>
                        <td className="px-3 py-2 text-white/60">{e.finalAmount?.toLocaleString()}원</td>
                        <td className="px-3 py-2">
                          <span className={`text-[10px] font-black px-1.5 py-0.5 ${
                            e.result === 'WIN'  ? 'bg-yellow-500/20 text-yellow-400'
                            : e.result === 'LOSE' ? 'bg-white/5 text-white/20'
                            : 'text-white/20'
                          }`}>
                            {e.result ?? 'PENDING'}
                          </span>
                        </td>
                        <td className="px-3 py-2">
                          {e.result === 'WIN' && !e.penalized && (
                            <button
                              onClick={() => {
                                if (confirm(`${e.userId?.slice(0, 8)}... 에게 패널티 부여?\n(30일 래플 참여 제한)`))
                                  penalizeMutation.mutate({ raffleId: entriesRaffleId!, userId: e.userId })
                              }}
                              className="flex items-center gap-1 text-[10px] text-orange-400/60 hover:text-orange-400 transition-colors"
                              title="패널티 부여"
                            >
                              <AlertTriangle size={11} />패널티
                            </button>
                          )}
                          {e.penalized && <span className="text-[10px] text-red-400/60 font-bold">패널티됨</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 수정 모달 */}
      {editTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-sm rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">래플 수정</h2>
              <button onClick={() => setEditTarget(null)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">래플명 *</label>
                <input type="text" value={editTarget.name} onChange={fe('name')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30" />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">당첨 인원 *</label>
                <input type="number" value={editTarget.winnerCount} onChange={fe('winnerCount')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30" />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">시작일시</label>
                <input type="datetime-local" value={editTarget.startedAt} onChange={fe('startedAt')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30" />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">종료일시</label>
                <input type="datetime-local" value={editTarget.endedAt} onChange={fe('endedAt')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30" />
              </div>
            </div>
            <div className="mt-5 flex gap-3">
              <button onClick={() => setEditTarget(null)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors">
                취소
              </button>
              <button
                disabled={!editTarget.name || !editTarget.winnerCount || updateMutation.isPending}
                onClick={() => updateMutation.mutate({
                  raffleId: editTarget.raffleId,
                  name: editTarget.name,
                  winnerCount: Number(editTarget.winnerCount),
                  startedAt: editTarget.startedAt || undefined,
                  endedAt:   editTarget.endedAt   || undefined,
                })}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors">
                {updateMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 래플 목록 테이블 */}
      <div className="rounded-lg border border-white/5 overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['래플명', '상태', '당첨인원', '종료일', '액션'].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={5} className="py-10 text-center text-white/20">불러오는 중...</td></tr>
            ) : raffles.length === 0 ? (
              <tr><td colSpan={5} className="py-10 text-center text-white/20">래플 없음</td></tr>
            ) : raffles.map((r: any) => (
              <tr key={r.raffleId} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                <td className="px-4 py-3 font-medium text-white/80 max-w-[200px] truncate">{r.name}</td>
                <td className="px-4 py-3">
                  <select
                    value={r.status}
                    onChange={e => statusMutation.mutate({ id: r.raffleId, status: e.target.value })}
                    className="bg-transparent border border-white/10 rounded px-2 py-1 text-[10px] text-white/60 focus:outline-none hover:border-white/30"
                  >
                    {STATUS_OPTIONS.map(s => <option key={s} value={s} className="bg-gray-900">{s}</option>)}
                  </select>
                </td>
                <td className="px-4 py-3 text-white/40">{r.winnerCount}명</td>
                <td className="px-4 py-3 text-white/30">{r.endedAt ? formatDate(r.endedAt) : '-'}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setEntriesRaffleId(r.raffleId)}
                      className="text-white/20 hover:text-blue-400 transition-colors" title="응모자 목록"
                    >
                      <Users size={14} />
                    </button>
                    <button
                      onClick={() => setEditTarget({
                        raffleId: r.raffleId,
                        name: r.name,
                        winnerCount: String(r.winnerCount),
                        startedAt: toDatetimeLocal(r.startedAt),
                        endedAt:   toDatetimeLocal(r.endedAt),
                      })}
                      className="text-white/20 hover:text-yellow-400 transition-colors" title="수정"
                    >
                      <Pencil size={14} />
                    </button>
                    <button
                      onClick={() => { if (confirm(`"${r.name}" 추첨을 실행하시겠습니까?`)) drawMutation.mutate(r.raffleId) }}
                      disabled={drawMutation.isPending}
                      className="text-white/20 hover:text-green-400 transition-colors disabled:opacity-30" title="추첨 실행"
                    >
                      <Shuffle size={14} />
                    </button>
                    <button
                      onClick={() => { if (confirm(`"${r.name}"을 삭제하시겠습니까?`)) deleteMutation.mutate(r.raffleId) }}
                      className="text-white/20 hover:text-red-400 transition-colors" title="삭제"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
