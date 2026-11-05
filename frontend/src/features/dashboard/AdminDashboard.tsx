import { useQuery } from '@tanstack/react-query'
import { BarChart3, FileCheck2 } from 'lucide-react'
import { Link } from 'react-router'
import { getAdminDashboard } from '@/api/dashboards'
import { errorMessage } from '@/api/errors'
import { Alert } from '@/components/ui/Alert'
import { Card, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { formatDateTime } from '@/lib/format'
import { formatMoney } from '@/lib/money'
import { DashboardLoading, StatRow, StatTile } from './figures'
import { compactNumber, longDay, shortDay } from './periods'
import { TimeChart } from './TimeChart'

export function AdminDashboard() {
  const query = useQuery({ queryKey: ['dashboard', 'admin'], queryFn: getAdminDashboard })

  if (query.error) return <Alert tone="error">{errorMessage(query.error)}</Alert>
  if (!query.data) return <DashboardLoading label="Loading the dashboard…" />
  const d = query.data
  const activity = d.registrationActivity.map((p) => ({
    label: shortDay(p.date),
    period: longDay(p.date),
    value: p.count,
    text: `${p.count} ${p.count === 1 ? 'registration' : 'registrations'}`,
  }))
  const totalActivity = d.registrationActivity.reduce((sum, p) => sum + p.count, 0)
  const registered = d.registrations.approved + d.registrations.awaitingApproval

  return (
    <div className="space-y-6">
      <StatRow label="University summary">
        <StatTile
          label="Students"
          value={d.totalStudents.toLocaleString()}
          hint={`${d.activeStudents.toLocaleString()} active`}
          link={{ to: '/admin/students', label: 'Students' }}
        />
        <StatTile label="Lecturers" value={d.lecturers.toLocaleString()} link={{ to: '/admin/lecturers', label: 'Lecturers' }} />
        <StatTile label="Active courses" value={d.activeCourses.toLocaleString()} link={{ to: '/admin/courses', label: 'Courses' }} />
        <StatTile
          label="Outstanding fees"
          value={formatMoney(d.outstandingFees.amount, d.outstandingFees.currency)}
          hint={`${d.outstandingFees.students.toLocaleString()} ${d.outstandingFees.students === 1 ? 'student owes' : 'students owe'}`}
          link={{ to: '/finance/reports', label: 'Finance reports' }}
        />
        <StatTile
          label="Current registrations"
          value={registered.toLocaleString()}
          hint={d.semester?.name ?? 'No current semester'}
          link={{ to: '/admin/registrations', label: 'Registrations' }}
        />
        <StatTile
          label="Awaiting approval"
          value={d.registrations.awaitingApproval.toLocaleString()}
          hint="Registrations to review"
          link={d.registrations.awaitingApproval > 0 ? { to: '/admin/registrations?status=SUBMITTED', label: 'Review' } : undefined}
        />
        <StatTile label="Drafts" value={d.registrations.draft.toLocaleString()} hint="Started but not submitted" />
        <StatTile
          label="Results to review"
          value={d.resultsAwaitingReview.toLocaleString()}
          hint="Submitted by lecturers"
          link={d.resultsAwaitingReview > 0 ? { to: '/admin/results?status=SUBMITTED', label: 'Review' } : undefined}
        />
      </StatRow>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <TimeChart
            title="Registrations submitted"
            description={`${totalActivity.toLocaleString()} in the last 30 days${d.semester ? `, ${d.semester.name}` : ''}.`}
            kind="column"
            data={activity}
            periodHeading="Day (UTC)"
            valueHeading="Registrations"
            formatTick={compactNumber}
            empty={
              totalActivity === 0 ? (
                <EmptyState icon={BarChart3} title="No registrations yet" description="Submitted registrations of the current semester appear here by day." />
              ) : undefined
            }
          />
        </div>
        <Card>
          <CardHeader title="Recently finalized results" />
          {d.recentResults.length === 0 ? (
            <EmptyState icon={FileCheck2} title="Nothing finalized yet" description="Results appear here once you finalize them." />
          ) : (
            <ul aria-label="Recently finalized results" className="divide-y divide-slate-100">
              {d.recentResults.map((r) => (
                <li key={r.offeringId} className="px-5 py-3 text-sm">
                  <Link to={`/admin/results/${r.offeringId}`} className="font-medium text-slate-900 hover:text-brand-800">
                    {r.courseCode} · Section {r.section}
                  </Link>
                  <p className="truncate text-slate-600">{r.courseTitle}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {r.passed} of {r.students} passed · {formatDateTime(r.finalizedAt)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
