import { useQuery } from '@tanstack/react-query'
import { BookOpen } from 'lucide-react'
import { Link } from 'react-router'
import { getLecturerDashboard } from '@/api/dashboards'
import { errorMessage } from '@/api/errors'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Card, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { RESULTS_STATUS_LABELS, RESULTS_STATUS_TONES } from '@/features/results/labels'
import { DashboardLoading, Meter, StatRow, StatTile } from './figures'

export function LecturerDashboard() {
  const query = useQuery({ queryKey: ['dashboard', 'lecturer'], queryFn: getLecturerDashboard })

  if (query.error) return <Alert tone="error">{errorMessage(query.error)}</Alert>
  if (!query.data) return <DashboardLoading label="Loading your dashboard…" />
  const d = query.data

  return (
    <div className="space-y-6">
      <StatRow label="Your teaching">
        <StatTile label="Courses" value={d.courses} hint={d.semester?.name ?? 'No current semester'} link={{ to: '/lecturer/courses', label: 'My courses' }} />
        <StatTile label="Students" value={d.students} hint="Across all your classes" />
        <StatTile label="Marks to enter" value={d.marksMissing} hint="In courses not yet submitted" />
        <StatTile label="Awaiting review" value={d.awaitingReview} hint="Submitted to the registrar" />
      </StatRow>

      <Card>
        <CardHeader title="Marks by course" description="How much of each class has complete marks." />
        {d.offerings.length === 0 ? (
          <EmptyState icon={BookOpen} title="No courses this semester" description="Courses assigned to you by the registrar appear here." />
        ) : (
          <ul aria-label="Marks by course" className="divide-y divide-neutral-100">
            {d.offerings.map((o) => (
              <li key={o.offeringId} className="px-5 py-4">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <Link to={`/lecturer/courses/${o.offeringId}`} className="min-w-0 text-sm font-medium text-neutral-900 hover:text-brand-800">
                    {o.courseCode} · Section {o.section} <span className="font-normal text-neutral-600">{o.courseTitle}</span>
                  </Link>
                  <Badge tone={RESULTS_STATUS_TONES[o.resultsStatus]}>{RESULTS_STATUS_LABELS[o.resultsStatus]}</Badge>
                </div>
                <Meter
                  label={`Marks entered for ${o.courseCode}`}
                  value={o.marksComplete}
                  max={o.classSize}
                  text={o.classSize === 0 ? 'No students yet' : `${o.marksComplete} of ${o.classSize} students marked`}
                />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
