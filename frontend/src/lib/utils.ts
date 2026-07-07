import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatPrice(amount: number) {
  return new Intl.NumberFormat('ko-KR').format(amount) + '원'
}

export function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}


// 타임스탬프 기반 래플 표시 상태 (백엔드 status 필드 무관)
export function getRaffleDisplayStatus(raffle: any): 'UPCOMING' | 'LIVE' | 'ENDED' | 'DRAWN' {
  if (raffle?.status === 'DRAWN') return 'DRAWN'
  if (raffle?.status === 'CANCELLED') return 'ENDED'
  const now = Date.now()
  const startTime = raffle?.startedAt ? new Date(raffle.startedAt).getTime() : 0
  const endTime = raffle?.endedAt ? new Date(raffle.endedAt).getTime() : Infinity
  if (startTime > now) return 'UPCOMING'
  if (now <= endTime) return 'LIVE'
  return 'ENDED'
}

// 타임스탬프 기반 드롭 표시 상태
export function getDropDisplayStatus(drop: any): 'UPCOMING' | 'LIVE' | 'ENDED' {
  const now = Date.now()
  const startTime = drop?.startAt ? new Date(drop.startAt).getTime() : 0
  const endTime = drop?.endAt ? new Date(drop.endAt).getTime() : Infinity
  if (startTime > now) return 'UPCOMING'
  if (now <= endTime) return 'LIVE'
  return 'ENDED'
}
