import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminProductsApi, adminInventoryApi } from '../../api/admin'
import { formatPrice } from '../../lib/utils'
import { Plus, Trash2, X, ImageIcon, Boxes } from 'lucide-react'

const INIT = { name: '', description: '', price: '', brand: '', category: '', imageUrl: '', initialQuantity: '' }

const IMAGE_PRESETS = [
  { label: '다크 스니커즈', url: 'https://images.unsplash.com/photo-1542838132-92c53300491e?w=600&q=80' },
  { label: '화이트 스니커즈', url: 'https://images.unsplash.com/photo-1460353581641-37baddab0fa2?w=600&q=80' },
  { label: '클린 스니커즈', url: 'https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=600&q=80' },
  { label: '레드 스니커즈', url: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80' },
  { label: '후드티', url: 'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=600&q=80' },
  { label: '자켓', url: 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=600&q=80' },
  { label: '볼캡', url: 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=600&q=80' },
  { label: '백팩', url: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80' },
]

function ImagePresetPicker({ value, onChange }: { value: string; onChange: (url: string) => void }) {
  return (
    <div>
      <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">이미지 URL</label>
      <input
        type="text"
        placeholder="https://..."
        value={value}
        onChange={e => onChange(e.target.value)}
        className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30 mb-2"
      />
      <p className="text-[10px] text-white/20 mb-1.5">프리셋 선택 (클릭 시 자동 입력)</p>
      <div className="grid grid-cols-4 gap-1.5">
        {IMAGE_PRESETS.map(preset => (
          <button
            key={preset.url}
            type="button"
            onClick={() => onChange(preset.url)}
            className={`relative aspect-square overflow-hidden rounded border-2 transition-all ${value === preset.url ? 'border-red-500' : 'border-transparent hover:border-white/30'}`}
            title={preset.label}
          >
            <img src={preset.url} alt={preset.label} className="h-full w-full object-cover" />
          </button>
        ))}
      </div>
    </div>
  )
}

function InventoryModal({ product, onClose }: { product: any; onClose: () => void }) {
  const qc = useQueryClient()
  const [newQty, setNewQty] = useState('')
  const [reason, setReason] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['admin-inventory', product.productId],
    queryFn: () => adminInventoryApi.get(product.productId),
  })
  const inv = data?.data?.data

  const updateMutation = useMutation({
    mutationFn: () => adminInventoryApi.update(product.productId, Number(newQty), reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-inventory', product.productId] })
      qc.invalidateQueries({ queryKey: ['admin-products'] })
      setNewQty('')
      setReason('')
      alert('재고가 수정되었습니다.')
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '수정 실패'),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
      <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6">
        <div className="mb-5 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-black tracking-wider">재고 관리</h2>
            <p className="text-[10px] text-white/30 mt-0.5">{product.name}</p>
          </div>
          <button onClick={onClose}><X size={18} className="text-white/40 hover:text-white" /></button>
        </div>

        {isLoading ? (
          <div className="py-8 text-center text-xs text-white/20">로딩 중...</div>
        ) : inv ? (
          <div className="mb-5 grid grid-cols-3 gap-3">
            {[
              { label: '총 재고', value: inv.totalQuantity },
              { label: '판매됨', value: inv.soldQuantity },
              { label: '잔여', value: inv.availableQuantity },
            ].map(({ label, value }) => (
              <div key={label} className="bg-white/5 rounded p-3 text-center">
                <p className="text-[10px] text-white/30 tracking-wider mb-1">{label}</p>
                <p className="text-xl font-black text-white">{value}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className="mb-5 py-4 text-center text-xs text-white/20">재고 정보 없음</div>
        )}

        <div className="space-y-3">
          <div>
            <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">새 총 재고 수량 *</label>
            <input
              type="number"
              min="0"
              placeholder={inv ? String(inv.totalQuantity) : '0'}
              value={newQty}
              onChange={e => setNewQty(e.target.value)}
              className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">변경 사유 * (감사 로그)</label>
            <input
              type="text"
              placeholder="예: 추가 입고, 불량 차감"
              value={reason}
              onChange={e => setReason(e.target.value)}
              className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30"
            />
          </div>
        </div>

        <div className="mt-5 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors"
          >
            닫기
          </button>
          <button
            disabled={!newQty || !reason || updateMutation.isPending}
            onClick={() => updateMutation.mutate()}
            className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors"
          >
            {updateMutation.isPending ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function AdminProductsPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(INIT)
  const [editProduct, setEditProduct] = useState<any | null>(null)
  const [editImageUrl, setEditImageUrl] = useState('')
  const [inventoryProduct, setInventoryProduct] = useState<any | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['admin-products'], queryFn: () => adminProductsApi.getAll() })
  const products = data?.data?.data?.content ?? []

  const createMutation = useMutation({
    mutationFn: () => adminProductsApi.create({
      name: form.name,
      description: form.description,
      price: Number(form.price),
      brand: form.brand,
      category: form.category,
      imageUrl: form.imageUrl || undefined,
      initialQuantity: Number(form.initialQuantity),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-products'] })
      setShowForm(false)
      setForm(INIT)
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '생성 실패'),
  })

  const updateImageMutation = useMutation({
    mutationFn: () => adminProductsApi.update(editProduct.productId, {
      name: editProduct.name,
      description: editProduct.description,
      price: editProduct.price,
      brand: editProduct.brand,
      category: editProduct.category,
      imageUrl: editImageUrl || undefined,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-products'] })
      qc.invalidateQueries({ queryKey: ['products-all'] })
      setEditProduct(null)
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '수정 실패'),
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
        <button
          onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-white px-4 py-2.5 text-[11px] font-black tracking-wider text-black hover:bg-red-500 hover:text-white transition-colors"
        >
          <Plus size={14} />상품 추가
        </button>
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-lg rounded-lg border border-white/10 bg-gray-900 p-6 max-h-[90vh] overflow-y-auto">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">상품 추가</h2>
              <button onClick={() => setShowForm(false)}>
                <X size={18} className="text-white/40 hover:text-white" />
              </button>
            </div>
            <div className="space-y-3">
              {[
                { label: '상품명 *', key: 'name', placeholder: '상품명 입력' },
                { label: '브랜드 *', key: 'brand', placeholder: '브랜드명' },
                { label: '카테고리 *', key: 'category', placeholder: '예: SNEAKERS, APPAREL' },
                { label: '가격 (원) *', key: 'price', placeholder: '100000', type: 'number' },
                { label: '초기 재고 *', key: 'initialQuantity', placeholder: '100', type: 'number' },
              ].map(({ label, key, placeholder, type = 'text' }) => (
                <div key={key}>
                  <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">{label}</label>
                  <input
                    type={type}
                    placeholder={placeholder}
                    value={(form as any)[key]}
                    onChange={f(key)}
                    className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30"
                  />
                </div>
              ))}
              <ImagePresetPicker value={form.imageUrl} onChange={url => setForm(p => ({ ...p, imageUrl: url }))} />
              <div>
                <label className="block text-[10px] font-bold text-white/40 tracking-wider mb-1">설명</label>
                <textarea
                  placeholder="상품 설명"
                  value={form.description}
                  onChange={f('description')}
                  rows={2}
                  className="w-full bg-white/5 border border-white/10 rounded px-3 py-2 text-xs text-white placeholder:text-white/20 focus:outline-none focus:border-white/30 resize-none"
                />
              </div>
            </div>
            <div className="mt-5 flex gap-3">
              <button
                onClick={() => setShowForm(false)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors"
              >
                취소
              </button>
              <button
                disabled={!form.name || !form.brand || !form.category || !form.price || !form.initialQuantity || createMutation.isPending}
                onClick={() => createMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors"
              >
                {createMutation.isPending ? '생성 중...' : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {editProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-lg border border-white/10 bg-gray-900 p-6">
            <div className="mb-5 flex items-center justify-between">
              <div>
                <h2 className="text-sm font-black tracking-wider">이미지 변경</h2>
                <p className="text-[10px] text-white/30 mt-0.5">{editProduct.name}</p>
              </div>
              <button onClick={() => setEditProduct(null)}>
                <X size={18} className="text-white/40 hover:text-white" />
              </button>
            </div>
            <ImagePresetPicker value={editImageUrl} onChange={setEditImageUrl} />
            <div className="mt-5 flex gap-3">
              <button
                onClick={() => setEditProduct(null)}
                className="flex-1 border border-white/10 py-2.5 text-xs font-bold text-white/40 hover:text-white transition-colors"
              >
                취소
              </button>
              <button
                disabled={!editImageUrl || updateImageMutation.isPending}
                onClick={() => updateImageMutation.mutate()}
                className="flex-1 bg-white py-2.5 text-xs font-black text-black hover:bg-red-500 hover:text-white disabled:bg-white/20 disabled:text-white/20 transition-colors"
              >
                {updateImageMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      )}

      {inventoryProduct && (
        <InventoryModal product={inventoryProduct} onClose={() => setInventoryProduct(null)} />
      )}

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
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => { setEditProduct(p); setEditImageUrl(p.imageUrl ?? '') }}
                      className="text-white/20 hover:text-blue-400 transition-colors"
                      title="이미지 변경"
                    >
                      <ImageIcon size={14} />
                    </button>
                    <button
                      onClick={() => setInventoryProduct(p)}
                      className="text-white/20 hover:text-green-400 transition-colors"
                      title="재고 관리"
                    >
                      <Boxes size={14} />
                    </button>
                    <button
                      onClick={() => { if (confirm('삭제?')) deleteMutation.mutate(p.productId) }}
                      className="text-white/20 hover:text-red-400 transition-colors"
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
