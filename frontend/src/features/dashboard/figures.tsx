import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Spinner } from '@/components/ui/Spinner'

/** A row of headline numbers. Tiles inside are a description list, so label and value stay associated. */
export function StatRow({ children, label }: { children: ReactNode; label: string }) {
  return (
    <dl aria-label={label} className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {children}
    </dl>
  )
}

export function StatTile({ label, value, hint, link }: {
  label: string
  value: ReactNode
  hint?: ReactNode
  link?: { to: string; label: string }
}) {
  return (
    <div className="flex flex-col rounded-lg border border-slate-200 bg-white px-5 py-4 shadow-xs">
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tracking-tight break-words text-slate-900">{value}</dd>
      {hint && <dd className="mt-1 text-xs text-slate-500">{hint}</dd>}
      {link && (
        <dd className="mt-auto pt-2 text-xs">
          <Link to={link.to} className="font-medium text-brand-700 hover:text-brand-900">
            {link.label} →
          </Link>
        </dd>
      )}
    </div>
  )
}

/** A ratio against a limit: the fill and the track are steps of one colour, and the text gives the exact figure. */
export function Meter({ label, value, max, text }: { label: string; value: number; max: number; text: string }) {
  const percent = max > 0 ? Math.min(value / max, 1) * 100 : 0
  return (
    <div>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={Math.min(value, max)}
        aria-valuetext={text}
        className="h-2 rounded-full bg-brand-100"
      >
        <div className="h-full rounded-full bg-brand-600" style={{ width: `${percent}%` }} />
      </div>
      <p className="mt-1.5 text-xs text-slate-600">{text}</p>
    </div>
  )
}

/** Horizontal bars with the value at the tip: every number is visible, so no tooltip or table is needed. */
export function BarList({ label, rows }: { label: string; rows: { key: string; label: string; value: number; text: string }[] }) {
  const max = Math.max(...rows.map((r) => r.value), 1)
  return (
    <ul aria-label={label} className="space-y-3">
      {rows.map((r) => (
        <li key={r.key} className="text-sm">
          <div className="flex items-baseline justify-between gap-3">
            <span className="text-slate-700">{r.label}</span>
            <span className="text-right font-medium text-slate-900 tabular-nums">{r.text}</span>
          </div>
          <div aria-hidden="true" className="mt-1 h-2 rounded-r bg-slate-100">
            <div className="h-full rounded-r bg-brand-600" style={{ width: `${(r.value / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function DashboardLoading({ label }: { label: string }) {
  return (
    <div className="flex justify-center p-12 text-brand-700">
      <Spinner label={label} />
    </div>
  )
}
