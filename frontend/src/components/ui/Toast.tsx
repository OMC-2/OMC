import { create } from 'zustand'
import { CheckCircle, XCircle, Info, X, AlertTriangle } from 'lucide-react'

type ToastType = 'success' | 'error' | 'info' | 'warning'

interface ToastItem {
  id: string
  type: ToastType
  message: string
}

interface ToastStore {
  toasts: ToastItem[]
  add: (type: ToastType, message: string, duration?: number) => void
  remove: (id: string) => void
}

export const useToastStore = create<ToastStore>((set) => ({
  toasts: [],
  add: (type, message, duration = 4000) => {
    const id = Math.random().toString(36).slice(2, 9)
    set(s => ({ toasts: [...s.toasts.slice(-4), { id, type, message }] }))
    setTimeout(() => set(s => ({ toasts: s.toasts.filter(t => t.id !== id) })), duration)
  },
  remove: (id) => set(s => ({ toasts: s.toasts.filter(t => t.id !== id) })),
}))

export const toast = {
  success: (msg: string) => useToastStore.getState().add('success', msg),
  error: (msg: string) => useToastStore.getState().add('error', msg, 6000),
  info: (msg: string) => useToastStore.getState().add('info', msg),
  warning: (msg: string) => useToastStore.getState().add('warning', msg, 5000),
}

const ICON = {
  success: CheckCircle,
  error: XCircle,
  info: Info,
  warning: AlertTriangle,
}

const STYLE = {
  success: 'border-green-200 bg-green-50 text-green-800',
  error:   'border-red-200 bg-red-50 text-red-800',
  info:    'border-blue-200 bg-blue-50 text-blue-800',
  warning: 'border-yellow-200 bg-yellow-50 text-yellow-800',
}

const ICON_STYLE = {
  success: 'text-green-500',
  error:   'text-red-400',
  info:    'text-blue-400',
  warning: 'text-yellow-500',
}

export function ToastContainer() {
  const toasts = useToastStore(s => s.toasts)
  const remove = useToastStore(s => s.remove)

  if (toasts.length === 0) return null

  return (
    <div className="fixed bottom-6 right-6 z-[200] flex flex-col gap-2 pointer-events-none">
      {toasts.map(t => {
        const Icon = ICON[t.type]
        return (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-start gap-3 w-[340px] max-w-[90vw] border px-4 py-3 shadow-lg ${STYLE[t.type]}`}
          >
            <Icon size={16} className={`shrink-0 mt-0.5 ${ICON_STYLE[t.type]}`} />
            <p className="flex-1 text-xs font-medium leading-relaxed">{t.message}</p>
            <button
              onClick={() => remove(t.id)}
              className="shrink-0 opacity-50 hover:opacity-100 transition-opacity"
            >
              <X size={14} />
            </button>
          </div>
        )
      })}
    </div>
  )
}
