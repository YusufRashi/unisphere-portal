import graduatesSunset from '@/assets/photos/graduates-sunset.webp'
import { useQuery } from '@tanstack/react-query'
import { GraduationCap } from 'lucide-react'
import { errorMessage } from '@/api/errors'
import { getMyResults } from '@/api/results'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Card, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { formatGpa, formatNumber } from '@/features/results/marks'
import type { SemesterRecord } from '@/types/api'

function SemesterCard({ record }: { record: SemesterRecord }) {
  return (
    <Card>
      <CardHeader
        title={record.semester.name}
        description={`${record.creditsEarned} of ${record.creditsAttempted} credits earned`}
        actions={
          <p className="text-right">
            <span className="block text-xs text-neutral-500">Semester GPA</span>
            <span className="text-lg font-semibold text-neutral-900 tabular-nums">{formatGpa(record.gpa)}</span>
          </p>
        }
      />
      <div className="overflow-x-auto">
        <table className="min-w-[36rem] divide-y divide-neutral-200 text-sm sm:min-w-full">
          <caption className="sr-only">Results for {record.semester.name}</caption>
          <thead className="bg-neutral-50">
            <tr>
              <th scope="col" className="px-5 py-2.5 text-left text-xs font-semibold text-neutral-600 uppercase">Course</th>
              <th scope="col" className="px-3 py-2.5 text-right text-xs font-semibold text-neutral-600 uppercase">Credits</th>
              <th scope="col" className="hidden px-3 py-2.5 text-right text-xs font-semibold text-neutral-600 uppercase sm:table-cell">CAT</th>
              <th scope="col" className="hidden px-3 py-2.5 text-right text-xs font-semibold text-neutral-600 uppercase sm:table-cell">Exam</th>
              <th scope="col" className="px-3 py-2.5 text-right text-xs font-semibold text-neutral-600 uppercase">Total</th>
              <th scope="col" className="px-3 py-2.5 text-left text-xs font-semibold text-neutral-600 uppercase">Grade</th>
              <th scope="col" className="px-5 py-2.5 text-right text-xs font-semibold text-neutral-600 uppercase">Points</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {record.courses.map((c) => (
              <tr key={c.resultId}>
                <td className="px-5 py-2.5">
                  <p className="font-medium text-neutral-900">{c.courseCode}</p>
                  <p className="text-neutral-600">{c.courseTitle}</p>
                  {!c.countsTowardCgpa && <p className="text-xs text-amber-700">Repeated later — not counted in CGPA</p>}
                </td>
                <td className="px-3 py-2.5 text-right tabular-nums">{c.credits}</td>
                <td className="hidden px-3 py-2.5 text-right text-neutral-600 tabular-nums sm:table-cell">{formatNumber(c.catMark)}</td>
                <td className="hidden px-3 py-2.5 text-right text-neutral-600 tabular-nums sm:table-cell">{formatNumber(c.examMark)}</td>
                <td className="px-3 py-2.5 text-right tabular-nums">{formatNumber(c.totalMark)}</td>
                <td className="px-3 py-2.5">
                  <span className="inline-flex items-center gap-2">
                    <span className={c.passed ? 'font-semibold text-neutral-900' : 'font-semibold text-red-700'}>{c.grade}</span>
                    {!c.passed && <Badge tone="danger">Fail</Badge>}
                  </span>
                </td>
                <td className="px-5 py-2.5 text-right tabular-nums">{c.gradePoints.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

/** The student's finalized results by semester with GPA and CGPA. Marks still being worked on are not shown. */
export function ResultsPage() {
  const query = useQuery({ queryKey: ['results', 'me'], queryFn: getMyResults })

  if (query.error) {
    return (
      <>
        <PageHeader image={graduatesSunset} imageClassName="object-[center_55%]" title="My results" />
        <Alert tone="error">{errorMessage(query.error)}</Alert>
      </>
    )
  }
  if (!query.data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading results…" />
      </div>
    )
  }

  const record = query.data
  return (
    <>
      <PageHeader image={graduatesSunset} imageClassName="object-[center_55%]" title="My results" description={`${record.student.studentNumber} · ${record.student.programName}`} />
      {record.semesters.length === 0 ? (
        <Card>
          <EmptyState
            icon={GraduationCap}
            title="No results yet"
            description="Results appear here once the registrar has finalized them for a course you took."
          />
        </Card>
      ) : (
        <div className="space-y-4">
          <dl className="grid gap-4 sm:grid-cols-3">
            {(
              [
                ['Cumulative GPA', formatGpa(record.cgpa)],
                ['Credits earned', String(record.creditsEarned)],
                ['Semesters with results', String(record.semesters.length)],
              ] as const
            ).map(([label, value]) => (
              <div key={label} className="rounded-lg border border-neutral-200 bg-surface px-5 py-4 shadow-xs">
                <dt className="text-sm text-neutral-500">{label}</dt>
                <dd className="mt-1 text-2xl font-semibold text-neutral-900 tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
          {record.semesters.map((s) => (
            <SemesterCard key={s.semester.id} record={s} />
          ))}
          <p className="text-xs text-neutral-500">
            GPA is the credit-weighted average of grade points, rounded to two decimals. When a course is repeated, only the latest
            attempt counts towards the cumulative GPA.
          </p>
        </div>
      )}
    </>
  )
}
