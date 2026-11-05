import { useQuery } from '@tanstack/react-query'
import { Bell, CalendarCheck, TrendingUp } from 'lucide-react'
import { Link } from 'react-router'
import { getStudentDashboard } from '@/api/dashboards'
import { errorMessage } from '@/api/errors'
import { getNotifications } from '@/api/notifications'
import { Alert } from '@/components/ui/Alert'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { NotificationContent } from '@/features/notifications/NotificationContent'
import { NOTIFICATIONS_KEY } from '@/features/notifications/useNotificationActions'
import { CreditMeter } from '@/features/student/registration/CreditMeter'
import { REGISTRATION_STATUS_LABELS } from '@/features/student/registration/labels'
import { formatDateTime } from '@/lib/format'
import { balanceStatus, formatMoney } from '@/lib/money'
import { DashboardLoading, StatRow, StatTile } from './figures'
import { TimeChart } from './TimeChart'

const RECENT_NOTIFICATIONS = 5
const GPA_TICKS = [0, 1, 2, 3, 4]

export function StudentDashboard() {
  const query = useQuery({ queryKey: ['dashboard', 'student'], queryFn: getStudentDashboard })
  const notifications = useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list', { size: RECENT_NOTIFICATIONS }],
    queryFn: () => getNotifications({ size: RECENT_NOTIFICATIONS }),
  })

  if (query.error) return <Alert tone="error">{errorMessage(query.error)}</Alert>
  if (!query.data) return <DashboardLoading label="Loading your dashboard…" />
  const d = query.data
  const balance = balanceStatus(d.fees.balance)
  const trend = d.gpaTrend.map((p) => ({
    label: p.semester.code,
    period: p.semester.name,
    value: p.gpa,
    text: p.gpa.toFixed(2),
  }))

  return (
    <div className="space-y-6">
      <StatRow label="Your summary">
        <StatTile
          label="CGPA"
          value={d.cgpa === null ? '—' : d.cgpa.toFixed(2)}
          hint={d.cgpa === null ? 'Appears with your first final results' : undefined}
          link={{ to: '/student/results', label: 'My results' }}
        />
        <StatTile label="Credits earned" value={d.creditsEarned} link={{ to: '/student/transcript', label: 'Transcript' }} />
        <StatTile
          label={balance.label}
          value={formatMoney(balance.amount, d.fees.currency)}
          link={{ to: '/student/fees', label: d.fees.balance > 0 ? 'Pay fees' : 'Statement' }}
        />
        <StatTile
          label="Registration"
          value={d.registration?.status ? REGISTRATION_STATUS_LABELS[d.registration.status] : 'Not started'}
          hint={d.semester?.name ?? 'No current semester'}
          link={d.registration ? { to: '/student/registration', label: 'Course registration' } : undefined}
        />
      </StatRow>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <TimeChart
            title="GPA by semester"
            description="From your final results, on a 4.0 scale."
            kind="line"
            data={trend}
            periodHeading="Semester"
            valueHeading="GPA"
            domain={[0, 4]}
            ticks={GPA_TICKS}
            formatTick={String}
            empty={
              trend.length < 2 ? (
                <EmptyState
                  icon={TrendingUp}
                  title={trend.length === 0 ? 'No final results yet' : `${trend[0]?.period}: GPA ${trend[0]?.text}`}
                  description="Your trend appears once you have final results from two semesters."
                />
              ) : undefined
            }
          />
        </div>
        <Card>
          <CardHeader title="This semester" description={d.semester?.name} />
          <CardBody>
            {d.registration ? (
              <>
                <CreditMeter total={d.registration.credits} min={d.registration.minCredits} max={d.registration.maxCredits} />
                <p className="mt-3 text-sm text-slate-600">
                  {d.registration.courses} {d.registration.courses === 1 ? 'course' : 'courses'} on your registration.
                </p>
              </>
            ) : (
              <p className="text-sm text-slate-500">No semester is open yet.</p>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Upcoming exams" />
          {d.upcomingExams.length === 0 ? (
            <EmptyState icon={CalendarCheck} title="No exams scheduled" description="Exams for your registered courses appear here once they are published." />
          ) : (
            <ul aria-label="Upcoming exams" className="divide-y divide-slate-100">
              {d.upcomingExams.map((e) => (
                <li key={e.offeringId} className="px-5 py-3 text-sm">
                  <p className="font-medium text-slate-900">
                    {e.courseCode} <span className="font-normal text-slate-600">{e.courseTitle}</span>
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {formatDateTime(e.startsAt)}
                    {e.venue ? ` · ${e.venue}` : ''}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <CardHeader
            title="Recent notifications"
            actions={
              <Link to="/notifications" className="text-xs font-medium text-brand-700 hover:text-brand-900">
                View all →
              </Link>
            }
          />
          {notifications.data?.content.length ? (
            <ul aria-label="Recent notifications" className="divide-y divide-slate-100">
              {notifications.data.content.map((n) => (
                <li key={n.id} className="px-5 py-3">
                  <NotificationContent notification={n} clampMessage />
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState icon={Bell} title="Nothing new" description="Updates about your registration, results and fees appear here." />
          )}
        </Card>
      </div>
    </div>
  )
}
