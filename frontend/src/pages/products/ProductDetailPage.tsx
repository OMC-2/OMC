import { useQuery } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'
import { ArrowLeft } from 'lucide-react'

export function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>()
  const { data, isLoading } = useQuery({ queryKey: ['product', productId], queryFn: () => productsApi.getById(productId!), enabled: !!productId })
  const product = data?.data?.data

  if (isLoading) return <Spinner className="py-20" />
  if (!product) return <p className="text-center py-20 text-xs tracking-widest text-gray-400">PRODUCT NOT FOUND</p>

  return (
    <div className="mx-auto max-w-5xl">
      <Link to="/products" className="mb-8 inline-flex items-center gap-2 text-xs font-bold tracking-wider text-gray-400 hover:text-black transition-colors">
        <ArrowLeft size={14} />BACK
      </Link>
      <div className="grid gap-12 md:grid-cols-2">
        <div className="aspect-square overflow-hidden bg-gray-100">
          <img src={product.imageUrl ?? getPlaceholderImage(0)} alt={product.name} className="h-full w-full object-cover" />
        </div>
        <div className="flex flex-col justify-center space-y-6">
          {product.category && <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 uppercase">{product.category}</p>}
          <h1 className="text-3xl font-black tracking-tight text-gray-900">{product.name}</h1>
          <p className="text-4xl font-black text-black">{formatPrice(product.price)}</p>
          <p className="text-sm text-gray-500 leading-relaxed">{product.description}</p>
          <div className="border border-gray-100 p-5">
            <div className="flex justify-between text-xs">
              <span className="font-bold tracking-wide text-gray-400">STOCK</span>
              <span className="font-black text-gray-900">{product.stockQuantity}개</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
