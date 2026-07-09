import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { rafflesApi } from '../../api/raffles'
import { productsApi } from '../../api/products'
import { couponsApi } from '../../api/coupons'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, getRaffleDisplayStatus } from '../../lib/utils'
import { getPlaceholderImage } from '../../lib/images'
import { useAuthStore } from '../../store/authStore'
import { ArrowLeft, Trophy, Clock, Timer, Users, Tag } from 'lucide-react'
import { useCountdown } from '../../hooks/useCountdown'
import { toast } from '../../components/ui/Toast'

export function RaffleDetailPage() {
  const { raffleId } = useParams<{ raffleId: string }>()
  const { isAuthenticated } = useAuthStore()
  const queryClient = useQueryClient()
  const [billingKeyId, setBillingKeyId]         = useState(() => localStorage.getItem('omc_billing_key') ?? '')
  const [originalAmount, setOriginalAmount]     = useState('0')
  const [discountAmount, setDiscountAmount]     = useState('0')
  const [selectedCouponId, setSelectedCouponId] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['raffle', raffleId],
    queryFn: () => rafflesApi.getById(raffleId!),
    enabled: !!raffleId,
  })
  const raffle = data?.data?.data

  const { data: productData, isLoading: productLoading } = useQuery({
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

  const { data: couponsData } = useQuery({
    queryKey: ['my-coupons'],
    queryFn: () => couponsApi.getMyCoupons(0, 50),
    enabled: isAuthenticated,
    retry: false,
  })
  const availableCoupons: any[] = (couponsData?.data?.data?.content ?? []).filter(
    (c: any) => c.status === 'AVAILABLE'
  )

  const displayStatus  = getRaffleDisplayStatus(raffle)
  const isOpen         = displayStatus === 'LIVE'
  const isUpcoming     = displayStatus === 'UPCOMING'
  const countdownTarget = isOpen ? raffle?.endedAt : isUpcoming ? raffle?.startedAt : undefined
  const countdown      = useCountdown(countdownTarget)
  const price          = product?.price ?? 0
  const imageUrl       = product?.imageUrl || getPlaceholderImage(0)

  // 쿠폰 할인 계산
  const selectedCoupon = availableCoupons.find((c: any) => c.couponId === selectedCouponId)
  const computedDiscount = (() => {
    if (!selectedCoupon) return parseFloat(discountAmount) || 0
    const orig = parseFloat(originalAmount) || 0
    if (selectedCoupon.discountType === 'AMOUNT') {
      return Number(selectedCoupon.discountValue)
    } else if (selectedCoupon.discountType === 'RATE') {
      const rate = orig * Number(selectedCoupon.discountValue)
      const max  = selectedCoupon.maxDiscountAmount ? Number(selectedCoupon.maxDiscountAmount) : Infinity
      return Math.min(rate, max)
    }
    return parseFloat(discountAmount) || 0
  })()
  const finalAmount = Math.max(0, (parseFloat(originalAmount) || 0) - computedDiscount)

  const entryMutation = useMutation({
    mutationFn: () => rafflesApi.enter(raffleId!, {
      billingKeyId: billingKeyId.trim() || null,
      couponId: selectedCouponId,
      originalAmount: parseFloat(originalAmount) || 0,
      discountAmount: computedDiscount,
      finalAmount,
    }),
    onSuccess: () => {
      toast.success('응모 완료!')
      queryClient.invalidateQueries({ queryKey: ['my-raffle-result', raffleId] })
      queryClient.invalidateQueries({ queryKey: ['raffle-count', raffleId] })
    },
    onError: (e: any) => {
      const msg = e?.response?.data?.message ?? ''
      if (msg.includes('패널티') || msg.includes('penalty')) {
        toast.error('패널티 기간 중 응모 불가: ' + msg)
      } else if (e?.response?.status === 409) {
        toast.error('이미 응모한 래플입니다.')
      } else {
        toast.error(msg || '응모 실패. 다시 시도해주세요.')
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
        {/* 이미지 */}
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

        {/* 상세 */}
        <div className="flex flex-col space-y-6">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">RAFFLE</p>
            <h1 className="text-3xl font-black tracking-tight text-gray-900 leading-tight">{raffle.name}</h1>
            {product && <p className="text-sm text-gray-400 mt-1">{product.name}</p>}
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-px bg-gray-100">
            {[
              { icon: Trophy, value: raffle.winnerCount, label: '당첨 인원' },
              { icon: Users,  value: participantsCount,  label: '참가자 수' },
              {
                icon: Trophy,
                value: productLoading ? '...' : price > 0 ? price.toLocaleString() + '원' : 'FREE',
                label: '정가',
              },
            ].map(({ icon: Icon, value, label }) => (
              <div key={label} className="bg-white p-4 text-center">
                <Icon size={16} className="mx-auto mb-1 text-gray-300" />
                <p className="text-xl font-black text-gray-900">{value}</p>
                <p className="text-[10px] text-gray-400 tracking-wide">{label}</p>
              </div>
            ))}
          </div>

          {/* 일정 */}
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
                  <Timer size={11} />{isUpcoming ? '시작까지' : '마감까지'}
                </span>
                <span className={`font-black tabular-nums ${isUpcoming ? 'text-blue-500' : 'text-red-500'}`}>
                  {countdown}
                </span>
              </div>
            )}
          </div>

          {/* 내 결과 */}
          {myResult && (
            <div className={`border p-4 ${myResult.status === 'WIN' ? 'bg-yellow-50 border-yellow-200' : 'bg-gray-50 border-gray-200'}`}>
              <p className="text-xs font-black tracking-wider text-gray-700">응모 완료</p>
              {myResult.status && myResult.status !== 'PENDING' && (
                <p className={`mt-1 text-sm font-black ${myResult.status === 'WIN' ? 'text-yellow-700' : 'text-gray-500'}`}>
                  결과: {myResult.status === 'WIN' ? '🎉 당첨!' : myResult.status === 'LOSE' ? '미당첨' : myResult.status}
                </p>
              )}
              {myResult.status === 'PENDING' && (
                <p className="mt-1 text-xs text-gray-500">추첨 대기 중</p>
              )}
              <Link
                to={`/raffles/${raffleId}/winners`}
                className="mt-2 inline-block text-xs font-black tracking-widest text-gray-900 underline hover:text-red-500 transition-colors"
              >
                당첨자 발표 확인 →
              </Link>
            </div>
          )}

          {/* 진행 예정 안내 */}
          {isUpcoming && (
            <div className="bg-blue-50 border border-blue-200 p-4 text-center">
              <p className="text-xs font-black tracking-wider text-blue-700">응모 예정</p>
              <p className="mt-1 text-xs text-blue-500">래플 시작 후 응모할 수 있습니다.</p>
            </div>
          )}

          {/* 응모 폼 */}
          {isAuthenticated && isOpen && !myResult && (
            <div className="border border-gray-200 p-6 space-y-4">
              <p className="text-xs font-black tracking-widest text-gray-900">APPLY</p>
              {productLoading && <p className="text-xs text-gray-400">상품 정보 불러오는 중...</p>}

              {/* 빌링키 */}
              <div>
                <label className="block text-xs font-bold text-gray-500 mb-1">
                  빌링키 ID <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  placeholder="빌링키 ID"
                  value={billingKeyId}
                  onChange={e => setBillingKeyId(e.target.value)}
                  className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black"
                />
                <p className="mt-1 text-[10px] text-gray-400">마이페이지에서 결제 수단 등록 후 발급되는 ID</p>
              </div>

              {/* 쿠폰 */}
              <div>
                <label className="flex items-center gap-1.5 text-xs font-bold text-gray-500 mb-1">
                  <Tag size={11} />쿠폰 (선택)
                </label>
                {availableCoupons.length === 0 ? (
                  <p className="text-[10px] text-gray-400 border border-dashed border-gray-200 px-3 py-2">
                    사용 가능한 쿠폰이 없습니다
                  </p>
                ) : (
                  <select
                    value={selectedCouponId ?? ''}
                    onChange={e => setSelectedCouponId(e.target.value || null)}
                    className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black bg-white"
                  >
                    <option value="">쿠폰 선택 안함</option>
                    {availableCoupons.map((c: any) => (
                      <option key={c.couponId} value={c.couponId}>
                        {c.name} ({c.discountType === 'AMOUNT'
                          ? `${Number(c.discountValue).toLocaleString()}원 할인`
                          : `${c.discountValue}% 할인${c.maxDiscountAmount ? ` (최대 ${Number(c.maxDiscountAmount).toLocaleString()}원)` : ''}`
                        })
                      </option>
                    ))}
                  </select>
                )}
              </div>

              {/* 금액 */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-gray-500 mb-1">원가</label>
                  <input
                    type="number"
                    value={originalAmount}
                    onChange={e => setOriginalAmount(e.target.value)}
                    className="w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-500 mb-1">
                    할인 {selectedCoupon ? `(${selectedCoupon.name})` : '(수동 입력)'}
                  </label>
                  <input
                    type="number"
                    value={selectedCoupon ? computedDiscount : discountAmount}
                    readOnly={!!selectedCoupon}
                    onChange={e => !selectedCoupon && setDiscountAmount(e.target.value)}
                    className={`w-full border border-gray-200 px-3 py-2 text-sm outline-none focus:border-black ${selectedCoupon ? 'bg-gray-50 text-gray-500' : ''}`}
                  />
                </div>
              </div>

              {/* 최종 금액 */}
              <div className="border-t border-gray-100 pt-3 flex justify-between items-center">
                <span className="text-xs font-bold text-gray-500">최종 결제 금액</span>
                <span className="text-lg font-black text-gray-900">{finalAmount.toLocaleString()}원</span>
              </div>

              {/* 응모 버튼 */}
              <button
                onClick={() => entryMutation.mutate()}
                disabled={entryMutation.isPending || !billingKeyId.trim()}
                className="w-full bg-black py-3.5 text-[11px] font-black tracking-[0.2em] text-white transition-colors hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400"
              >
                {entryMutation.isPending ? '응모 중...' : 'APPLY NOW'}
              </button>
              {!billingKeyId.trim() && (
                <p className="text-center text-[10px] text-red-400">빌링키 ID를 입력해주세요</p>
              )}
            </div>
          )}

          {/* 비로그인 상태 */}
          {!isAuthenticated && isOpen && (
            <div className="border border-gray-200 p-6 text-center space-y-3">
              <p className="text-sm font-black tracking-wider">로그인이 필요합니다</p>
              <Link
                to="/login"
                className="inline-block bg-black px-6 py-3 text-[11px] font-black tracking-widest text-white hover:bg-red-500 transition-colors"
              >
                로그인 →
              </Link>
            </div>
          )}

          {/* 종료된 래플 */}
          {!isOpen && !isUpcoming && !myResult && (
            <div className="border border-gray-100 p-6 text-center">
              <p className="text-xs font-black tracking-wider text-gray-400">응모 종료</p>
              <Link
                to={`/raffles/${raffleId}/winners`}
                className="mt-3 inline-block text-xs font-black tracking-widest text-gray-900 underline hover:text-red-500 transition-colors"
              >
                당첨자 확인 →
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
