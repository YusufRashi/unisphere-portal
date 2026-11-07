import { useQuery } from '@tanstack/react-query'
import { BookOpen } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '@/api/errors'
import { getMyTeaching } from '@/api/results'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { SelectField } from '@/components/ui/fields'
import { Spinner } from '@/components/ui/Spinner'
import { useSemesters } from '@/features/admin/queries'
import { RESULTS_STATUS_LABELS, RESULTS_STATUS_TONES } from '@/features/results/labels'
import { DAY_SHORT, shortTime } from '@/lib/datetime'

/** The lecturer's teaching assignments for a semester, each linking to its marks sheet. */
export function MyCoursesPage() {
  const [params, setParams] = useSearchParams()
  const semesters = useSemesters()
  const semesterId = Number(params.get('semesterId')) || semesters.data?.find((s) => s.current)?.id
  const query = useQuery({
    queryKey: ['lecturer', 'teaching', semesterId],
    queryFn: () => getMyTeaching(semesterId),
    enabled: semesters.isSuccess,
  })

  return (
    <>
      <PageHeader
        title="My courses"
        description="Class lists, marks entry and statistics for the courses you teach."
        actions={
          semesters.data && semesters.data.length > 0 ? (
            <div className="w-64 max-w-full">
              <SelectField label="Semester" hideLabel value={semesterId ?? ''} onChange={(e) => setParams({ semesterId: e.target.value }, { replace: true })}>
                {semesters.data.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                    {s.current ? ' (current)' : ''}
                  </option>
                ))}
              </SelectField>
            </div>
          ) : undefined
        }
      />
      {query.error || semesters.error ? (
        <Alert tone="error">{errorMessage(query.error ?? semesters.error)}</Alert>
      ) : !query.data ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Loading your courses…" />
        </div>
      ) : query.data.length === 0 ? (
        <Card>
          <EmptyState icon={BookOpen} title="No courses this semester" description="Courses assigned to you by the registrar appear here." />
        </Card>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Courses I teach">
          {query.data.map(({ offering: o, classSize, marksComplete }) => (
            <li key={o.id}>
              <Card className="flex h-full flex-col">
                <div className="flex-1 space-y-3 px-5 py-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-neutral-900">
                        {o.course.code} · Section {o.section}
                      </p>
                      <p className="truncate text-sm text-neutral-600">{o.course.title}</p>
                    </div>
                    <Badge tone={RESULTS_STATUS_TONES[o.resultsStatus]}>{RESULTS_STATUS_LABELS[o.resultsStatus]}</Badge>
                  </div>
                  <p className="text-xs text-neutral-500">
                    {o.sessions.map((s) => `${DAY_SHORT[s.dayOfWeek]} ${shortTime(s.startTime)}–${shortTime(s.endTime)} ${s.venue}`).join(' · ') ||
                      'No sessions scheduled'}
                  </p>
                  <div>
                    <div className="flex justify-between text-xs text-neutral-600">
                      <span>
                        {classSize} {classSize === 1 ? 'student' : 'students'}
                      </span>
                      <span className="tabular-nums">
                        {marksComplete} of {classSize} marked
                      </span>
                    </div>
                    <div
                      role="progressbar"
                      aria-label={`Marks entered for ${o.course.code}`}
                      aria-valuemin={0}
                      aria-valuemax={classSize}
                      aria-valuenow={marksComplete}
                      className="mt-1 h-1.5 rounded-full bg-neutral-100"
                    >
                      <div className="h-full rounded-full bg-brand-600" style={{ width: `${classSize ? (marksComplete / classSize) * 100 : 0}%` }} />
                    </div>
                  </div>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-t border-neutral-200 px-5 py-3">
                  <Link to={`/lecturer/courses/${o.id}`} className="text-sm font-medium text-brand-700 hover:text-brand-900">
                    {o.resultsStatus === 'DRAFT' ? `Enter marks for ${o.course.code}` : `View results for ${o.course.code}`} →
                  </Link>
                  <Link to={`/announcements?offeringId=${o.id}`} className="text-sm text-neutral-600 hover:text-neutral-900">
                    Announcements<span className="sr-only"> for {o.course.code}</span>
                  </Link>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
