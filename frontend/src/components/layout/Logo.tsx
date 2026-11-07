import { cn } from '@/lib/cn'

/** The globe mark on an accent tile. `onDark` is for the fixed dark panel on the sign-in page. */
export function Logo({ className, onDark = false }: { className?: string; onDark?: boolean }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <svg viewBox="0 0 32 32" aria-hidden="true" className="size-8 shrink-0">
        <rect width="32" height="32" rx="9" className="fill-accent" />
        <g fill="none" className="stroke-on-accent">
          <circle cx="16" cy="16" r="8.5" strokeWidth="2.2" />
          <ellipse cx="16" cy="16" rx="3.6" ry="8.5" strokeWidth="1.6" />
          <path d="M7.5 16h17" strokeWidth="1.6" />
        </g>
      </svg>
      <div className="leading-tight">
        <p className={cn('text-base font-semibold tracking-tight', onDark ? 'text-white' : 'text-neutral-900')}>UniSphere</p>
        <p className={cn('text-[11px]', onDark ? 'text-white/60' : 'text-neutral-500')}>Academic Portal</p>
      </div>
    </div>
  )
}
