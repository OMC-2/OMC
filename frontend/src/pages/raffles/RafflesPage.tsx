import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { rafflesApi } from '../../api/raffles'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'

export function RafflesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['raffles'],
    queryFn: () => rafflesApi.getAll(),
  })

  const { data: productsData } = useQuery({
    queryKey: ['products-all'],
    queryFn: () => productsApi.getAll(0, 100),
  })

  const raffles = data?.data?.data?.content ?? []
  const productsArr = productsData?.data?.data?.content ?? productsData?.data?.data ?? []
  const productMap: Record<string, any> = {}
  for (const p of productsArr) productMap[p.productId] = p

  return (
    <div>
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-red-500 mb-1">FAIR DRAW</p>
        <h1 className="text-3xl font-black tracking-tight">RAFFLE</h1>
        <p className="mt-2 text-sm text-gray-400">모두에게 동등한 기회. 추첨으로 결정됩니다.</p>
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : raffles.length === 0 ? (
        <div className="flex flex-col items-center py-32 text-gray-300">
          <p className="text-xs font-bold tracking-widest">NO RAFFLES AVAILABLE</p>
        </div>
      ) : (
        <div className="grid gap-1 sm:grid-cols-2 lg:grid-cols-3">
          {raffles.map((raffle: any, i: number) => {
            const product = productMap[raffle.productId]
            const imageUrl = product?.imageUrl || raffle.imageUrl || getPlaceholderImage(i)
            return (
              <Link key={raffle.raffleId} to={`/raffles/${raffle.raffleId}`} className="group">
                <div className="relative aspect-[3/4] overflow-hidden bg-gray-900">
                  <img
                    src={imageUrl}
                    alt={raffle.name}
                    onError={(e) => {
                      const t = e.currentTarget
                      t.onerror = null
                      t.src = getPlaceholderImage(i)
                    }}
                    className="h-full w-full object-cover opacity-80 group-hover:scale-105 transition-transform duration-700"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/5 to-transparent" />
                  <div className="absolute top-4 left-4">
                    <span className={`px-2.5 py-1 text-[9px] font-black tracking-[0.25em] ${
                      raffle.status === 'OPEN' ? 'bg-red-500 text-white' : 'bg-black/50 text-white/40'
                    }`}>
                      {raffle.status === 'OPEN' ? 'LIVE' : raffle.status === 'DRAWN' ? 'DRAWN' : 'ENDED'}
                    </span>
                  </div>
                  <div className="absolute bottom-0 left-0 right-0 p-5">
                    <p className="text-sm font-black text-white line-clamp-2 group-hover:text-red-300 transition-colors leading-snug">{raffle.name}</p>
                    <div className="mt-2 flex items-center justify-between text-[10px] text-white/40">
                      <span>당첨 {raffle.winnerCount}명</span>
                      {raffle.endedAt && <span>{formatDate(raffle.endedAt)}</span>}
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
