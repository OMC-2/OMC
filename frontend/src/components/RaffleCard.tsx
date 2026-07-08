import { Link } from 'react-router-dom'
import { useCountdown } from '../hooks/useCountdown'
import { Timer } from 'lucide-react'
import { getPlaceholderImage } from '../lib/images'
import { getRaffleDisplayStatus } from '../lib/utils'

interface RaffleCardProps {
  raffle: any
  product?: any
  index: number
  aspectClass?: string
}

export function RaffleCard({ raffle, product, index, aspectClass = 'aspect-[3/4]' }: RaffleCardProps) {
  const displayStatus = getRaffleDisplayStatus(raffle)
  const isLive = displayStatus === 'LIVE'
  const isUpcoming = displayStatus === 'UPCOMING'

  // LIVE면 종료까지, UPCOMING이면 시작까지 카운트다운
  const countdownTarget = isLive ? raffle?.endedAt : isUpcoming ? raffle?.startedAt : undefined
  const countdown = useCountdown(countdownTarget)

  const imageUrl = product?.imageUrl || raffle?.imageUrl || getPlaceholderImage(index)

  const badgeStyle = isLive
    ? 'bg-red-500 text-white'
    : isUpcoming
    ? 'bg-blue-600 text-white'
    : 'bg-black/50 text-white/40'

  const badgeText = isLive
    ? 'LIVE'
    : isUpcoming
    ? '진행 예정'
    : displayStatus === 'DRAWN'
    ? 'DRAWN'
    : 'ENDED'

  return (
    <Link to={`/raffles/${raffle.raffleId}`} className="group">
      <div className={`relative ${aspectClass} overflow-hidden bg-gray-900`}>
        <img
          src={imageUrl}
          alt={raffle.name}
          onError={(e) => { const t = e.currentTarget; t.onerror = null; t.src = getPlaceholderImage(index) }}
          className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-700"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/5 to-transparent" />

        {/* Top badges */}
        <div className="absolute top-3 left-3 flex flex-col gap-1.5">
          <span className={`px-2 py-1 text-[9px] font-black tracking-[0.2em] ${badgeStyle}`}>
            {badgeText}
          </span>
          {(isLive || isUpcoming) && countdown && (
            <span className="flex items-center gap-1 bg-black/80 px-2 py-1 text-[9px] font-black text-white tabular-nums">
              <Timer size={9} className={isUpcoming ? 'text-blue-400 shrink-0' : 'text-red-400 shrink-0'} />
              {isUpcoming ? '시작 ' : ''}{countdown}
            </span>
          )}
        </div>

        {/* Bottom info */}
        <div className="absolute bottom-0 left-0 right-0 p-4">
          <p className="text-xs font-black text-white line-clamp-2 group-hover:text-red-300 transition-colors leading-snug">
            {raffle.name}
          </p>
          <div className="mt-1.5 flex items-center justify-between text-[10px] text-white/40">
            <span>당첨 {raffle.winnerCount}명</span>
          </div>
        </div>
      </div>
    </Link>
  )
}
