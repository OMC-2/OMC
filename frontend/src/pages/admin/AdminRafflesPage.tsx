import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminRafflesApi, adminProductsApi } from '../../api/admin'
import { formatDate } from '../../lib/utils'
import { Plus, X, Shuffle, Trash2, Users } from 'lucide-react'

const INIT = { productId: '', name: '', winnerCount: '', startedAt: '', endedAt: '' }
const STATUS_OPTIONS = ['OPEN', 'CLOSED', 'DRAWN', 'CANCELLED']

export function AdminRafflesPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(INIT)
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
  })

  const raffles = data?.data?.data?.content ?? []
  const products = productData?.data?.data?.content ?? productData?.data?.data ?? []
  const entries = entriesData?.data?.data?.content ?? []

  const createMutation = useMutation({
    mutationFn: () => adminRafflesApi.create({
      productId: form.productId,
      name: form.name,
      winnerCount: Number(form.winnerCount),
      startedAt: new Date(form.startedAt).toISOString().slice(0, 19),
      endedAt: new Date(form.endedAt).toISOString().slice(0, 19),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-raffles'] }); setShowForm(false); setForm(INIT) },
    onError: (e: any) => alert(e?.response?.data?.message ?? '생성 실패'),
  })

  const drawMutation = useMutation({
    mutationFn: (id: string) => adminRafflesApi.draw(id),
    onSuccess: () => { alert('추첨 완료!'); qc.invalidateQueries({ queryKey: ['admin-raffles'] }) },
    onError: (e: any) => alert(e?.response?.data?.message ?? '추첨 실패'),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => adminRafflesApi.updateStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-raffles'] }),
    onError: (e: any) => alert(e?.response?.data?.message ?? '상태 변경 실패'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminRafflesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-raffles'] }),
  })

  const f = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(p => ({ ...p, [k]: e.target.value }))

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">래플 관리</h1>
          <p className="text-xs text-white/30 mt-1">{raffles.length}개 래플</p>
        </div>
        <button onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-white px-4 py-2.5 text-[11px] font-black tracking-wider text-black hover:bg-red-500 hover:text-white transition-colors">
          <Plus size={14} />래플 추가
        </button>
      </div>

      {/* 생성 모달 */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">래플 추가</h2>
              <button onClick={() => setShowForm(false)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">상품 *</label>
                <select value={form.productId} onChange={f('productId')}
                  className="w-full bg-gray-800 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30">
                  <option value="" className="bg-gray-800">
                    {productsLoading ? '로딩 중...' : products.length === 0 ? '상품 없음 (상품 관리에서 먼저 추가)' : '상품 선택'}
                  </option>
                  {products.map((p: any) => (
                    <option key={p.productId} value={p.productId} className="bg-gray-800 text-white">{p.name}</option>
                  ))}
                </select>
              </div>
              {[
                { label: '래플 이름 *', key: 'name', type: 'text', placeholder: '예: Nike Dunk Low 래플' },
                { label: '당첨 인원 *', key: 'winnerCount', type: 'number', placeholder: '10' },
                { label: '시작 시간 *', key: 'startedAt', type: 'datetime-local' },
                { label: '종료 시간 *', key: 'endedAt', type: 'datetime-local' },
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
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors">취소</button>
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

      {/* 응모자 모달 */}
      {entriesRaffleId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-lg rounded-lg border border-white/10 bg-gray-900 p-6 max-h-[80vh] flex flex-col">
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
                      {['#', 'User ID', '응모 시간', '금액'].map(h => (
                        <th key={h} className="px-3 py-2 text-left text-[10px] text-white/30 font-bold">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((e: any, i: number) => (
                      <tr key={e.entryId} className="border-b border-white/5">
                        <td className="px-3 py-2 text-white/30">{i + 1}</td>
                        <td className="px-3 py-2 font-mono text-[10px] text-white/50">{e.userId?.slice(0, 12)}...</td>
                        <td className="px-3 py-2 text-white/40">{e.enteredAt ? formatDate(e.enteredAt) : '-'}</td>
                        <td className="px-3 py-2 text-white/60">{e.finalAmount?.toLocaleString()}원</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 목록 */}
      <div className="rounded-lg border border-white/5 overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['래플 이름', '상태', '당첨', '종료', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={5} className="py-10 text-center text-white/20">로딩 중...</td></tr>
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
                    <button onClick={() => setEntriesRaffleId(r.raffleId)}
                      className="text-white/20 hover:text-blue-400 transition-colors" title="응모자 조회">
                      <Users size={14} />
                    </button>
                    <button
                      onClick={() => { if (confirm(`"${r.name}" 추첨을 실행하시겠습니까?`)) drawMutation.mutate(r.raffleId) }}
                      disabled={drawMutation.isPending}
                      className="text-white/20 hover:text-green-400 transition-colors" title="추첨 실행">
                      <Shuffle size={14} />
                    </button>
                    <button onClick={() => { if (confirm('삭제?')) deleteMutation.mutate(r.raffleId) }}
                      className="text-white/20 hover:text-red-400 transition-colors"><Trash2 size={14} /></button>
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
