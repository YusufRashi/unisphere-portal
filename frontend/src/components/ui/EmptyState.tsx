import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { EmptySheets } from '@/components/illustrations/Illustrations'

export function EmptyState({ icon: Icon, title, description, action }: {
  icon: LucideIcon
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center px-6 py-10 text-center">
      <div className="relative mb-4 h-24 w-36">
        <EmptySheets className="absolute inset-0 size-full" />
        <div className="absolute right-5 bottom-1 grid size-10 place-items-center rounded-full bg-accent text-on-accent shadow-md ring-4 ring-surface">
          <Icon aria-hidden="true" className="size-5" />
        </div>
      </div>
      <p className="text-sm font-medium text-neutral-900">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-neutral-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
