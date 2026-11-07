import { useQuery } from '@tanstack/react-query'
import { CalendarDays } from 'lucide-react'
import { Link } from 'react-router'
import { errorMessage, toApiError } from '@/api/errors'
import { getMyTimetable } from '@/api/registration'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { DAY_LONG, DAY_SHORT, shortTime } from '@/lib/datetime'
import { REGISTRATION_STATUS_LABELS, REGISTRATION_STATUS_TONES } from '../registration/labels'
import { layoutTimetable } from './timetableLayout'

// A fixed palette keeps each course the same colour across the week.
const COLOURS = [
  'bg-brand-50 border-brand-300 text-brand-900',
  'bg-emerald-50 border-emerald-300 text-emerald-900',
  'bg-amber-50 border-amber-300 text-amber-900',
  'bg-violet-50 border-violet-300 text-violet-900',
  'bg-rose-50 border-rose-300 text-rose-900',
  'bg-cyan-50 border-cyan-300 text-cyan-900',
]
const HOUR_HEIGHT_REM = 3.5

export function TimetablePage() {
  const query = useQuery({ queryKey: ['timetable', 'me'], queryFn: getMyTimetable })

  if (query.error) {
    const notOpen = toApiError(query.error).status === 404
    return (
      <>
        <PageHeader title="My timetable" />
        {notOpen ? (
          <Card>
            <EmptyState icon={CalendarDays} title="No current semester" description={errorMessage(query.error)} />
          </Card>
        ) : (
          <Alert tone="error">{errorMessage(query.error)}</Alert>
        )}
      </>
    )
  }
  if (!query.data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading timetable…" />
      </div>
    )
  }

  const { semester, status, entries } = query.data
  const layout = layoutTimetable(entries)
  const colourOf = new Map<number, string>()
  entries.forEach((e) => {
    if (!colourOf.has(e.offeringId)) colourOf.set(e.offeringId, COLOURS[colourOf.size % COLOURS.length]!)
  })

  return (
    <>
      <PageHeader
        title="My timetable"
        description={semester.name}
        actions={status ? <Badge tone={REGISTRATION_STATUS_TONES[status]}>{REGISTRATION_STATUS_LABELS[status]}</Badge> : undefined}
      />
      {entries.length === 0 ? (
        <Card>
          <EmptyState
            icon={CalendarDays}
            title="Nothing scheduled yet"
            description="Your timetable appears once you have submitted your course registration."
            action={
              <Link to="/student/registration" className="text-sm font-medium text-brand-700 hover:text-brand-900">
                Go to course registration →
              </Link>
            }
          />
        </Card>
      ) : (
        <>
          {/* Week grid on larger screens */}
          <Card className="hidden overflow-hidden md:block">
            <div className="grid" style={{ gridTemplateColumns: `4rem repeat(${layout.days.length}, minmax(0, 1fr))` }}>
              <div className="border-b border-neutral-200 bg-neutral-50" />
              {layout.days.map((d) => (
                <div key={d} className="border-b border-l border-neutral-200 bg-neutral-50 px-3 py-2 text-xs font-semibold tracking-wide text-neutral-600 uppercase">
                  {DAY_SHORT[d]}
                </div>
              ))}
              <div className="relative" style={{ height: `${layout.hours.length * HOUR_HEIGHT_REM}rem` }}>
                {layout.hours.map((h, i) => (
                  // The first label sits below the header line instead of being centred on it (and clipped).
                  <div key={h} className={`absolute right-2 text-xs text-neutral-400 tabular-nums ${i === 0 ? 'translate-y-0.5' : '-translate-y-1/2'}`} style={{ top: `${i * HOUR_HEIGHT_REM}rem` }}>
                    {String(h).padStart(2, '0')}:00
                  </div>
                ))}
              </div>
              {layout.days.map((d) => (
                <div key={d} className="relative border-l border-neutral-200" style={{ height: `${layout.hours.length * HOUR_HEIGHT_REM}rem` }}>
                  {layout.hours.map((h, i) => (
                    <div key={h} aria-hidden="true" className="absolute inset-x-0 border-t border-neutral-100" style={{ top: `${i * HOUR_HEIGHT_REM}rem` }} />
                  ))}
                  {layout.byDay[d].map((e, i) => (
                    <div
                      key={i}
                      className={`absolute inset-x-1 overflow-hidden rounded-lg border-l-4 px-2 py-1 text-xs ${colourOf.get(e.offeringId)}`}
                      style={{ top: `${e.topPercent}%`, height: `${e.heightPercent}%` }}
                    >
                      <p className="font-semibold">
                        {e.courseCode} · {e.section}
                      </p>
                      <p className="truncate">{e.venue}</p>
                      <p className="truncate opacity-80">
                        {shortTime(e.startTime)}–{shortTime(e.endTime)}
                      </p>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </Card>

          {/* Agenda list on phones (and for screen readers, a clear reading order) */}
          <div className="space-y-4 md:hidden">
            {layout.days.filter((d) => layout.byDay[d].length > 0).map((d) => (
              <Card key={d}>
                <h2 className="border-b border-neutral-200 px-4 py-2 text-sm font-semibold text-neutral-900">{DAY_LONG[d]}</h2>
                <ul className="divide-y divide-neutral-100">
                  {layout.byDay[d].map((e, i) => (
                    <li key={i} className="flex gap-3 px-4 py-3">
                      <p className="w-24 shrink-0 text-sm text-neutral-600 tabular-nums">
                        {shortTime(e.startTime)}–{shortTime(e.endTime)}
                      </p>
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-neutral-900">
                          {e.courseCode} · {e.courseTitle}
                        </p>
                        <p className="text-xs text-neutral-500">
                          {e.venue}
                          {e.lecturer ? ` · ${e.lecturer}` : ''}
                        </p>
                      </div>
                    </li>
                  ))}
                </ul>
              </Card>
            ))}
          </div>
        </>
      )}
    </>
  )
}
