import type { LucideIcon } from 'lucide-react'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import type { ReactNode } from 'react'
import { errorMessage } from '@/api/errors'
import { Alert } from '@/components/ui/Alert'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'

const headerClass = 'px-5 py-3 text-left text-xs font-semibold tracking-wide whitespace-nowrap text-slate-600 uppercase'

export function Th({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <th scope="col" className={cn(headerClass, className)}>
      {children}
    </th>
  )
}

/** Column header that toggles server-side sorting via a "field,direction" string. */
export function SortTh({ field, label, sort, onSort, className }: {
  field: string
  label: string
  sort: string
  onSort: (sort: string) => void
  className?: string
}) {
  const [current, direction] = sort.split(',')
  const active = current === field
  const next = active && direction === 'asc' ? `${field},desc` : `${field},asc`
  const Icon = !active ? ArrowUpDown : direction === 'asc' ? ArrowUp : ArrowDown
  return (
    <th
      scope="col"
      aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={cn(headerClass, className)}
    >
      <button type="button" onClick={() => onSort(next)} className="inline-flex items-center gap-1 tracking-wide uppercase hover:text-slate-900">
        {label}
        <Icon aria-hidden="true" className={active ? 'size-3.5 text-slate-900' : 'size-3.5 text-slate-400'} />
      </button>
    </th>
  )
}

export function Td({ children, className }: { children?: ReactNode; className?: string }) {
  return <td className={cn('px-5 py-3 align-top', className)}>{children}</td>
}

/**
 * Wraps a data table with its loading, error and empty states. `relative` on the scroll container keeps
 * visually hidden (absolutely positioned) text inside it on narrow screens.
 */
export function DataTable({
  caption,
  isLoading,
  error,
  onRetry,
  isEmpty,
  emptyIcon,
  emptyTitle,
  emptyDescription,
  emptyAction,
  isFetching,
  head,
  children,
  footer,
}: {
  caption: string
  isLoading: boolean
  error: unknown
  onRetry?: () => void
  isEmpty: boolean
  emptyIcon: LucideIcon
  emptyTitle: string
  emptyDescription?: string
  emptyAction?: ReactNode
  isFetching?: boolean
  head: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  if (error) {
    return (
      <div className="p-5">
        <Alert tone="error" title={`Could not load ${caption.toLowerCase()}`}>
          {errorMessage(error)}{' '}
          {onRetry && (
            <button type="button" className="font-medium underline" onClick={onRetry}>
              Try again
            </button>
          )}
        </Alert>
      </div>
    )
  }
  if (isLoading) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label={`Loading ${caption.toLowerCase()}…`} />
      </div>
    )
  }
  if (isEmpty) {
    return <EmptyState icon={emptyIcon} title={emptyTitle} description={emptyDescription} action={emptyAction} />
  }
  return (
    <>
      <div className="relative overflow-x-auto" aria-busy={isFetching || undefined}>
        <table className="min-w-full divide-y divide-slate-200">
          <caption className="sr-only">{caption}</caption>
          <thead className="bg-slate-50">
            <tr>{head}</tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">{children}</tbody>
        </table>
      </div>
      {footer}
    </>
  )
}

/** Toolbar row above a table holding search and filter controls. */
export function TableToolbar({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('grid gap-3 border-b border-slate-200 p-4', className)}>{children}</div>
}
