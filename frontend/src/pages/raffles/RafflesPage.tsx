import { useQuery } from '@tanstack/react-query'
import { rafflesApi } from '../../api/raffles'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { RaffleCard } from '../../components/RaffleCard'

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
          {raffles.map((raffle: any, i: number) => (
            <RaffleCard
              key={raffle.raffleId}
              raffle={raffle}
              product={productMap[raffle.productId]}
              index={i}
            />
          ))}
        </div>
      )}
    </div>
  )
}
