import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { rafflesApi } from '../api/raffles'
import { dropsApi } from '../api/drops'
import { couponsApi } from '../api/coupons'
import { productsApi } from '../api/products'
import { getPlaceholderImage } from '../lib/images'
import { ArrowRight, ChevronLeft, ChevronRight, Tag, Ticket, ShoppingBag, Zap } from 'lucide-react'

const SLIDE_MS = 4500

// ── 이미지 폴백 헬퍼 ──────────────────────────────────────────
function safeImg(url: string | undefined, fallbackIdx: number) {
  return url?.startsWith('http') ? url : getPlaceholderImage(fallbackIdx)
}

// ── 캐러셀 ───────────────────────────────────────────────────
function Carousel({ slides }: { slides: React.ReactNode[] }) {
  const [idx, setIdx] = useState(0)

  useEffect(() => {
    const t = setInterval(() => setIdx(i => (i + 1) % slides.length), SLIDE_MS)
    return () => clearInterval(t)
  }, [slides.length])

  return (
    <div className="relative w-full overflow-hidden bg-black" style={{ height: 440 }}>
      {slides.map((slide, i) => (
        <div
          key={i}
          className="absolute inset-0 transition-opacity duration-700"
          style={{ opacity: i === idx ? 1 : 0, pointerEvents: i === idx ? 'auto' : 'none' }}
        >
          {slide}
        </div>
      ))}
      <button
        onClick={() => setIdx(i => (i - 1 + slides.length) % slides.length)}
        className="absolute left-4 top-1/2 -translate-y-1/2 z-20 h-9 w-9 flex items-center justify-center bg-black/40 text-white hover:bg-black/70 transition-colors"
      >
        <ChevronLeft size={18} />
      </button>
      <button
        onClick={() => setIdx(i => (i + 1) % slides.length)}
        className="absolute right-4 top-1/2 -translate-y-1/2 z-20 h-9 w-9 flex items-center justify-center bg-black/40 text-white hover:bg-black/70 transition-colors"
      >
        <ChevronRight size={18} />
      </button>
      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-20 flex gap-1.5">
        {slides.map((_, i) => (
          <button
            key={i}
            onClick={() => setIdx(i)}
            className={`h-1.5 rounded-full transition-all duration-300 ${i === idx ? 'w-6 bg-white' : 'w-1.5 bg-white/30'}`}
          />
        ))}
      </div>
    </div>
  )
}

// ── 슬라이드 컴포넌트 ─────────────────────────────────────────
function SlideRaffle({ raffle, imgUrl }: { raffle: any; imgUrl: string }) {
  return (
    <div className="relative h-full overflow-hidden">
      <img src={imgUrl} alt="" className="absolute inset-0 h-full w-full object-cover opacity-40"
        onError={(e) => { e.currentTarget.src = getPlaceholderImage(1) }} />
      <div className="absolute inset-0 bg-gradient-to-r from-black via-black/60 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
      <div className="relative h-full flex flex-col justify-center px-12 md:px-20 max-w-2xl">
        <span className="mb-4 inline-block bg-red-500 px-3 py-1 text-[9px] font-black tracking-[0.3em] text-white w-fit">RAFFLE</span>
        <h2 className="text-3xl md:text-4xl font-black text-white leading-tight mb-3">
          {raffle?.name ?? '래플 응모하기'}
        </h2>
        <p className="text-sm text-white/40 mb-8">
          {raffle ? `당첨 인원 ${raffle.winnerCount}명 · 공정 추첨` : '한정판을 공정하게 획득하는 방법'}
        </p>
        <Link to="/raffles"
          className="inline-flex items-center gap-2 bg-white px-7 py-3 text-[11px] font-black tracking-[0.25em] text-black hover:bg-red-500 hover:text-white transition-colors w-fit">
          응모하기 <ArrowRight size={12} />
        </Link>
      </div>
    </div>
  )
}

function SlideDrop({ drop, product, imgUrl }: { drop: any; product: any; imgUrl: string }) {
  return (
    <div className="relative h-full overflow-hidden">
      <img src={imgUrl} alt="" className="absolute inset-0 h-full w-full object-cover opacity-40"
        onError={(e) => { e.currentTarget.src = getPlaceholderImage(2) }} />
      <div className="absolute inset-0 bg-gradient-to-r from-black via-black/60 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
      <div className="relative h-full flex flex-col justify-center px-12 md:px-20 max-w-2xl">
        <span className="mb-4 inline-block bg-blue-600 px-3 py-1 text-[9px] font-black tracking-[0.3em] text-white w-fit">LIMITED DROP</span>
        <h2 className="text-3xl md:text-4xl font-black text-white leading-tight mb-3">
          {product?.name ?? 'DROP 시작'}
        </h2>
        <p className="text-sm text-white/40 mb-8">
          {drop ? `한정 수량 ${drop.totalQty ?? ''}개 · 선착순 마감` : '선착순 한정 수량 구매'}
        </p>
        <Link to="/drops"
          className="inline-flex items-center gap-2 bg-white px-7 py-3 text-[11px] font-black tracking-[0.25em] text-black hover:bg-blue-600 hover:text-white transition-colors w-fit">
          지금 구매 <ArrowRight size={12} />
        </Link>
      </div>
    </div>
  )
}

function SlideCoupon({ coupon }: { coupon: any }) {
  const amount = coupon?.discountType === 'AMOUNT'
    ? `${Number(coupon.discountValue).toLocaleString()}원`
    : coupon
    ? `${(Number(coupon.discountValue) * 100).toFixed(0)}%`
    : null

  return (
    <div className="relative h-full overflow-hidden" style={{ background: '#0d0d14' }}>
      <div className="absolute inset-0" style={{ background: 'radial-gradient(ellipse at 75% 50%, rgba(234,179,8,0.18) 0%, transparent 65%)' }} />
      <div className="absolute right-10 top-1/2 -translate-y-1/2 opacity-[0.04] pointer-events-none">
        <Tag size={320} />
      </div>
      <div className="relative h-full flex flex-col justify-center px-12 md:px-20 max-w-2xl">
        <span className="mb-4 inline-block bg-yellow-400 px-3 py-1 text-[9px] font-black tracking-[0.3em] text-black w-fit">COUPON</span>
        {amount ? (
          <>
            <h2 className="text-5xl md:text-6xl font-black text-white leading-none">{amount}</h2>
            <p className="text-xl font-black text-yellow-400/60 mt-1 mb-3">즉시 할인 쿠폰</p>
          </>
        ) : (
          <h2 className="text-4xl font-black text-white mb-3">선착순 쿠폰</h2>
        )}
        <p className="text-sm text-white/30 mb-8">
          {coupon ? `${coupon.remainingQuantity ?? coupon.totalQuantity}개 남음 · 선착순 발급` : '한정 수량 할인 쿠폰'}
        </p>
        <Link to="/coupons"
          className="inline-flex items-center gap-2 bg-yellow-400 px-7 py-3 text-[11px] font-black tracking-[0.25em] text-black hover:bg-yellow-300 transition-colors w-fit">
          쿠폰 받기 <ArrowRight size={12} />
        </Link>
      </div>
    </div>
  )
}

// ── 카드 공통 ─────────────────────────────────────────────────
function ItemCard({ to, imgUrl, fallbackIdx, badge, badgeColor, title, sub }: {
  to: string; imgUrl: string; fallbackIdx: number
  badge: string; badgeColor: string; title: string; sub: string
}) {
  return (
    <Link to={to} className="group relative aspect-[3/4] overflow-hidden bg-gray-100 block">
      <img
        src={imgUrl}
        alt={title}
        className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
        onError={(e) => { e.currentTarget.src = getPlaceholderImage(fallbackIdx) }}
      />
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />
      <div className="absolute top-3 left-3">
        <span className={`px-2 py-0.5 text-[9px] font-black tracking-widest ${badgeColor}`}>{badge}</span>
      </div>
      <div className="absolute bottom-0 left-0 right-0 p-4">
        <p className="text-xs font-bold text-white leading-snug line-clamp-2">{title}</p>
        <p className="text-[10px] text-white/40 mt-1">{sub}</p>
      </div>
    </Link>
  )
}

// ── 메인 ─────────────────────────────────────────────────────
export function HomePage() {
  const { data: rafflesData } = useQuery({ queryKey: ['raffles-home'], queryFn: () => rafflesApi.getAll() })
  const { data: dropsData }   = useQuery({ queryKey: ['drops-home'],   queryFn: () => dropsApi.getAll() })
  const { data: couponsData } = useQuery({ queryKey: ['coupons-home'], queryFn: () => couponsApi.getAll() })
  const { data: productsData } = useQuery({ queryKey: ['products-home'], queryFn: () => productsApi.getAll(0, 100) })

  const raffles = rafflesData?.data?.data?.content ?? []
  const drops   = dropsData?.data?.data?.content   ?? []
  const coupons = couponsData?.data?.data?.content  ?? couponsData?.data?.data ?? []

  const productsArr: any[] = productsData?.data?.data?.content ?? productsData?.data?.data ?? []
  const productMap: Record<string, any> = {}
  for (const p of productsArr) productMap[p.productId] = p

  const liveRaffle = raffles.find((r: any) => r.status === 'OPEN') ?? raffles[0]
  const liveDrop   = drops.find((d: any)   => d.status === 'OPEN') ?? drops[0]
  const liveCoupon = coupons.find((c: any) => (c.remainingQuantity ?? 0) > 0) ?? coupons[0]

  const raffleProduct = productMap[liveRaffle?.productId]
  const dropProduct   = productMap[liveDrop?.productId]

  const slides = [
    <SlideRaffle key="r" raffle={liveRaffle} imgUrl={safeImg(raffleProduct?.imageUrl, 1)} />,
    <SlideDrop   key="d" drop={liveDrop} product={dropProduct} imgUrl={safeImg(dropProduct?.imageUrl, 2)} />,
    <SlideCoupon key="c" coupon={liveCoupon} />,
  ]

  return (
    <div className="-mx-4 lg:-mx-8 -mt-8">

      {/* HERO */}
      <section className="relative bg-black overflow-hidden" style={{ height: 180 }}>
        <div className="absolute inset-0" style={{ background: 'radial-gradient(ellipse at 60% 50%, rgba(220,38,38,0.14) 0%, transparent 65%)' }} />
        <div className="relative h-full max-w-7xl mx-auto px-8 lg:px-16 flex items-center justify-between">
          <div>
            <p className="text-[9px] font-bold tracking-[0.5em] text-red-400/50 mb-2">LIMITED EDITION PLATFORM</p>
            <h1 className="text-5xl md:text-6xl font-black tracking-tighter text-white leading-none">
              OMC
            </h1>
            <p className="mt-2 text-xs text-white/25 tracking-widest">한정판을 위한 가장 공정한 방법</p>
          </div>
          <div className="hidden md:flex items-center gap-2">
            <Link to="/raffles" className="bg-white px-6 py-2.5 text-[11px] font-black tracking-widest text-black hover:bg-red-500 hover:text-white transition-colors">
              RAFFLE
            </Link>
            <Link to="/drops" className="border border-white/20 px-6 py-2.5 text-[11px] font-black tracking-widest text-white/50 hover:border-white hover:text-white transition-colors">
              DROPS
            </Link>
          </div>
        </div>
      </section>

      {/* 기능 스트립 */}
      <section className="border-y border-gray-100 bg-white">
        <div className="max-w-7xl mx-auto grid grid-cols-3 divide-x divide-gray-100">
          {[
            { icon: Ticket,      label: 'FAIR RAFFLE',   desc: '투명한 추첨' },
            { icon: ShoppingBag, label: 'LIMITED DROP',  desc: '선착순 한정' },
            { icon: Tag,         label: 'COUPON',        desc: '선착순 발급' },
          ].map(({ icon: Icon, label, desc }) => (
            <div key={label} className="flex flex-col items-center py-4 text-center">
              <Icon size={15} className="mb-1 text-gray-300" />
              <p className="text-[10px] font-black tracking-widest">{label}</p>
              <p className="text-[10px] text-gray-400">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 자동 캐러셀 */}
      <Carousel slides={slides} />

      {/* 본문 섹션들 */}
      <div className="max-w-7xl mx-auto px-4 lg:px-8">

        {/* RAFFLE */}
        {raffles.length > 0 && (
          <section className="py-12">
            <div className="flex items-end justify-between mb-6">
              <div>
                <p className="text-[10px] font-bold tracking-[0.4em] text-red-500 mb-1">NOW LIVE</p>
                <h2 className="text-2xl font-black tracking-tight">RAFFLE</h2>
              </div>
              <Link to="/raffles" className="flex items-center gap-1 text-[10px] font-black tracking-widest text-gray-300 hover:text-black transition-colors">
                VIEW ALL <ArrowRight size={11} />
              </Link>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {raffles.slice(0, 4).map((r: any, i: number) => {
                const p = productMap[r.productId]
                return (
                  <ItemCard
                    key={r.raffleId}
                    to={`/raffles/${r.raffleId}`}
                    imgUrl={safeImg(p?.imageUrl, i)}
                    fallbackIdx={i}
                    badge={r.status === 'OPEN' ? 'OPEN' : r.status ?? 'SCHEDULED'}
                    badgeColor={r.status === 'OPEN' ? 'bg-red-500 text-white' : 'bg-black/50 text-white/50'}
                    title={r.name}
                    sub={`당첨 ${r.winnerCount}명`}
                  />
                )
              })}
            </div>
          </section>
        )}

        {/* DROPS */}
        {drops.filter((d: any) => !!productMap[d.productId]).length > 0 && (
          <section className="pb-12">
            <div className="flex items-end justify-between mb-6">
              <div>
                <p className="text-[10px] font-bold tracking-[0.4em] text-blue-500 mb-1">LIVE NOW</p>
                <h2 className="text-2xl font-black tracking-tight">DROPS</h2>
              </div>
              <Link to="/drops" className="flex items-center gap-1 text-[10px] font-black tracking-widest text-gray-300 hover:text-black transition-colors">
                VIEW ALL <ArrowRight size={11} />
              </Link>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {drops.filter((d: any) => !!productMap[d.productId]).slice(0, 4).map((d: any, i: number) => {
                const p = productMap[d.productId]
                return (
                  <ItemCard
                    key={d.dropId}
                    to={`/drops/${d.dropId}`}
                    imgUrl={safeImg(p?.imageUrl, i + 4)}
                    fallbackIdx={i + 4}
                    badge={d.status === 'OPEN' ? 'LIVE' : d.status ?? 'SCHEDULED'}
                    badgeColor={d.status === 'OPEN' ? 'bg-blue-600 text-white' : 'bg-black/50 text-white/50'}
                    title={p?.name ?? ''}
                    sub={`잔여 ${d.totalQty ?? '?'}개`}
                  />
                )
              })}
            </div>
          </section>
        )}

        {/* COUPONS */}
        {coupons.length > 0 && (
          <section className="pb-16">
            <div className="flex items-end justify-between mb-6">
              <div>
                <p className="text-[10px] font-bold tracking-[0.4em] text-yellow-500 mb-1">LIMITED OFFER</p>
                <h2 className="text-2xl font-black tracking-tight">COUPONS</h2>
              </div>
              <Link to="/coupons" className="flex items-center gap-1 text-[10px] font-black tracking-widest text-gray-300 hover:text-black transition-colors">
                VIEW ALL <ArrowRight size={11} />
              </Link>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {coupons.slice(0, 3).map((c: any) => {
                const discountLabel = c.discountType === 'AMOUNT'
                  ? `${Number(c.discountValue).toLocaleString()}원 할인`
                  : `${(Number(c.discountValue) * 100).toFixed(0)}% 할인`
                const remaining = c.remainingQuantity ?? c.totalQuantity ?? 0
                const total     = c.totalQuantity ?? 0
                const pct       = total > 0 ? Math.round(((total - remaining) / total) * 100) : 0
                const live      = remaining > 0

                return (
                  <Link
                    key={c.couponId}
                    to="/coupons"
                    className={`group border p-5 flex flex-col gap-3 transition-colors ${
                      live ? 'border-gray-200 hover:border-black' : 'border-gray-100 opacity-60'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-lg font-black text-gray-900">{discountLabel}</p>
                        <p className="text-[11px] text-gray-400 mt-0.5">{c.name}</p>
                      </div>
                      <Tag size={18} className={`shrink-0 mt-0.5 transition-colors ${live ? 'text-gray-200 group-hover:text-yellow-400' : 'text-gray-100'}`} />
                    </div>
                    <div>
                      <div className="flex justify-between text-[10px] mb-1.5 text-gray-400">
                        <span>잔여 수량</span>
                        <span className="font-black text-gray-700">{remaining.toLocaleString()} / {total.toLocaleString()}</span>
                      </div>
                      <div className="h-1 bg-gray-100 overflow-hidden">
                        <div
                          className={`h-full transition-all ${!live ? 'bg-gray-200' : pct > 80 ? 'bg-red-500' : 'bg-black'}`}
                          style={{ width: `${Math.max(0, 100 - pct)}%` }}
                        />
                      </div>
                      {pct > 80 && live && (
                        <p className="text-[10px] text-red-500 font-bold mt-1">마감 임박!</p>
                      )}
                    </div>
                    <span className={`text-[10px] font-black tracking-widest transition-colors ${live ? 'text-gray-300 group-hover:text-black' : 'text-gray-200'}`}>
                      {live ? '발급받기 →' : '소진 완료'}
                    </span>
                  </Link>
                )
              })}
            </div>
          </section>
        )}

      </div>
    </div>
  )
}
