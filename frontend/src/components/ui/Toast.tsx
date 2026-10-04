import { CheckCircle2, AlertCircle, X } from 'lucide-react'
import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { ToastContext, type ToastApi } from './toastContext'

type ToastTone = 'success' | 'error'
interface ToastItem {
  id: number
  tone: ToastTone
  message: string
}

let nextId = 1

/** Lightweight, screen-reader-announced notifications for the result of user actions. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), [])

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = nextId++
      setToasts((all) => [...all, { id, tone, message }])
      setTimeout(() => dismiss(id), 5000)
    },
    [dismiss],
  )

  const api = useMemo<ToastApi>(
    () => ({ success: (m) => push('success', m), error: (m) => push('error', m) }),
    [push],
  )

  return (
    <ToastContext value={api}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed right-4 bottom-4 z-50 flex w-full max-w-sm flex-col gap-2">
        {toasts.map((t) => {
          const Icon = t.tone === 'success' ? CheckCircle2 : AlertCircle
          return (
            <div
              key={t.id}
              role="status"
              className={cn(
                'pointer-events-auto flex items-start gap-3 rounded-md border bg-white p-3 shadow-lg',
                t.tone === 'success' ? 'border-emerald-200' : 'border-red-200',
              )}
            >
              <Icon aria-hidden="true" className={cn('mt-0.5 size-4 shrink-0', t.tone === 'success' ? 'text-emerald-600' : 'text-red-600')} />
              <p className="flex-1 text-sm text-slate-800">{t.message}</p>
              <button type="button" onClick={() => dismiss(t.id)} className="rounded text-slate-400 hover:text-slate-600" aria-label="Dismiss">
                <X aria-hidden="true" className="size-4" />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext>
  )
}
