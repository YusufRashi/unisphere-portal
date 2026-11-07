import { cn } from '@/lib/cn'

/**
 * A small row of pill-shaped choices, the selected one solid. A group of toggle buttons: each says whether it is
 * pressed, and the group has a name, so screen readers announce it as one control.
 */
export function PillTabs<T extends string>({ label, options, value, onChange, className }: {
  label: string
  options: readonly { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
  className?: string
}) {
  return (
    <div role="group" aria-label={label} className={cn('inline-flex flex-wrap gap-1 rounded-full border border-neutral-200 bg-surface p-1', className)}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          aria-pressed={value === o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            'rounded-full px-3.5 py-1.5 text-xs font-medium transition-colors',
            value === o.value ? 'bg-neutral-900 text-surface' : 'text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
