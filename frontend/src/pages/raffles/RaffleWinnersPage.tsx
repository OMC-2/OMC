import { useQuery } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { rafflesApi } from '../../api/raffles'
import { Spinner } from '../../components/ui/Spinner'
import { ArrowLeft, Trophy, Mail } from 'lucide-react'

export function RaffleWinnersPage() {
  const { raffleId } = useParams<{ raffleId: string }>()

  const { data, isLoading } = useQuery({
    queryKey: ['raffle', raffleId],
    queryFn: () => rafflesApi.getById(raffleId!),
    enabled: !!raffleId,
  })
  const raffle = data?.data?.data

  if (isLoading) return <Spinner className="py-20" />

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

      <div className="border border-gray-100 p-10 text-center space-y-4">
        <Mail size={40} className="mx-auto text-gray-200" />
        <p className="text-sm font-black tracking-wider text-gray-900">당첨자는 이메일로 개별 안내됩니다</p>
        <p className="text-xs text-gray-400 leading-relaxed">
          래플 추첨 완료 후 당첨 여부는 개인정보 보호를 위해<br />
          공개적으로 표시되지 않습니다.<br />
          당첨 시 등록하신 이메일로 안내 드립니다.
        </p>
        <Link to={`/raffles/${raffleId}`} className="inline-block mt-2 text-xs font-black tracking-widest text-gray-900 underline hover:text-red-500 transition-colors">
          내 응모 결과 확인 →
        </Link>
      </div>
    </div>
  )
}
