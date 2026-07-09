import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { dropsApi } from '../../api/drops'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatPrice, getDropDisplayStatus } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'

export function DropsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['drops'],
    queryFn: () => dropsApi.getAll(),
    refetchInterval: 5000, // 5초마다 갱신 → LIVE 상태 자동 전환
  })
  const { data: productsData } = useQuery({
    queryKey: ['products-all'],
    queryFn: () => productsApi.getAll(0, 100),
    refetchInterval: 10000,
  })

  const allDrops = data?.data?.data?.content ?? []
  const productsArr = productsData?.data?.data?.content ?? productsData?.data?.data ?? []
  const productMap: Record<string, any> = {}
  for (const p of productsArr) productMap[p.productId] = p

  // 상품 데이터 로드 완료 후 연결된 상품이 없는 고아 드롭 제거
  const drops = productsData
    ? allDrops.filter((d: any) => !!productMap[d.productId])
    : allDrops

  return (
    <div>
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-red-500 mb-1">EXCLUSIVE</p>
        <h1 className="text-3xl font-black tracking-tight">DROPS</h1>
        <p className="mt-2 text-sm text-gray-400">선착순 한정 판매. 놓치지 마세요.</p>
      </div>
      {isLoading ? <Spinner className="py-20" /> : drops.length === 0 ? (
        <div className="flex flex-col items-center py-32 text-gray-300">
          <p className="text-xs font-bold tracking-widest">NO DROPS AVAILABLE</p>
        </div>
      ) : (
        <div className="grid gap-1 sm:grid-cols-2 lg:grid-cols-3">
          {drops.map((drop: any, i: number) => {
            const product = productMap[drop.productId]
            const name = product?.name ?? drop.productId
            const price = product?.price ?? 0
            const imageUrl = product?.imageUrl || getPlaceholderImage(i + 1)
            return (
              <Link key={drop.dropId} to={`/drops/${drop.dropId}`} className="group">
                <div className="relative aspect-[4/3] overflow-hidden bg-gray-100">
                  <img
                    src={imageUrl}
                    onError={(e) => { const t = e.currentTarget; t.onerror = null; t.src = getPlaceholderImage(i + 1) }}
                    alt={name}
                    className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-700"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/10 to-transparent" />
                  {(() => {
                    const ds = getDropDisplayStatus(drop)
                    return (
                      <span className={`absolute top-4 left-4 px-2.5 py-1 text-[9px] font-black tracking-[0.25em] ${ds === 'LIVE' ? 'bg-red-500 text-white' : ds === 'UPCOMING' ? 'bg-blue-600 text-white' : 'bg-black/50 text-white/50'}`}>
                        {ds === 'LIVE' ? 'ON DROP' : ds === 'UPCOMING' ? '진행 예정' : 'ENDED'}
                      </span>
                    )
                  })()}
                  <div className="absolute bottom-0 left-0 right-0 p-5">
                    <p className="text-sm font-black text-white line-clamp-1 group-hover:text-red-300 transition-colors">{name}</p>
                    <div className="mt-1.5 flex items-center justify-between">
                      <p className="text-xs font-black text-white/70">{formatPrice(price)}</p>
                      <p className="text-[10px] text-white/30">{drop.totalQty}개</p>
                    </div>
                  </div>
                </div>
              </Link>
            )
          })}
        </div>
      )}
    </div>
  )
}
