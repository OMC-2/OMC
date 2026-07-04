import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'

export function ProductsPage() {
  const { data, isLoading } = useQuery({ queryKey: ['products'], queryFn: () => productsApi.getAll() })
  const products = data?.data?.data?.content ?? []

  return (
    <div>
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">CATALOG</p>
        <h1 className="text-3xl font-black tracking-tight">PRODUCTS</h1>
      </div>
      {isLoading ? <Spinner className="py-20" /> : products.length === 0 ? (
        <div className="flex flex-col items-center py-32 text-gray-300">
          <p className="text-xs font-bold tracking-widest">NO PRODUCTS YET</p>
        </div>
      ) : (
        <div className="grid gap-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {products.map((product: any, i: number) => (
            <Link key={product.productId} to={`/products/${product.productId}`} className="group">
              <div className="relative aspect-square overflow-hidden bg-gray-100">
                <img
                  src={product.imageUrl || getPlaceholderImage(i)}
                  alt={product.name}
                  onError={(e) => { const t = e.currentTarget; t.onerror = null; t.src = getPlaceholderImage(i) }}
                  className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-500"
                />
              </div>
              <div className="p-4 border border-t-0 border-gray-100 group-hover:border-gray-300 transition-colors">
                <p className="text-xs font-black text-gray-900 line-clamp-1 group-hover:text-red-500 transition-colors">{product.name}</p>
                {product.category && <p className="text-[10px] text-gray-400 mt-0.5 tracking-wide uppercase">{product.category}</p>}
                <div className="mt-2 flex items-center justify-between">
                  <p className="text-sm font-black text-gray-900">{formatPrice(product.price)}</p>
                  <p className="text-[10px] text-gray-300">{product.stockQuantity}개</p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
