import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { rafflesApi } from '../../api/raffles'
import { productsApi } from '../../api/products'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, getRaffleDisplayStatus } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'
import { useAuthStore } from '../../store/authStore'
import { ArrowLeft, Trophy, Clock, Timer, Users } from 'lucide-react'
import { useCountdown } from '../../hooks/useCountdown'

export function RaffleDetailPage() {
  const { raffleId } = useParams<{ raffleId: string }>()
  const { isAuthenticated } = useAuthStore()
  const queryClient = useQueryClient()

  const [billingKeyId, setBillingKeyId] = useState(() => localStorage.getItem('omc_billing_key') ?? '')
  const [originalAmount, setOriginalAmount] = useState('0')
  const [discountAmount, setDiscountAmount] = useState('0')

  const { data, isLoading } = useQuery({
    queryKey: ['raffle', raffleId],
    queryFn: () => rafflesApi.getById(raffleId!),
    enabled: !!raffleId,
  })
  const raffle = data?.data?.data

  const { data: productData, isLoading: productLoading, isError: productError } = useQuery({
    queryKey: ['product', raffle?.productId],
    queryFn: () => productsApi.getById(raffle!.productId),
    enabled: !!raffle?.productId,
    retry: false,
  })
  const product = productData?.data?.data

  useEffect(() => {
    if (product?.price != null && product.price > 0) {
      setOriginalAmount(String(product.price))
    }
  }, [product?.price])

  const { data: myResultData } = useQuery({
    queryKey: ['my-raffle-result', raffleId],
    queryFn: () => rafflesApi.getMyResult(raffleId!),
    enabled: !!raffleId && isAuthenticated,
    retry: false,
  })
  const myResult = myResultData?.data?.data

  const { data: countData } = useQuery({
    queryKey: ['raffle-count', raffleId],
    queryFn: () => rafflesApi.getParticipantsCount(raffleId!),
    enabled: !!raffleId,
    retry: false,
  })
  const participantsCount: number = countData?.data?.data ?? 0

  const displayStatus = getRaffleDisplayStatus(raffle)
  const isOpen = displayStatus === 'LIVE'
  const isUpcoming = displayStatus === 'UPCOMING'
  const countdownTarget = isOpen ? raffle?.endedAt : isUpcoming ? raffle?.startedAt : undefined
  const countdown = useCountdown(countdownTarget)

  const price = product?.price ?? 0
  const imageUrl = product?.imageUrl || getPlaceholderImage(0)
  const finalAmount = Math.max(0, (parseFloat(originalAmount) || 0) - (parseFloat(discountAmount) || 0))

  const entryMutation = useMutation({
    mutationFn: () => rafflesApi.enter(raffleId!, {
      billingKeyId: billingKeyId.trim() || null,
      couponId: null,
      originalAmount: parseFloat(originalAmount) || 0,
      discountAmount: parseFloat(discountAmount) || 0,
      finalAmount,
    }),
    onSuccess: () => {
      alert('응모 완료! 추첨 결과를 기다려주세요.')
      queryClient.invalidateQueries({ queryKey: ['my-raffle-result', raffleId] })
    },
    onError: (e: any) => {
      const msg = e?.response?.data?.message ?? ''
      if (msg.includes('입력값') || msg.includes('billing') || e?.response?.status === 400) {
        alert('응모 실패: ' + (msg || '결제 정보(빌링키/금액)를 확인해주세요.'))
      } else {
        alert(msg || '응모 실패. 이미 응모했거나 래플이 종료되었습니다.')
      }
    },
  })

  if (isLoading) return <Spinner className="py-20" />
  if (!raffle) return <p className="text-center py-20 text-xs tracking-widest text-gray-400">NOT FOUND</p>

  return (
    <div className="mx-auto max-w-5xl">
      <Link to="/raffles" className="mb-8 inline-flex items-center gap-2 text-xs font-bold tracking-wider text-gray-400 hover:text-black transition-colors">
        <ArrowLeft size={14} />BACK
      </Link>

      <div className="grid gap-12 md:grid-cols-2">
        <div className="relative aspect-square overflow-hidden bg-gray-50">
          <img
            src={imageUrl}
            alt={raffle.name}
            onError={(e) => { const t = e.currentTarget; t.onerror = null; t.src = getPlaceholderImage(0) }}
            className="h-full w-full object-cover"
          />
          <div className="absolute top-5 left-5 flex flex-col gap-2">
            <span className={`px-3 py-1.5 text-[10px] font-black tracking-[0.2em] ${
              isOpen ? 'bg-red-500 text-white' : isUpcoming ? 'bg-blue-600 text-white' : 'bg-black/60 text-white/60'
            }`}>
              {isOpen ? 'RAFFLE LIVE' : isUpcoming ? '진행 예정' : displayStatus === 'DRAWN' ? 'DRAWN' : 'ENDED'}
            </span>
            {(isOpen || isUpcoming) && countdown && (
              <span className="flex items-center gap-1.5 bg-black/80 px-3 py-1.5 text-[11px] font-black text-white tabular-nums">
                <Timer size={11} className={isUpcoming ? 'text-blue-400' : 'text-red-400'} />
                {isUpcoming ? '시작까지 ' : ''}{countdown}
              </span>
            )}
          </div>
        </div>

        <div className="flex flex-col space-y-6">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">RAFFLE</p>
            <h1 className="text-3xl font-black tracking-tight text-gray-900 leading-tight">{raffle.name}</h1>
            {product && <p className="text-sm text-gray-400 mt-1">{product.name}</p>}
          </div>

          <div className="grid grid-cols-3 gap-px bg-gray-100">
            {[
              { icon: Trophy, value: raffle.winnerCount, label: '당첨 인원' },
              { icon: Users, value: participantsCount, label: '참가자 수' },
              {
                icon: Trophy,
                value: productLoading ? '...' : price > 0 ? price.toLocaleString() + '원' : 'FREE',
                label: '상품 가격',
              },
            ].map(({ icon: Icon, value, label }) => (
              <div key={label} className="bg-white p-4 text-center">
                <Icon size={16} className="mx-auto mb-1 text-gray-300" />
                <p className="text-xl font-black text-gray-900">{value}</p>
                <p className="text-[10px] text-gray-400 tracking-wide">{label}</p>
              </div>
            ))}
          </div>

          <div className="space-y-2 border border-gray-100 p-4">
            {raffle.startedAt && (
              <div className="flex justify-between text-xs">
                <span className="flex items-center gap-1.5 font-bold text-gray-400 tracking-wide">
                  <Clock size={11} />START
                </span>
                <span className="font-medium">{formatDate(raffle.startedAt)}</span>
              </div>
            )}
            {raffle.endedAt && (
              <div className="flex justify-between text-xs">
                <span className="flex items-center gap-1.5 font-bold text-gray-400 tracking-wide">
                  <Clock size={11} />DEADLINE
                </span>
                <span className={`font-medium ${isOpen ? 'text-red-500' : ''}`}>{formatDate(raffle.endedAt)}</span>
              </div>
            )}
            {(isOpen || isUpcoming) && countdown && (
              <div className="mt-1 flex justify-between text-xs border-t border-gray-100 pt-2">
                <span className={`flex items-center gap-1.5 font-bold tracking-wide ${isUpcoming ? 'text-blue-400' : 'text-red-400'}`}>
                  <Timer size={11} />{isUpcoming ? '시작까지' : '남은 시간'}
                </span>
                <span className={`font-black tabular-nums ${isUpcoming ? 'text-blue-500' : 'text-red-500'}`}>
                  {countdown}
                </span>
              </div>
            )}
          </div>

          {myResult && (
            <div className="bg-green-50 border border-green-200 p-4">
              <p className="text-xs font-black tracking-wider text-green-700">응모 완료</p>
              {myResult.result && (
                <p className="mt-1 text-xs text-green-600">
                  결과: {myResult.result === 'WINNER' ? '🎉 당첨!' : myResult.result === 'LOSER' ? '아쉽게 탈락' : '집계 중'}
                </p>
              )}
            </div>
          )}

          {isUpcoming && (
            <div className="bg-blue-50 border border-blue-200 p-4 text-center">
              <p className="text-xs font-black tracking-wider text-blue-700">아직 응모 기간이 아닙니다</p>
              <p className="mt-1 text-xs text-blue-500">시작 시간에 다시 접속해주세요.</p>
            </div>
          )}

          {isAuthenticated && isOpen && !myResult && (
            <div className="border border-gray-200 p-6 space-y-4">
              <p className="text-xs font-black tracking-widest text-gray-900">APPLY</p>
              {productLoading && <p className="text-xs text-gray-400">상품 정보 로딩 중...</p>}
              <div>
                <label className="block text-xs font-bold text-gray-500 mb-1">
                  빌링키 ID <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  placeholder="등록된 결제 수단 ID"
                  value={billingKeyId}
                  onChange={e => setBillingKeyId(e.target.value)}
                  className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-gray-500 mb-1">상품 금액 (원)</label>
                  <input
                    type="number"
                    value={originalAmount}
                    onChange={e => setOriginalAmount(e.target.value)}
                    className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-500 mb-1">할인 금액 (원)</label>
                  <input
                    type="number"
                    value={discountAmount}
                    onChange={e => setDiscountAmount(e.target.value)}
                    className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black"
                  />
                </div>
              </div>
              <div className="flex justify-between border-t pt-3 text-xs">
                <span className="text-gray-500 font-bold tracking-wide">최종 결제 예정</span>
                <span className="font-black text-gray-900">{finalAmount.toLocaleString()}원</span>
              </div>
              {productError && (
                <div className="bg-yellow-50 border border-yellow-200 px-4 py-3 text-xs text-yellow-700">
                  상품 정보를 불러올 수 없습니다. 래플 데이터를 확인해주세요.
                </div>
              )}
              <button
                disabled={entryMutation.isPending || productLoading || !!productError || !billingKeyId.trim()}
                onClick={() => entryMutation.mutate()}
                className="w-full bg-black py-4 text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
              >
                {entryMutation.isPending ? '응모 중...' : productLoading ? '로딩 중...' : productError ? '상품 오류' : 'ENTER RAFFLE'}
              </button>
              {!billingKeyId.trim() && !productError && (
                <p className="text-[10px] text-red-400 text-center">
                  빌링키 없음 —{' '}
                  <a href="/mypage" className="underline">마이페이지</a>에서 카드 등록 후 응모하세요
                </p>
              )}
            </div>
          )}

          {!isAuthenticated && isOpen && (
            <Link
              to="/login"
              className="block w-full bg-black py-4 text-center text-xs font-black tracking-[0.2em] text-white hover:bg-red-500 transition-colors"
            >
              LOGIN TO ENTER
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}
