import { cn } from '@/lib/cn'

/** A graduation cap resting on a globe, on an accent tile. Keep public/favicon.svg in step with this mark. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn('size-8 shrink-0', className)}>
      <rect width="32" height="32" rx="9" className="fill-accent" />
      <g fill="none" className="stroke-on-accent">
        <circle cx="16" cy="19.5" r="7" strokeWidth="2" />
        <ellipse cx="16" cy="19.5" rx="3" ry="7" strokeWidth="1.5" />
        <path d="M9 19.5h14" strokeWidth="1.5" />
      </g>
      <path d="M16 5.5 27 10 16 14.5 5 10Z" strokeWidth="1.3" strokeLinejoin="round" className="fill-on-accent stroke-accent" />
      <path d="M24 11.2v4.3" strokeWidth="1.3" strokeLinecap="round" className="stroke-on-accent" />
      <circle cx="24" cy="16.2" r="1.2" className="fill-on-accent" />
    </svg>
  )
}

/** The mark with the name. `onDark` is for text over the dark photo panels. */
export function Logo({ className, onDark = false }: { className?: string; onDark?: boolean }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <LogoMark />
      <div className="leading-tight">
        <p className={cn('text-base font-semibold tracking-tight', onDark ? 'text-white' : 'text-neutral-900')}>
          Uni<span className={onDark ? 'text-brand-300 dark:text-brand-700' : 'text-accent'}>Sphere</span>
        </p>
        <p className={cn('text-[11px]', onDark ? 'text-white/60' : 'text-neutral-500')}>Academic Portal</p>
      </div>
    </div>
  )
}
