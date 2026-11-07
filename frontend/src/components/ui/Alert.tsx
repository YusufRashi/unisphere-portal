import { AlertCircle, CheckCircle2, Info } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type Tone = 'error' | 'success' | 'info'

const tones: Record<Tone, { box: string; icon: typeof Info }> = {
  error: { box: 'border-red-200 bg-red-50 text-red-800', icon: AlertCircle },
  success: { box: 'border-emerald-200 bg-emerald-50 text-emerald-800', icon: CheckCircle2 },
  info: { box: 'border-brand-200 bg-brand-50 text-brand-900', icon: Info },
}

export function Alert({ tone = 'info', title, children, className }: {
  tone?: Tone
  title?: string
  children?: ReactNode
  className?: string
}) {
  const { box, icon: Icon } = tones[tone]
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('flex gap-3 rounded-xl border p-3', box, className)}>
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="space-y-1 text-sm">
        {title && <p className="font-medium">{title}</p>}
        {children && <div>{children}</div>}
      </div>
    </div>
  )
}
