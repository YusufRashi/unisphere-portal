import { cn } from '@/lib/cn'

/** Registered credits against the semester's minimum and maximum load. */
export function CreditMeter({ total, min, max }: { total: number; min: number; max: number }) {
  const percent = max > 0 ? Math.min(total / max, 1) * 100 : 0
  const minPercent = max > 0 ? Math.min(min / max, 1) * 100 : 0
  const state = total > max ? 'over' : total < min ? 'under' : 'ok'
  const message =
    state === 'under' ? `${min - total} more credit${min - total === 1 ? '' : 's'} needed (minimum ${min})`
      : state === 'over' ? `${total - max} over the maximum of ${max}`
        : `Within the allowed load of ${min}–${max} credits`

  return (
    <div>
      <div className="flex items-baseline justify-between">
        <p className="text-sm font-medium text-slate-700">Credit load</p>
        <p className="text-sm text-slate-600">
          <span className="text-lg font-semibold text-slate-900 tabular-nums">{total}</span> / {max}
        </p>
      </div>
      <div
        role="meter"
        aria-label="Credit load"
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={total}
        aria-valuetext={`${total} of ${max} credits. ${message}`}
        className="relative mt-2 h-2 rounded-full bg-slate-100"
      >
        <div
          className={cn('h-full rounded-full transition-[width]', state === 'ok' ? 'bg-emerald-500' : state === 'under' ? 'bg-amber-500' : 'bg-red-500')}
          style={{ width: `${percent}%` }}
        />
        {min > 0 && (
          <div aria-hidden="true" className="absolute -top-1 h-4 w-0.5 rounded bg-slate-400" style={{ left: `${minPercent}%` }} />
        )}
      </div>
      <p className={cn('mt-1.5 text-xs', state === 'ok' ? 'text-emerald-700' : state === 'under' ? 'text-amber-700' : 'text-red-700')}>
        {message}
      </p>
    </div>
  )
}
