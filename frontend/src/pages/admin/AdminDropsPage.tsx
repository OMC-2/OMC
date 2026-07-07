import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminDropsApi, adminProductsApi } from '../../api/admin'
import { formatDate } from '../../lib/utils'
import { Plus, X, StopCircle, Trash2, Package, Edit2 } from 'lucide-react'

const INIT = { productId: '', startAt: '', endAt: '', totalQty: '', holdTtlSec: '30' }

export function AdminDropsPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(INIT)
  const [editDrop, setEditDrop] = useState<any | null>(null)
  const [editForm, setEditForm] = useState({ startAt: '', endAt: '', totalQty: '', holdTtlSec: '' })

  const { data, isLoading } = useQuery({ queryKey: ['admin-drops'], queryFn: () => adminDropsApi.getAll() })
  const { data: productData, isLoading: productsLoading } = useQuery({
    queryKey: ['admin-products'],
    queryFn: () => adminProductsApi.getAll(),
    staleTime: 0,
  })
  const drops = data?.data?.data?.content ?? []
  const products = productData?.data?.data?.content ?? productData?.data?.data ?? []
  const productMap: Record<string, any> = {}
  for (const p of products) productMap[p.productId] = p

  const createMutation = useMutation({
    mutationFn: () => adminDropsApi.create({
      productId: form.productId,
      startAt: form.startAt + ':00',
      endAt: form.endAt + ':00',
      totalQty: Number(form.totalQty),
      holdTtlSec: Number(form.holdTtlSec),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-drops'] }); setShowForm(false); setForm(INIT) },
    onError: (e: any) => alert(e?.response?.data?.message ?? '생성 실패'),
  })

  const closeMutation = useMutation({
    mutationFn: (id: string) => adminDropsApi.close(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-drops'] }),
    onError: (e: any) => alert(e?.response?.data?.message ?? '종료 실패'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminDropsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-drops'] }),
  })

  const updateMutation = useMutation({
    mutationFn: () => adminDropsApi.update(editDrop.dropId, {
      startAt: editForm.startAt + ':00',
      endAt: editForm.endAt + ':00',
      totalQty: Number(editForm.totalQty),
      holdTtlSec: Number(editForm.holdTtlSec),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-drops'] })
      setEditDrop(null)
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '수정 실패'),
  })

  const openEdit = (d: any) => {
    const toLocal = (dt: string) => dt ? dt.slice(0, 16) : ''
    setEditForm({ startAt: toLocal(d.startAt), endAt: toLocal(d.endAt), totalQty: String(d.totalQty ?? ''), holdTtlSec: String(d.holdTtlSec ?? '30') })
    setEditDrop(d)
  }

  const f = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(p => ({ ...p, [k]: e.target.value }))

  const ef = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setEditForm(p => ({ ...p, [k]: e.target.value }))

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">드롭 관리</h1>
          <p className="text-xs text-white/30 mt-1">{drops.length}개 드롭</p>
        </div>
        <button onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-white px-4 py-2.5 text-[11px] font-black tracking-wider text-black hover:bg-red-500 hover:text-white transition-colors">
          <Plus size={14} />드롭 추가
        </button>
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">드롭 추가</h2>
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
                { label: '시작 시간 *', key: 'startAt', type: 'datetime-local' },
                { label: '종료 시간 *', key: 'endAt', type: 'datetime-local' },
                { label: '재고 수량 *', key: 'totalQty', type: 'number', placeholder: '100' },
                { label: '선점 대기(초)', key: 'holdTtlSec', type: 'number', placeholder: '30' },
              ].map(({ label, key, type, placeholder }: any) => (
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
                disabled={!form.productId || !form.startAt || !form.endAt || !form.totalQty || createMutation.isPending}
                onClick={() => createMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors">
                {createMutation.isPending ? '생성 중...' : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {editDrop && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <div>
                <h2 className="text-sm font-black tracking-wider">드롭 수정</h2>
                <p className="text-[10px] text-white/30 mt-0.5 font-mono">{editDrop.dropId?.slice(0, 8)}...</p>
              </div>
              <button onClick={() => setEditDrop(null)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="space-y-3">
              {[
                { label: '시작 시간 *', key: 'startAt', type: 'datetime-local' },
                { label: '종료 시간 *', key: 'endAt', type: 'datetime-local' },
                { label: '재고 수량 *', key: 'totalQty', type: 'number', placeholder: '100' },
                { label: '선점 대기(초) *', key: 'holdTtlSec', type: 'number', placeholder: '30' },
              ].map(({ label, key, type, placeholder }: any) => (
                <div key={key}>
                  <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">{label}</label>
                  <input type={type} placeholder={placeholder} value={(editForm as any)[key]} onChange={ef(key)}
                    className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
                </div>
              ))}
            </div>
            <div className="mt-5 flex gap-3">
              <button onClick={() => setEditDrop(null)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors">취소</button>
              <button
                disabled={!editForm.startAt || !editForm.endAt || !editForm.totalQty || updateMutation.isPending}
                onClick={() => updateMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors">
                {updateMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-white/5 overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['드롭 ID', '상품명', '상태', '시작', '종료', '재고', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={7} className="py-10 text-center text-white/20">로딩 중...</td></tr>
            ) : drops.length === 0 ? (
              <tr><td colSpan={7} className="py-10 text-center text-white/20">드롭 없음</td></tr>
            ) : drops.map((d: any) => (
              <tr key={d.dropId} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                <td className="px-4 py-3 font-mono text-white/30 text-[10px]">{d.dropId?.slice(0, 8)}...</td>
                <td className="px-4 py-3 text-white/70 text-xs">
                  <span className="flex items-center gap-1.5">
                    <Package size={12} className="text-white/20 shrink-0" />
                    {productMap[d.productId]?.name ?? <span className="text-white/20 font-mono text-[10px]">{d.productId?.slice(0, 8)}</span>}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 text-[10px] font-black ${d.status === 'OPEN' ? 'bg-red-500/20 text-red-400' : 'bg-white/5 text-white/30'}`}>
                    {d.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-white/40">{d.startAt ? formatDate(d.startAt) : '-'}</td>
                <td className="px-4 py-3 text-white/40">{d.endAt ? formatDate(d.endAt) : '-'}</td>
                <td className="px-4 py-3 text-white/60">{d.totalQty ?? '-'}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {d.status === 'SCHEDULED' && (
                      <button onClick={() => openEdit(d)}
                        className="text-white/20 hover:text-blue-400 transition-colors" title="수정">
                        <Edit2 size={14} />
                      </button>
                    )}
                    {d.status === 'OPEN' && (
                      <button onClick={() => { if (confirm('드롭을 종료하시겠습니까?')) closeMutation.mutate(d.dropId) }}
                        className="text-white/30 hover:text-yellow-400 transition-colors" title="종료">
                        <StopCircle size={14} />
                      </button>
                    )}
                    <button onClick={() => { if (confirm('삭제?')) deleteMutation.mutate(d.dropId) }}
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
