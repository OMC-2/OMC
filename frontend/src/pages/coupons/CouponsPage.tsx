import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { couponsApi } from '../../api/coupons'
import { Spinner } from '../../components/ui/Spinner'
import { useAuthStore } from '../../store/authStore'
import { formatDate } from '../../lib/utils'
import { Tag, Clock, CheckCircle, XCircle, Zap } from 'lucide-react'

const DEFAULT_IMAGES = [
  'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600&q=80', // 쇼핑백
  'https://images.unsplash.com/photo-1472851294608-062f824d29cc?w=600&q=80', // 쇼핑
  'https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&q=80',   // 선물
  'https://images.unsplash.com/photo-1483985988355-763728e1935b?w=600&q=80', // 리테일
]

function getCouponStatus(coupon: any): 'UPCOMING' | 'LIVE' | 'SOLD_OUT' | 'ENDED' {
  const now = new Date()
  if (coupon.startedAt && new Date(coupon.startedAt) > now) return 'UPCOMING'
  if (coupon.expiredAt && new Date(coupon.expiredAt) < now) return 'ENDED'
  if (coupon.remainingQuantity === 0) return 'SOLD_OUT'
  return 'LIVE'
}

const STATUS_CONFIG = {
  LIVE:      { label: 'LIVE',      className: 'bg-red-500 text-white' },
  UPCOMING:  { label: 'UPCOMING',  className: 'bg-blue-600 text-white' },
  SOLD_OUT:  { label: 'SOLD OUT',  className: 'bg-gray-800 text-gray-400' },
  ENDED:     { label: 'ENDED',     className: 'bg-gray-200 text-gray-400' },
}

export function CouponsPage() {
  const { isAuthenticated } = useAuthStore()
  const qc = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['coupons-all'],
    queryFn: () => couponsApi.getAll(),
  })

  const { data: myData } = useQuery({
    queryKey: ['my-coupons'],
    queryFn: () => couponsApi.getMyCoupons(),
    enabled: isAuthenticated,
  })

  const issueMutation = useMutation({
    mutationFn: (couponId: string) => couponsApi.issueCoupon(couponId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['coupons-all'] })
      qc.invalidateQueries({ queryKey: ['my-coupons'] })
      alert('쿠폰이 발급되었습니다!')
    },
    onError: (e: any) => {
      const msg = e?.response?.data?.message ?? ''
      if (e?.response?.status === 409 || msg.includes('이미')) {
        alert('이미 발급받은 쿠폰입니다.')
      } else if (msg.includes('소진') || msg.includes('품절') || msg.includes('수량')) {
        alert('쿠폰이 모두 소진되었습니다.')
      } else {
        alert(msg || '쿠폰 발급 실패. 다시 시도해주세요.')
      }
    },
  })

  const coupons: any[] = data?.data?.data?.content ?? data?.data?.data ?? []
  const myIssuedIds = new Set(
    (myData?.data?.data?.content ?? myData?.data?.data ?? []).map((c: any) => c.couponId)
  )

  return (
    <div className="mx-auto max-w-4xl">
      {/* Header */}
      <div className="mb-10">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-2">LIMITED OFFER</p>
        <h1 className="text-3xl font-black tracking-tight text-gray-900">COUPONS</h1>
        <p className="mt-2 text-sm text-gray-400">선착순 한정 수량 쿠폰을 발급받으세요.</p>
      </div>

      {isLoading && <Spinner className="py-20" />}

      {!isLoading && coupons.length === 0 && (
        <div className="py-20 text-center border border-gray-100">
          <Tag size={36} className="mx-auto mb-4 text-gray-200" />
          <p className="text-sm font-black tracking-wider text-gray-300">현재 발급 가능한 쿠폰이 없습니다</p>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {coupons.map((coupon: any) => {
          const status = getCouponStatus(coupon)
          const cfg = STATUS_CONFIG[status]
          const total = coupon.totalQuantity ?? 0
          const remaining = coupon.remainingQuantity ?? 0
          const usedPct = total > 0 ? Math.round(((total - remaining) / total) * 100) : 0
          const alreadyIssued = myIssuedIds.has(coupon.couponId)
          const canIssue = isAuthenticated && status === 'LIVE' && !alreadyIssued && !issueMutation.isPending

          const discountText = coupon.discountType === 'AMOUNT'
            ? `${Number(coupon.discountValue).toLocaleString()}원 할인`
            : `${(Number(coupon.discountValue) * 100).toFixed(0)}% 할인`

          const imgIdx = coupons.indexOf(coupon) % DEFAULT_IMAGES.length
          const imgUrl = coupon.imageUrl || DEFAULT_IMAGES[imgIdx]

          return (
            <div
              key={coupon.couponId}
              className={`border flex flex-col overflow-hidden ${
                status === 'LIVE' ? 'border-gray-900' : 'border-gray-100'
              }`}
            >
              {/* Top stripe */}
              <div className={`px-5 py-1.5 flex items-center justify-between ${
                status === 'LIVE' ? 'bg-black' : 'bg-gray-100'
              }`}>
                <span className={`text-[9px] font-black tracking-[0.2em] ${
                  status === 'LIVE' ? 'text-white' : 'text-gray-400'
                }`}>
                  {cfg.label}
                </span>
                {status === 'LIVE' && <Zap size={11} className="text-yellow-400" />}
              </div>

              {/* Image */}
              <div className="relative overflow-hidden bg-gray-100" style={{ height: '160px' }}>
                <img
                  src={imgUrl}
                  alt={coupon.name}
                  onError={(e) => {
                    e.currentTarget.style.display = 'none'
                    const fb = e.currentTarget.nextElementSibling as HTMLElement | null
                    if (fb) fb.style.display = 'flex'
                  }}
                  className={`w-full h-full object-cover ${
                    status !== 'LIVE' ? 'grayscale opacity-50' : ''
                  }`}
                />
                {/* 이미지 로드 실패 폴백 */}
                <div className="absolute inset-0 hidden items-center justify-center bg-gray-100">
                  <Tag size={32} className="text-gray-300" />
                </div>
                {status !== 'LIVE' && (
                  <div className="absolute inset-0 bg-white/40" />
                )}
              </div>

              {/* Body */}
              <div className="px-5 py-5 flex-1 flex flex-col gap-4">
                {/* Discount amount */}
                <div>
                  <p className="text-2xl font-black text-gray-900">{discountText}</p>
                  <p className="text-sm text-gray-500 mt-0.5 font-medium">{coupon.name}</p>
                  {coupon.maxDiscountAmount && coupon.discountType === 'RATE' && (
                    <p className="text-[10px] text-gray-400 mt-0.5">
                      최대 {Number(coupon.maxDiscountAmount).toLocaleString()}원
                    </p>
                  )}
                </div>

                {/* Progress bar */}
                <div>
                  <div className="flex justify-between text-[10px] mb-1.5">
                    <span className="font-bold text-gray-400 tracking-wide">잔여 수량</span>
                    <span className="font-black text-gray-900">
                      {remaining.toLocaleString()} / {total.toLocaleString()}
                    </span>
                  </div>
                  <div className="h-1.5 bg-gray-100 overflow-hidden">
                    <div
                      className={`h-full transition-all ${
                        remaining === 0 ? 'bg-gray-300' : usedPct > 80 ? 'bg-red-500' : 'bg-black'
                      }`}
                      style={{ width: `${Math.max(0, 100 - usedPct)}%` }}
                    />
                  </div>
                  {usedPct >= 80 && remaining > 0 && (
                    <p className="text-[10px] text-red-500 font-bold mt-1">
                      마감 임박! {remaining}개 남음
                    </p>
                  )}
                </div>

                {/* Time info */}
                <div className="space-y-1">
                  {status === 'UPCOMING' && coupon.startedAt && (
                    <div className="flex items-center gap-1.5 text-[10px] text-blue-500 font-bold">
                      <Clock size={11} />
                      발급 시작: {formatDate(coupon.startedAt)}
                    </div>
                  )}
                  {coupon.expiredAt && (
                    <div className="flex items-center gap-1.5 text-[10px] text-gray-400">
                      <Clock size={11} />
                      만료: {formatDate(coupon.expiredAt)}
                    </div>
                  )}
                </div>

                {/* CTA */}
                <div className="mt-auto pt-2">
                  {!isAuthenticated ? (
                    <Link
                      to="/login"
                      className="block w-full bg-black py-3 text-center text-[10px] font-black tracking-[0.2em] text-white hover:bg-red-500 transition-colors"
                    >
                      로그인 후 발급
                    </Link>
                  ) : alreadyIssued ? (
                    <div className="flex items-center justify-center gap-2 border border-green-200 py-3 text-[10px] font-black tracking-widest text-green-600">
                      <CheckCircle size={13} />
                      발급 완료
                    </div>
                  ) : status === 'SOLD_OUT' || status === 'ENDED' ? (
                    <div className="flex items-center justify-center gap-2 border border-gray-100 py-3 text-[10px] font-black tracking-widest text-gray-300">
                      <XCircle size={13} />
                      {status === 'SOLD_OUT' ? '소진 완료' : '기간 종료'}
                    </div>
                  ) : status === 'UPCOMING' ? (
                    <div className="border border-blue-100 py-3 text-center text-[10px] font-black tracking-widest text-blue-400">
                      발급 예정
                    </div>
                  ) : (
                    <button
                      onClick={() => issueMutation.mutate(coupon.couponId)}
                      disabled={!canIssue}
                      className="w-full bg-black py-3 text-[10px] font-black tracking-[0.2em] text-white hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
                    >
                      {issueMutation.isPending ? '발급 중...' : '선착순 발급받기'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {/* My coupons link */}
      {isAuthenticated && (
        <div className="mt-10 border-t border-gray-100 pt-8 text-center">
          <p className="text-xs text-gray-400">
            발급받은 쿠폰은{' '}
            <Link to="/mypage" className="font-black text-gray-900 underline hover:text-red-500 transition-colors">
              마이페이지
            </Link>
            에서 확인하세요.
          </p>
        </div>
      )}
    </div>
  )
}
