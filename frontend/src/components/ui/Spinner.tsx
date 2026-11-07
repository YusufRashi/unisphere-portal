import { cn } from '@/lib/cn'

export function Spinner({ className, label }: { className?: string; label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2">
      <span
        aria-hidden="true"
        className={cn('size-4 animate-spin rounded-full border-2 border-current border-r-transparent', className)}
      />
      <span className={label ? 'text-neutral-600' : 'sr-only'}>{label ?? 'Loading'}</span>
    </span>
  )
}

export function FullPageSpinner({ label }: { label: string }) {
  return (
    <div className="flex min-h-dvh items-center justify-center text-brand-700">
      <Spinner className="size-6" label={label} />
    </div>
  )
}
