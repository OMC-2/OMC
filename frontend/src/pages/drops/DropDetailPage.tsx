import { useMutation, useQuery } from '@tanstack/react-query'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { dropsApi } from '../../api/drops'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice, formatDate } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'
import { useAuthStore } from '../../store/authStore'
import { ArrowLeft, Clock, Package } from 'lucide-react'

export function DropDetailPage() {
  const { dropId } = useParams<{ dropId: string }>()
  const { isAuthenticated } = useAuthStore()
  const navigate = useNavigate()

  const { data: dropData, isLoading: dropLoading } = useQuery({
    queryKey: ['drop', dropId],
    queryFn: () => dropsApi.getById(dropId!),
    enabled: !!dropId,
  })
  const drop = dropData?.data?.data

  const { data: productData, isLoading: productLoading } = useQuery({
    queryKey: ['product', drop?.productId],
    queryFn: () => productsApi.getById(drop!.productId),
    enabled: !!drop?.productId,
  })
  const product = productData?.data?.data

  const purchaseMutation = useMutation({
    mutationFn: () => dropsApi.purchase(dropId!),
    onSuccess: () => { alert('구매 완료! 주문 내역에서 확인하세요.'); navigate('/orders') },
    onError: (e: any) => alert(e?.response?.data?.message ?? '구매 실패. 재고가 없거나 이미 구매한 상품입니다.'),
  })

  if (dropLoading || productLoading) return <Spinner className="py-20" />
  if (!drop) return <p className="text-center py-20 text-xs tracking-widest text-gray-400">NOT FOUND</p>

  const isOpen = drop.status === 'OPEN'
  const name = product?.name ?? '상품 정보 로딩 중'
  const price = product?.price ?? 0
  const description = product?.description ?? ''
  const imageUrl = product?.imageUrl ?? getPlaceholderImage(1)

  return (
    <div className="mx-auto max-w-5xl">
      <Link to="/drops" className="mb-8 inline-flex items-center gap-2 text-xs font-bold tracking-wider text-gray-400 hover:text-black transition-colors">
        <ArrowLeft size={14} />BACK
      </Link>
      <div className="grid gap-12 md:grid-cols-2">
        <div className="relative aspect-square overflow-hidden bg-gray-100">
          <img src={imageUrl} alt={name} className="h-full w-full object-cover" />
          {isOpen && <span className="absolute top-5 left-5 bg-red-500 px-3 py-1.5 text-[10px] font-black tracking-[0.2em] text-white">ON DROP</span>}
        </div>
        <div className="flex flex-col space-y-6">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">
              {product?.brand ?? ''}{product?.category ? ` · ${product.category}` : ''}
            </p>
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">{isOpen ? 'NOW AVAILABLE' : 'DROP ENDED'}</p>
            <h1 className="text-3xl font-black tracking-tight text-gray-900">{name}</h1>
          </div>
          <p className="text-4xl font-black">{formatPrice(price)}</p>
          {description && <p className="text-sm text-gray-500 leading-relaxed">{description}</p>}
          <div className="space-y-3 border border-gray-100 p-5">
            <div className="flex justify-between text-xs">
              <span className="flex items-center gap-1.5 font-bold tracking-wide text-gray-400"><Package size={12} />TOTAL QTY</span>
              <span className="font-black">{drop.totalQty}개</span>
            </div>
            {drop.startAt && (
              <div className="flex justify-between text-xs">
                <span className="flex items-center gap-1.5 font-bold tracking-wide text-gray-400"><Clock size={12} />START</span>
                <span>{formatDate(drop.startAt)}</span>
              </div>
            )}
            {drop.endAt && (
              <div className="flex justify-between text-xs">
                <span className="flex items-center gap-1.5 font-bold tracking-wide text-gray-400"><Clock size={12} />END</span>
                <span>{formatDate(drop.endAt)}</span>
              </div>
            )}
          </div>
          {isAuthenticated ? (
            <button
              disabled={!isOpen || purchaseMutation.isPending}
              onClick={() => purchaseMutation.mutate()}
              className="w-full bg-black py-5 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
            >
              {purchaseMutation.isPending ? '처리 중...' : isOpen ? 'BUY NOW' : 'SOLD OUT'}
            </button>
          ) : (
            <Link to="/login" className="block w-full bg-black py-5 text-center text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 transition-colors">
              LOGIN TO BUY
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}
