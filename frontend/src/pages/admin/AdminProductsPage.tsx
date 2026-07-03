import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminProductsApi } from '../../api/admin'
import { formatPrice } from '../../lib/utils'
import { Plus, Trash2, X } from 'lucide-react'

const INIT = { name: '', description: '', price: '', brand: '', category: '', imageUrl: '', initialQuantity: '' }

export function AdminProductsPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(INIT)

  const { data, isLoading } = useQuery({ queryKey: ['admin-products'], queryFn: () => adminProductsApi.getAll() })
  const products = data?.data?.data?.content ?? []

  const createMutation = useMutation({
    mutationFn: () => adminProductsApi.create({
      name: form.name, description: form.description,
      price: Number(form.price), brand: form.brand,
      category: form.category, imageUrl: form.imageUrl || undefined,
      initialQuantity: Number(form.initialQuantity),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-products'] }); setShowForm(false); setForm(INIT) },
    onError: (e: any) => alert(e?.response?.data?.message ?? '생성 실패'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminProductsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-products'] }),
    onError: (e: any) => alert(e?.response?.data?.message ?? '삭제 실패'),
  })

  const f = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm(p => ({ ...p, [k]: e.target.value }))

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black tracking-tight">상품 관리</h1>
          <p className="text-xs text-white/30 mt-1">{products.length}개 상품</p>
        </div>
        <button onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-white px-4 py-2.5 text-[11px] font-black tracking-wider text-black hover:bg-red-500 hover:text-white transition-colors">
          <Plus size={14} />상품 추가
        </button>
      </div>

      {/* 생성 폼 모달 */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-lg rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">상품 추가</h2>
              <button onClick={() => setShowForm(false)}><X size={18} className="text-white/40 hover:text-white" /></button>
            </div>
            <div className="space-y-3">
              {[
                { label: '상품명 *', key: 'name', placeholder: '상품명 입력' },
                { label: '브랜드 *', key: 'brand', placeholder: '브랜드명' },
                { label: '카테고리 *', key: 'category', placeholder: '예: SNEAKERS, APPAREL' },
                { label: '가격 (원) *', key: 'price', placeholder: '100000', type: 'number' },
                { label: '초기 재고 *', key: 'initialQuantity', placeholder: '100', type: 'number' },
                { label: '이미지 URL', key: 'imageUrl', placeholder: 'https://...' },
              ].map(({ label, key, placeholder, type = 'text' }) => (
                <div key={key}>
                  <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">{label}</label>
                  <input type={type} placeholder={placeholder} value={(form as any)[key]} onChange={f(key)}
                    className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30" />
                </div>
              ))}
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">설명</label>
                <textarea placeholder="상품 설명" value={form.description} onChange={f('description')} rows={2}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30 resize-none" />
              </div>
            </div>
            <div className="mt-5 flex gap-3">
              <button onClick={() => setShowForm(false)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors">취소</button>
              <button
                disabled={!form.name || !form.brand || !form.category || !form.price || !form.initialQuantity || createMutation.isPending}
                onClick={() => createMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors">
                {createMutation.isPending ? '생성 중...' : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 목록 */}
      <div className="rounded-lg border border-white/5 overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-white/5 bg-white/5">
              {['상품명', '브랜드', '카테고리', '가격', '재고', ''].map(h => (
                <th key={h} className="px-4 py-3 text-left text-[10px] font-bold tracking-widest text-white/30">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={6} className="py-10 text-center text-white/20">로딩 중...</td></tr>
            ) : products.length === 0 ? (
              <tr><td colSpan={6} className="py-10 text-center text-white/20">상품 없음</td></tr>
            ) : products.map((p: any) => (
              <tr key={p.productId} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                <td className="px-4 py-3 font-medium text-white/80">{p.name}</td>
                <td className="px-4 py-3 text-white/40">{p.brand}</td>
                <td className="px-4 py-3 text-white/40">{p.category}</td>
                <td className="px-4 py-3 text-white/70 font-bold">{formatPrice(p.price)}</td>
                <td className="px-4 py-3 text-white/40">{p.stockQuantity}</td>
                <td className="px-4 py-3">
                  <button onClick={() => { if (confirm('삭제?')) deleteMutation.mutate(p.productId) }}
                    className="text-white/20 hover:text-red-400 transition-colors"><Trash2 size={14} /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
