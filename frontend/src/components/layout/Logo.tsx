import { cn } from '@/lib/cn'

export function Logo({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <svg viewBox="0 0 32 32" aria-hidden="true" className="size-8 shrink-0">
        <rect width="32" height="32" rx="7" fill={inverted ? '#ffffff' : '#1e3a8a'} />
        <g fill="none" stroke={inverted ? '#1e3a8a' : '#ffffff'}>
          <circle cx="16" cy="16" r="8.5" strokeWidth="2.2" />
          <ellipse cx="16" cy="16" rx="3.6" ry="8.5" strokeWidth="1.6" />
          <path d="M7.5 16h17" strokeWidth="1.6" />
        </g>
      </svg>
      <div className="leading-tight">
        <p className={cn('text-base font-semibold tracking-tight', inverted ? 'text-white' : 'text-slate-900')}>UniSphere</p>
        <p className={cn('text-[11px]', inverted ? 'text-brand-200' : 'text-slate-500')}>Academic Portal</p>
      </div>
    </div>
  )
}
