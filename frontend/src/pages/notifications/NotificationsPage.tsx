import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { notificationsApi } from '../../api/notifications'
import { formatDate } from '../../lib/utils'
import { Spinner } from '../../components/ui/Spinner'

const TYPE_COLOR: Record<string, string> = {
  ORDER: 'bg-blue-50 text-blue-700',
  PAYMENT: 'bg-green-50 text-green-700',
  RAFFLE: 'bg-purple-50 text-purple-700',
  DROP: 'bg-red-50 text-red-700',
  COUPON: 'bg-yellow-50 text-yellow-700',
  SYSTEM: 'bg-gray-100 text-gray-600',
}

export function NotificationsPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['notifications-page', page],
    queryFn: () => notificationsApi.getMyNotifications(page, 20),
  })

  const notifications = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.totalPages ?? 0

  const markAsRead = useMutation({
    mutationFn: (id: string) => notificationsApi.markAsRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications-page'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">INBOX</p>
          <h1 className="text-2xl font-black tracking-tight text-gray-900">알림</h1>
        </div>
        <Bell size={20} className="text-gray-300" />
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : notifications.length === 0 ? (
        <div className="py-20 text-center">
          <Bell size={32} className="mx-auto mb-3 text-gray-200" />
          <p className="text-sm text-gray-400">알림이 없습니다</p>
        </div>
      ) : (
        <div className="divide-y divide-gray-100 border border-gray-100">
          {notifications.map((n: any) => (
            <div
              key={n.notificationId}
              onClick={() => !n.isRead && markAsRead.mutate(n.notificationId)}
              className={`flex gap-4 p-5 transition-colors ${
                n.isRead
                  ? 'bg-white cursor-default'
                  : 'bg-white cursor-pointer hover:bg-gray-50'
              }`}
            >
              <div className="flex-shrink-0 pt-0.5">
                <span className={`inline-block px-2 py-0.5 text-[9px] font-black tracking-wider ${TYPE_COLOR[n.notificationType] ?? 'bg-gray-100 text-gray-600'}`}>
                  {n.notificationType ?? 'SYSTEM'}
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className={`text-sm font-bold leading-tight ${n.isRead ? 'text-gray-400' : 'text-gray-900'}`}>
                      {n.title}
                    </p>
                    {n.content && (
                      <p className={`mt-1 text-xs leading-relaxed ${n.isRead ? 'text-gray-300' : 'text-gray-500'}`}>
                        {n.content}
                      </p>
                    )}
                  </div>
                  <div className="flex-shrink-0 flex flex-col items-end gap-1.5">
                    {!n.isRead && <div className="w-2 h-2 rounded-full bg-black" />}
                    <p className="text-[10px] text-gray-400 whitespace-nowrap">
                      {formatDate(n.sentAt ?? n.createdAt)}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-2">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="px-3 py-1.5 text-xs font-bold text-gray-400 hover:text-black disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            ← 이전
          </button>
          <span className="text-xs text-gray-400">{page + 1} / {totalPages}</span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage(p => p + 1)}
            className="px-3 py-1.5 text-xs font-bold text-gray-400 hover:text-black disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            다음 →
          </button>
        </div>
      )}
    </div>
  )
}
