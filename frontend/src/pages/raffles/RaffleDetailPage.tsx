import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { rafflesApi } from '../../api/raffles'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'
import { useAuthStore } from '../../store/authStore'
import { ArrowLeft, Trophy, Clock } from 'lucide-react'

export function RaffleDetailPage() {
  const { raffleId } = useParams<{ raffleId: string }>()
  const { isAuthenticated } = useAuthStore()
  const queryClient = useQueryClient()

  const [billingKeyId, setBillingKeyId] = useState('')
  const [originalAmount, setOriginalAmount] = useState('0')
  const [discountAmount, setDiscountAmount] = useState('0')

  const { data, isLoading } = useQuery({
    queryKey: ['raffle', raffleId],
    queryFn: () => rafflesApi.getById(raffleId!),
    enabled: !!raffleId,
  })

  const raffle = data?.data?.data

  const { data: productData } = useQuery({
    queryKey: ['product', raffle?.productId],
    queryFn: () => productsApi.getById(raffle!.productId),
    enabled: !!raffle?.productId,
  })
  const product = productData?.data?.data

  const { data: myResultData } = useQuery({
    queryKey: ['my-raffle-result', raffleId],
    queryFn: () => rafflesApi.getMyResult(raffleId!),
    enabled: !!raffleId && isAuthenticated,
    retry: false,
  })
  const myResult = myResultData?.data?.data

  const entryMutation = useMutation({
    mutationFn: () => rafflesApi.enter(raffleId!, {
      billingKeyId: billingKeyId || null,
      couponId: null,
      originalAmount: parseFloat(originalAmount) || 0,
      discountAmount: parseFloat(discountAmount) || 0,
      finalAmount: Math.max(0, (parseFloat(originalAmount) || 0) - (parseFloat(discountAmount) || 0)),
    }),
    onSuccess: () => {
      alert('응모 완료! 추첨 결과를 기다려주세요.')
      queryClient.invalidateQueries({ queryKey: ['my-raffle-result', raffleId] })
    },
    onError: (e: any) => alert(e?.response?.data?.message ?? '응모 실패'),
  })

  if (isLoading) return <Spinner className="py-20" />
  if (!raffle) return <p className="text-center py-20 text-xs tracking-widest text-gray-400">NOT FOUND</p>

  const isOpen = raffle.status === 'OPEN'
  const price = product?.price ?? 0
  const imageUrl = product?.imageUrl ?? getPlaceholderImage(0)
  const finalAmount = Math.max(0, (parseFloat(originalAmount) || 0) - (parseFloat(discountAmount) || 0))

  return (
    <div className="mx-auto max-w-5xl">
      <Link to="/raffles" className="mb-8 inline-flex items-center gap-2 text-xs font-bold tracking-wider text-gray-400 hover:text-black transition-colors">
        <ArrowLeft size={14} />BACK
      </Link>

      <div className="grid gap-12 md:grid-cols-2">
        {/* Image */}
        <div className="relative aspect-square overflow-hidden bg-gray-50">
          <img src={imageUrl} alt={raffle.name} className="h-full w-full object-cover" />
          <div className="absolute top-5 left-5">
            <span className={`px-3 py-1.5 text-[10px] font-black tracking-[0.2em] ${
              isOpen ? 'bg-red-500 text-white' : 'bg-black/60 text-white/60'
            }`}>
              {isOpen ? 'RAFFLE LIVE' : raffle.status === 'DRAWN' ? 'DRAWN' : 'ENDED'}
            </span>
          </div>
        </div>

        {/* Info */}
        <div className="flex flex-col space-y-6">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">RAFFLE</p>
            <h1 className="text-3xl font-black tracking-tight text-gray-900 leading-tight">{raffle.name}</h1>
            {product && <p className="text-sm text-gray-400 mt-1">{product.name}</p>}
          </div>

          {/* Stats */}
          <div className="grid grid-cols-2 gap-px bg-gray-100">
            {[
              { icon: Trophy, value: raffle.winnerCount, label: '당첨 인원' },
              { icon: Trophy, value: price > 0 ? price.toLocaleString() + '원' : 'FREE', label: '상품 가격' },
            ].map(({ icon: Icon, value, label }) => (
              <div key={label} className="bg-white p-4 text-center">
                <Icon size={16} className="mx-auto mb-1 text-gray-300" />
                <p className="text-xl font-black text-gray-900">{value}</p>
                <p className="text-[10px] text-gray-400 tracking-wide">{label}</p>
              </div>
            ))}
          </div>

          {/* Period */}
          <div className="space-y-2 border border-gray-100 p-4">
            {raffle.startedAt && (
              <div className="flex justify-between text-xs">
                <span className="flex items-center gap-1.5 font-bold text-gray-400 tracking-wide"><Clock size={11} />START</span>
                <span className="font-medium">{formatDate(raffle.startedAt)}</span>
              </div>
            )}
            {raffle.endedAt && (
              <div className="flex justify-between text-xs">
                <span className="flex items-center gap-1.5 font-bold text-gray-400 tracking-wide"><Clock size={11} />DEADLINE</span>
                <span className="font-medium">{formatDate(raffle.endedAt)}</span>
              </div>
            )}
          </div>

          {/* My entry result */}
          {myResult && (
            <div className="bg-green-50 border border-green-200 p-4">
              <p className="text-xs font-black tracking-wider text-green-700">응모 완료</p>
              {myResult.result && (
                <p className="mt-1 text-xs text-green-600">
                  결과: {myResult.result === 'WIN' ? '당첨!' : '아쉽게 탈락'}
                </p>
              )}
            </div>
          )}

          {/* Entry form */}
          {isAuthenticated && isOpen && !myResult && (
            <div className="border border-gray-200 p-6 space-y-4">
              <p className="text-xs font-black tracking-widest text-gray-900">APPLY</p>
              {price > 0 && (
                <>
                  <div>
                    <label className="block text-xs font-bold text-gray-500 mb-1">빌링키 ID (결제 수단)</label>
                    <input
                      type="text"
                      placeholder="등록된 결제 수단 ID (선택)"
                      value={billingKeyId}
                      onChange={e => setBillingKeyId(e.target.value)}
                      className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-bold text-gray-500 mb-1">상품 금액 (원)</label>
                      <input type="number" value={originalAmount} onChange={e => setOriginalAmount(e.target.value)}
                        className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black" />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-gray-500 mb-1">할인 금액 (원)</label>
                      <input type="number" value={discountAmount} onChange={e => setDiscountAmount(e.target.value)}
                        className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black" />
                    </div>
                  </div>
                  <div className="flex justify-between border-t pt-3 text-xs">
                    <span className="text-gray-500 font-bold tracking-wide">최종 결제 예정</span>
                    <span className="font-black text-gray-900">{finalAmount.toLocaleString()}원</span>
                  </div>
                </>
              )}
              <button
                disabled={entryMutation.isPending}
                onClick={() => entryMutation.mutate()}
                className="w-full bg-black py-4 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
              >
                {entryMutation.isPending ? '응모 중...' : 'ENTER RAFFLE'}
              </button>
            </div>
          )}

          {!isAuthenticated && isOpen && (
            <Link to="/login" className="block w-full bg-black py-4 text-center text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 transition-colors">
              LOGIN TO ENTER
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}
