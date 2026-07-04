import { useState, useEffect } from 'react'

export function useCountdown(targetDate: string | undefined) {
  const [timeLeft, setTimeLeft] = useState('')

  useEffect(() => {
    if (!targetDate) return
    const tick = () => {
      const diff = new Date(targetDate).getTime() - Date.now()
      if (diff <= 0) { setTimeLeft('종료'); return }
      const d = Math.floor(diff / 86400000)
      const h = Math.floor((diff % 86400000) / 3600000)
      const m = Math.floor((diff % 3600000) / 60000)
      const s = Math.floor((diff % 60000) / 1000)
      if (d > 0) setTimeLeft(`${d}일 ${h}시간 ${m}분`)
      else if (h > 0) setTimeLeft(`${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`)
      else setTimeLeft(`${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`)
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [targetDate])

  return timeLeft
}
