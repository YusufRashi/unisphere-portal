import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={cn('rounded-card border border-neutral-200 bg-surface shadow-xs', className)}>{children}</section>
}

/** Title, optional supporting text and actions (small buttons or links) at the right. */
export function CardHeader({ title, description, actions }: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 px-5 pt-5 pb-4">
      <div className="min-w-0">
        <h2 className="text-[15px] font-semibold text-neutral-900">{title}</h2>
        {description && <p className="mt-0.5 text-sm text-neutral-500">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  )
}

export function CardBody({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('px-5 pb-5', className)}>{children}</div>
}

/** A quieter panel inside a card, for grouping items (a class, an exam, an announcement). */
export function Inset({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('rounded-xl border border-neutral-200 bg-neutral-50', className)}>{children}</div>
}

/** One-line footer at the bottom of a card: a summary at the left, a hint or link at the right. */
export function CardFooter({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-2 border-t border-neutral-100 px-5 py-3 text-xs text-neutral-500', className)}>
      {children}
    </div>
  )
}
