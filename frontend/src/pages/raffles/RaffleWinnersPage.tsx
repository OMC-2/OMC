import { useQuery } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { rafflesApi } from '../../api/raffles'
import { Spinner } from '../../components/ui/Spinner'
import { ArrowLeft, Trophy, Users, Clock } from 'lucide-react'
import { formatDate } from '../../lib/utils'

const RESULT_COLOR: Record<string, string> = {
  WINNER: 'text-yellow-600 bg-yellow-50',
  LOSER: 'text-gray-400 bg-gray-50',
}

export function RaffleWinnersPage() {
  const { raffleId } = useParams<{ raffleId: string }>()

  const { data: raffleData, isLoading: raffleLoading } = useQuery({
    queryKey: ['raffle', raffleId],
    queryFn: () => rafflesApi.getById(raffleId!),
    enabled: !!raffleId,
  })

  const { data: winnersData, isLoading: winnersLoading } = useQuery({
    queryKey: ['raffle-winners', raffleId],
    queryFn: () => rafflesApi.getPublicWinners(raffleId!),
    enabled: !!raffleId,
  })

  const { data: countData } = useQuery({
    queryKey: ['raffle-count', raffleId],
    queryFn: () => rafflesApi.getParticipantsCount(raffleId!),
    enabled: !!raffleId,
  })

  const raffle = raffleData?.data?.data
  const winners: any[] = winnersData?.data?.data ?? []
  const participantsCount: number = countData?.data?.data ?? 0
  const isLoading = raffleLoading || winnersLoading

  if (isLoading) return <Spinner className="py-20" />

  const winnerList = winners.filter((w: any) => w.result === 'WINNER')
  const isDrawn = raffle?.status === 'DRAWN' || raffle?.status === 'COMPLETED' || winnerList.length > 0

  return (
    <div className="mx-auto max-w-xl">
      <Link to={`/raffles/${raffleId}`} className="mb-8 inline-flex items-center gap-2 text-xs font-bold tracking-wider text-gray-400 hover:text-black transition-colors">
        <ArrowLeft size={14} />BACK
      </Link>

      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <Trophy size={24} className="text-yellow-500" />
          <h1 className="text-3xl font-black tracking-tight">WINNERS</h1>
        </div>
        {raffle && <p className="text-sm text-gray-500">{raffle.name}</p>}
      </div>

      <div className="grid grid-cols-2 gap-3 mb-6">
        <div className="border border-gray-100 px-4 py-3 text-center">
          <div className="flex items-center justify-center gap-1.5 mb-1">
            <Users size={13} className="text-gray-300" />
            <p className="text-[10px] font-bold tracking-widest text-gray-400">PARTICIPANTS</p>
          </div>
          <p className="text-2xl font-black text-gray-900">{participantsCount}</p>
        </div>
        <div className="border border-gray-100 px-4 py-3 text-center">
          <div className="flex items-center justify-center gap-1.5 mb-1">
            <Trophy size={13} className="text-yellow-400" />
            <p className="text-[10px] font-bold tracking-widest text-gray-400">WINNERS</p>
          </div>
          <p className="text-2xl font-black text-gray-900">{raffle?.winnerCount ?? '-'}</p>
        </div>
      </div>

      {!isDrawn ? (
        <div className="border border-gray-100 p-10 text-center space-y-3">
          <Clock size={36} className="mx-auto text-gray-200" />
          <p className="text-sm font-black tracking-wider text-gray-900">추첨 전</p>
          <p className="text-xs text-gray-400 leading-relaxed">
            래플이 종료된 후 추첨이 진행됩니다.<br />
            당첨 여부는 등록하신 이메일로 안내됩니다.
          </p>
          <Link to={`/raffles/${raffleId}`} className="inline-block mt-2 text-xs font-black tracking-widest text-gray-900 underline hover:text-red-500 transition-colors">
            내 응모 결과 확인 -&gt;
          </Link>
        </div>
      ) : winners.length === 0 ? (
        <div className="border border-gray-100 p-10 text-center space-y-3">
          <Trophy size={36} className="mx-auto text-yellow-300" />
          <p className="text-sm font-black tracking-wider text-gray-900">당첨자 발표 완료</p>
          <p className="text-xs text-gray-400">
            개인정보 보호를 위해 당첨자 정보는 개별 이메일로 안내됩니다.
          </p>
          <Link to={`/raffles/${raffleId}`} className="inline-block mt-2 text-xs font-black tracking-widest text-gray-900 underline hover:text-red-500 transition-colors">
            내 당첨 여부 확인 -&gt;
          </Link>
        </div>
      ) : (
        <div>
          <p className="text-[10px] font-bold tracking-widest text-gray-400 mb-3">RESULT ({winners.length}명)</p>
          <div className="divide-y divide-gray-100 border border-gray-100">
            {winners.map((w: any, i: number) => (
              <div key={i} className="flex items-center justify-between px-5 py-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center bg-gray-50 text-[10px] font-black text-gray-400">
                    {i + 1}
                  </div>
                  <p className="text-xs font-mono text-gray-500 truncate max-w-[180px]">
                    {w.userId ? `${String(w.userId).slice(0, 8)}...` : '-'}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  {w.decidedAt && (
                    <p className="text-[10px] text-gray-300">{formatDate(w.decidedAt)}</p>
                  )}
                  <span className={`px-2 py-0.5 text-[9px] font-black tracking-wider ${RESULT_COLOR[w.result] ?? 'text-gray-400 bg-gray-50'}`}>
                    {w.result}
                  </span>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-4 text-center">
            <Link to={`/raffles/${raffleId}`} className="text-xs font-black tracking-widest text-gray-900 underline hover:text-red-500 transition-colors">
              내 당첨 여부 확인 -&gt;
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
