import { cn } from '../../lib/utils'

interface BadgeProps {
  children: React.ReactNode
  variant?: 'default' | 'success' | 'warning' | 'error' | 'outline' | 'secondary' | 'destructive'
  className?: string
}

const variants: Record<string, string> = {
  default: 'bg-gray-900 text-white',
  secondary: 'bg-gray-100 text-gray-700',
  success: 'bg-green-100 text-green-800',
  warning: 'bg-yellow-100 text-yellow-800',
  error: 'bg-red-100 text-red-800',
  destructive: 'bg-red-100 text-red-700',
  outline: 'border border-gray-300 text-gray-700',
}

export function Badge({ children, variant = 'default', className }: BadgeProps) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap', variants[variant], className)}>
      {children}
    </span>
  )
}
