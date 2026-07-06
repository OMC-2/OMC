import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminCouponsApi } from '../../api/admin'
import { formatDate } from '../../lib/utils'
import { Plus, X, Copy, Check } from 'lucide-react'

const INIT = {
  name: '', discountType: 'FIXED', discountValue: '',
  maxDiscountAmount: '', totalQuantity: '', startedAt: '', expiredAt: '',
}

export function AdminCouponsPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(INIT)
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-coupons'],
    queryFn: () => adminCouponsApi.getAll(),
  })
  const coupons = data?.data?.data?.content ?? []

  const createMutation = useMutation({
    mutationFn: () => adminCouponsApi.create({
      name: form.name,
      discountType: form.discountType,
      discountValue: Number(form.discountValue),
      maxDiscountAmount: form.maxDiscountAmount ? Number(form.maxDiscountAmount) : undefined,
      totalQuantity: Number(form.totalQuantity),
      // 백엔드(Spring)는 KST 로컬 시간 기준으로 검증.
      // datetime-local 값(YYYY-MM-DDTHH:MM)에 ':00'만 붙여 KST 로컬 시간 그대로 전송.
      startedAt: form.startedAt + ':00',
      expiredAt: form.expiredAt + ':00',
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-coupons'] }); setShowForm(false); setForm(INIT) },
    onError: (e: any) => alert(e?.response?.data?.message ?? '생성 실패'),
  })

  const copyId = (id: string) => {
    navigator.clipboard.writeText(id)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 1500)
  }

  const f = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(p => ({ ...p, [k]: e.target.value }))

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">쿠폰 관리</h1>
          <p className="text-xs text-white/30 mt-1">{coupons.length}개 쿠폰</p>
        </div>
        <button onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-white px-4 py-2.5 text-[11px] font-black tracking-wider text-black hover:bg-red-500 hover:text-white transition-colors">
          <Plus size={14} />쿠폰 추가
        </button>
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6 max-h-[90vh] overflow-y-auto">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">쿠폰 추가</h2>
              <button onClick={() => setShowForm(false)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">쿠폰 이름 *</label>
                <input type="text" placeholder="예: 신규 가입 할인" value={form.name} onChange={f('name')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">할인 유형 *</label>
                <select value={form.discountType} onChange={f('discountType')}
                  className="w-full bg-gray-800 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none">
                  <option value="FIXED" className="bg-gray-800">정액 (FIXED)</option>
                  <option value="RATE" className="bg-gray-800">정률 (RATE)</option>
                </select>
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">
                  할인 값 * {form.discountType === 'FIXED' ? '(원)' : '(%, 0.1 = 10%)'}
                </label>
                <input type="number" placeholder={form.discountType === 'FIXED' ? '5000' : '0.1'} value={form.discountValue} onChange={f('discountValue')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
              </div>
              {form.discountType === 'RATE' && (
                <div>
                  <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">최대 할인 금액 (원)</label>
                  <input type="number" placeholder="20000" value={form.maxDiscountAmount} onChange={f('maxDiscountAmount')}
                    className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
                </div>
              )}
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">발급 수량 *</label>
                <input type="number" placeholder="100" value={form.totalQuantity} onChange={f('totalQuantity')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">시작 시간 *</label>
                <input type="datetime-local" value={form.startedAt} onChange={f('startedAt')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30" />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">만료 시간 *</label>
                <input type="datetime-local" value={form.expiredAt} onChange={f('expiredAt')}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white focus:outline-none focus:border-white/30" />
              </div>
            </div>
            <div className="mt-5 flex gap-3">
              <button onClick={() => setShowForm(false)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors">취소</button>
              <button
                disabled={!form.name || !form.discountValue || !form.totalQuantity || !form.startedAt || !form.expiredAt || createMutation.isPending}
                onClick={() => createMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors">
                {createMutation.isPending ? '생성 중...' : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-white/5 overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['이름', '타입', '할인', '잔여/전체', '만료', '쿠폰 ID'].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={6} className="py-10 text-center text-white/20">로딩 중...</td></tr>
            ) : coupons.length === 0 ? (
              <tr><td colSpan={6} className="py-10 text-center text-white/20">쿠폰 없음</td></tr>
            ) : coupons.map((c: any) => (
              <tr key={c.couponId} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                <td className="px-4 py-3 font-medium text-white/80">{c.name}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 text-[10px] font-black ${c.discountType === 'FIXED' ? 'bg-blue-500/20 text-blue-400' : 'bg-purple-500/20 text-purple-400'}`}>
                    {c.discountType}
                  </span>
                </td>
                <td className="px-4 py-3 text-white/70 font-bold">
                  {c.discountType === 'FIXED'
                    ? `${Number(c.discountValue).toLocaleString()}원`
                    : `${(Number(c.discountValue) * 100).toFixed(0)}%`}
                </td>
                <td className="px-4 py-3 text-white/40">{c.remainingQuantity}/{c.totalQuantity}</td>
                <td className="px-4 py-3 text-white/30">{c.expiredAt ? formatDate(c.expiredAt) : '-'}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => copyId(c.couponId)}
                    className="flex items-center gap-1.5 font-mono text-[10px] text-white/30 hover:text-white transition-colors"
                    title="ID 복사"
                  >
                    {copiedId === c.couponId ? <Check size={12} className="text-green-400" /> : <Copy size={12} />}
                    {c.couponId?.slice(0, 8)}...
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4 rounded border border-white/5 bg-white/2 px-4 py-3">
        <p className="text-[10px] text-white/30">💡 쿠폰 ID를 복사해서 유저에게 전달하면 마이페이지에서 직접 발급받을 수 있습니다.</p>
      </div>
    </div>
  )
}
