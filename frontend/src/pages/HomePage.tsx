import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { rafflesApi } from '../api/raffles'
import { dropsApi } from '../api/drops'
import { productsApi } from '../api/products'
import { getPlaceholderImage, HERO_IMAGES } from '../lib/images'
import { ArrowRight, Zap, ShoppingBag, Ticket } from 'lucide-react'
import { RaffleCard } from '../components/RaffleCard'

export function HomePage() {
  const { data: rafflesData } = useQuery({ queryKey: ['raffles-home'], queryFn: () => rafflesApi.getAll() })
  const { data: dropsData } = useQuery({ queryKey: ['drops-home'], queryFn: () => dropsApi.getAll() })

  const { data: productsData } = useQuery({ queryKey: ['products-home'], queryFn: () => productsApi.getAll(0, 100) })
  const raffles = (rafflesData?.data?.data?.content ?? []).slice(0, 4)
  const drops = (dropsData?.data?.data?.content ?? []).slice(0, 4)
  const productsArr = productsData?.data?.data?.content ?? productsData?.data?.data ?? []
  const productMap: Record<string, any> = {}
  for (const p of productsArr) productMap[p.productId] = p

  return (
    <div className="-mx-4 lg:-mx-8 -mt-8">

      {/* HERO */}
      <section className="relative h-[92vh] min-h-[600px] overflow-hidden bg-black">
        <img src={HERO_IMAGES.main} alt="" className="absolute inset-0 h-full w-full object-cover opacity-50 scale-105" />
        <div className="absolute inset-0" style={{ background: 'linear-gradient(to bottom, rgba(0,0,0,0.2) 0%, transparent 40%, rgba(0,0,0,0.85) 100%)' }} />
        <div className="absolute inset-0" style={{ background: 'radial-gradient(ellipse at 70% 50%, rgba(220,38,38,0.12) 0%, transparent 60%)' }} />

        <div className="relative flex h-full flex-col items-center justify-center text-center px-4">
          <div className="mb-6 flex items-center gap-3">
            <span className="h-px w-8 bg-red-500/60" />
            <p className="text-[10px] font-bold tracking-[0.5em] text-red-400/80">LIMITED EDITION PLATFORM</p>
            <span className="h-px w-8 bg-red-500/60" />
          </div>
          <h1 className="text-7xl md:text-[10rem] font-black tracking-tighter text-white leading-none">
            SOLD<span className="text-red-500">OUT</span>
          </h1>
          <p className="mt-8 text-sm md:text-base text-white/40 max-w-sm tracking-wide">
            한정판을 위한 가장 공정한 방법
          </p>
          <div className="mt-12 flex gap-3">
            <Link to="/raffles" className="bg-white px-10 py-4 text-[11px] font-black tracking-[0.3em] text-black hover:bg-red-500 hover:text-white transition-all duration-200">
              RAFFLE
            </Link>
            <Link to="/drops" className="border border-white/30 px-10 py-4 text-[11px] font-black tracking-[0.3em] text-white/70 hover:border-white hover:text-white transition-all duration-200">
              DROPS
            </Link>
          </div>
        </div>
        <div className="absolute bottom-10 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2">
          <div className="h-10 w-px bg-gradient-to-b from-transparent to-white/30" />
          <p className="text-[9px] tracking-[0.4em] text-white/20">SCROLL</p>
        </div>
      </section>

      {/* Feature strip */}
      <section className="border-b border-gray-100 bg-white">
        <div className="grid grid-cols-3 divide-x divide-gray-100">
          {[
            { icon: Zap, title: 'FAIR RAFFLE', desc: '투명한 추첨' },
            { icon: ShoppingBag, title: 'LIMITED DROP', desc: '선착순 한정' },
            { icon: Ticket, title: 'VERIFIED', desc: '정품 보증' },
          ].map(({ icon: Icon, title, desc }) => (
            <div key={title} className="flex flex-col items-center py-6 px-4 text-center">
              <Icon size={18} className="mb-2 text-gray-300" />
              <p className="text-[10px] font-black tracking-widest text-black">{title}</p>
              <p className="mt-0.5 text-[10px] text-gray-400">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      <div className="px-4 lg:px-8">

        {/* RAFFLE section */}
        <section className="py-16">
          <div className="mb-10 flex items-end justify-between">
            <div>
              <p className="text-[10px] font-bold tracking-[0.4em] text-red-500 mb-1">NOW LIVE</p>
              <h2 className="text-3xl font-black tracking-tight">RAFFLE</h2>
            </div>
            <Link to="/raffles" className="flex items-center gap-1.5 text-[10px] font-black tracking-widest text-gray-300 hover:text-black transition-colors">
              VIEW ALL <ArrowRight size={12} />
            </Link>
          </div>

          <div className="grid gap-1 sm:grid-cols-2 lg:grid-cols-4">
            {(raffles.length > 0 ? raffles : []).map((raffle: any, i: number) => (
              <RaffleCard
                key={raffle.raffleId}
                raffle={raffle}
                product={productMap[raffle.productId]}
                index={i}
              />
            ))}
          </div>
        </section>

        {/* DROP banner */}
        <section className="mb-16">
          <div className="relative overflow-hidden bg-black" style={{ minHeight: 420 }}>
            <img src={HERO_IMAGES.drops} alt="" className="absolute inset-0 h-full w-full object-cover opacity-25" />
            <div className="absolute inset-0" style={{ background: 'radial-gradient(ellipse at 30% 50%, rgba(220,38,38,0.18) 0%, transparent 70%)' }} />
            <div className="relative grid md:grid-cols-2 items-center gap-10 p-10 md:p-16">
              <div>
                <p className="text-[10px] font-bold tracking-[0.5em] text-red-400/70 mb-4">EXCLUSIVE RELEASE</p>
                <h2 className="text-5xl font-black tracking-tight text-white leading-tight">DROP<br />SEASON</h2>
                <p className="mt-4 text-sm text-white/30 max-w-xs">선착순으로만 살 수 있는 한정 상품.</p>
                <Link to="/drops" className="mt-8 inline-flex items-center gap-2 bg-white px-7 py-3.5 text-[11px] font-black tracking-[0.25em] text-black hover:bg-red-500 hover:text-white transition-colors">
                  SHOP DROPS <ArrowRight size={13} />
                </Link>
              </div>
              <div className="hidden md:grid grid-cols-2 gap-1.5">
                {Array.from({ length: 4 }).map((_, i) => {
                  const drop = drops[i]
                  return (
                    <div key={i} className="group relative aspect-square overflow-hidden">
                      <img
                        src={getPlaceholderImage(i + 4)}
                        alt=""
                        className="h-full w-full object-cover opacity-70 group-hover:opacity-100 group-hover:scale-105 transition-all duration-500"
                      />
                      <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
                      {drop && (
                        <span className={`absolute top-2 left-2 px-2 py-0.5 text-[9px] font-black tracking-widest ${drop.status === 'OPEN' ? 'bg-red-500 text-white' : 'bg-black/50 text-white/50'}`}>
                          {drop.status === 'OPEN' ? 'LIVE' : 'ENDED'}
                        </span>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        </section>

        {/* PRODUCTS preview */}
        <section className="pb-16">
          <div className="mb-10 flex items-end justify-between">
            <div>
              <p className="text-[10px] font-bold tracking-[0.4em] text-gray-400 mb-1">CATALOG</p>
              <h2 className="text-3xl font-black tracking-tight">PRODUCTS</h2>
            </div>
            <Link to="/products" className="flex items-center gap-1.5 text-[10px] font-black tracking-widest text-gray-300 hover:text-black transition-colors">
              VIEW ALL <ArrowRight size={12} />
            </Link>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-1">
            {[2, 5, 7, 9].map((imgIdx, i) => (
              <Link key={i} to="/products" className="group relative aspect-square overflow-hidden bg-gray-100">
                <img
                  src={getPlaceholderImage(imgIdx)}
                  alt=""
                  className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-700 opacity-80 group-hover:opacity-100"
                />
                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
              </Link>
            ))}
          </div>
        </section>

      </div>
    </div>
  )
}
