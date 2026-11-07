import { useQuery } from '@tanstack/react-query'
import { Bell, CalendarCheck, CalendarDays, Clock, MapPin, Megaphone, TrendingUp } from 'lucide-react'
import { Link } from 'react-router'
import { getStudentDashboard } from '@/api/dashboards'
import { errorMessage } from '@/api/errors'
import { getAnnouncements, getNotifications } from '@/api/notifications'
import { getMyTimetable } from '@/api/registration'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardFooter, CardHeader, Inset } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { NotificationContent } from '@/features/notifications/NotificationContent'
import { NOTIFICATIONS_KEY } from '@/features/notifications/useNotificationActions'
import { CreditMeter } from '@/features/student/registration/CreditMeter'
import { REGISTRATION_STATUS_LABELS } from '@/features/student/registration/labels'
import { cn } from '@/lib/cn'
import { useNow } from '@/hooks/useNow'
import { shortTime } from '@/lib/datetime'
import { formatDateTime, timeAgo } from '@/lib/format'
import { balanceStatus, formatMoney } from '@/lib/money'
import { DashboardLoading, StatRow, StatTile } from './figures'
import { dayLabel, startsIn, upcomingClasses } from './schedule'
import { TimeChart } from './TimeChart'

const RECENT_NOTIFICATIONS = 4
const UPCOMING = 4
const ANNOUNCEMENTS = 3
const GPA_TICKS = [0, 1, 2, 3, 4]
// A course keeps its colour everywhere on the page; the code is always printed beside it.
const COURSE_BAR = ['bg-course-1', 'bg-course-2', 'bg-course-3']

const linkAction = 'inline-flex h-8 items-center gap-1.5 rounded-lg border border-neutral-200 bg-surface px-2.5 text-xs font-medium text-neutral-700 hover:bg-neutral-50'

function CodeChip({ children }: { children: string }) {
  return <span className="rounded-md border border-neutral-200 bg-surface px-1.5 py-0.5 font-mono text-[10px] font-medium text-neutral-600">{children}</span>
}

function UpcomingClassesCard() {
  const timetable = useQuery({ queryKey: ['timetable', 'me'], queryFn: getMyTimetable })
  const now = useNow()
  const entries = timetable.data?.entries ?? []
  const codes = [...new Set(entries.map((e) => e.courseCode))].sort()
  const next = upcomingClasses(entries, now, UPCOMING)

  return (
    <Card className="flex flex-col">
      <CardHeader
        title="Upcoming classes"
        description="Your next classes at a glance."
        actions={
          <Link to="/student/timetable" className={linkAction}>
            <CalendarDays aria-hidden="true" className="size-3.5" /> View timetable
          </Link>
        }
      />
      {timetable.error ? (
        <CardBody>
          <Alert tone="error">{errorMessage(timetable.error)}</Alert>
        </CardBody>
      ) : next.length === 0 ? (
        <EmptyState icon={CalendarDays} title="No classes yet" description="Classes appear here once your registration is submitted." />
      ) : (
        <ul aria-label="Upcoming classes" className="flex-1 space-y-2.5 px-5 pb-4">
          {next.map(({ entry: e, starts }) => (
            <li key={`${e.offeringId}-${e.dayOfWeek}-${e.startTime}`}>
              <Inset className="relative overflow-hidden py-3 pr-3 pl-4">
                <span aria-hidden="true" className={cn('absolute inset-y-2.5 left-0 w-1 rounded-r-full', COURSE_BAR[codes.indexOf(e.courseCode) % COURSE_BAR.length])} />
                <div className="flex items-center justify-between gap-2">
                  <CodeChip>{e.courseCode}</CodeChip>
                  <span className="text-[11px] font-medium text-neutral-500">{dayLabel(starts, now)}</span>
                </div>
                <p className="mt-1.5 truncate text-sm font-semibold text-neutral-900">{e.courseTitle}</p>
                <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-neutral-500">
                  <span className="inline-flex items-center gap-1 font-mono">
                    <Clock aria-hidden="true" className="size-3" />
                    {shortTime(e.startTime)} → {shortTime(e.endTime)}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <MapPin aria-hidden="true" className="size-3" />
                    {e.venue}
                  </span>
                </p>
              </Inset>
            </li>
          ))}
        </ul>
      )}
      {next[0] && (
        <CardFooter>
          <span>
            Next class <span className="font-semibold text-neutral-800">{startsIn(next[0].starts, now)}</span>
          </span>
          <span>{timetable.data?.semester.name}</span>
        </CardFooter>
      )}
    </Card>
  )
}

function AnnouncementsCard() {
  const announcements = useQuery({ queryKey: ['announcements', { size: ANNOUNCEMENTS }], queryFn: () => getAnnouncements({ size: ANNOUNCEMENTS }) })
  const items = announcements.data?.content ?? []
  return (
    <Card className="flex flex-col">
      <CardHeader
        title="Announcements"
        description="From the lecturers of your courses."
        actions={
          <Link to="/announcements" className={linkAction}>
            View all
          </Link>
        }
      />
      {items.length === 0 ? (
        <EmptyState icon={Megaphone} title="No announcements" description="Messages from your lecturers appear here." />
      ) : (
        <ul aria-label="Latest announcements" className="flex-1 space-y-2.5 px-5 pb-5">
          {items.map((a) => (
            <li key={a.id}>
              <Inset className="p-3">
                <div className="flex flex-wrap items-center gap-1.5">
                  <Badge tone="brand">Announcement</Badge>
                  <CodeChip>{a.courseCode}</CodeChip>
                </div>
                <p className="mt-2 text-sm font-semibold text-neutral-900">{a.title}</p>
                <p className="mt-1 line-clamp-2 text-xs text-neutral-600">{a.body}</p>
                <div className="mt-2.5 flex items-center justify-between gap-2 text-[11px] text-neutral-500">
                  <span>
                    {a.authorName} · <time dateTime={a.createdAt}>{timeAgo(a.createdAt)}</time>
                  </span>
                  <Link to={`/announcements?offeringId=${a.offeringId}`} className="font-medium text-brand-700 hover:text-brand-900">
                    Read more<span className="sr-only">: {a.title}</span>
                  </Link>
                </div>
              </Inset>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

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
    <div className="space-y-5">
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

      <div className="grid items-start gap-5 lg:grid-cols-3">
        <div className="space-y-5">
          <UpcomingClassesCard />
          <AnnouncementsCard />
        </div>
        <div className="space-y-5 lg:col-span-2">
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
          <div className="grid items-start gap-5 md:grid-cols-2">
        <Card>
          <CardHeader title="This semester" description={d.semester?.name ?? 'No current semester'} />
          <CardBody>
            {d.registration ? (
              <>
                <CreditMeter total={d.registration.credits} min={d.registration.minCredits} max={d.registration.maxCredits} />
                <p className="mt-3 text-sm text-neutral-600">
                  {d.registration.courses} {d.registration.courses === 1 ? 'course' : 'courses'} on your registration.
                </p>
              </>
            ) : (
              <p className="text-sm text-neutral-500">No semester is open yet.</p>
            )}
          </CardBody>
          <CardHeader title="Upcoming exams" />
          {d.upcomingExams.length === 0 ? (
            <EmptyState icon={CalendarCheck} title="No exams scheduled" description="Exams for your registered courses appear here once they are published." />
          ) : (
            <ul aria-label="Upcoming exams" className="space-y-2 px-5 pb-5">
              {d.upcomingExams.map((e) => (
                <li key={e.offeringId}>
                  <Inset className="px-3 py-2.5 text-sm">
                    <p className="flex items-center gap-2 font-medium text-neutral-900">
                      <CodeChip>{e.courseCode}</CodeChip>
                      <span className="truncate">{e.courseTitle}</span>
                    </p>
                    <p className="mt-1 text-xs text-neutral-500">
                      {formatDateTime(e.startsAt)}
                      {e.venue ? ` · ${e.venue}` : ''}
                    </p>
                  </Inset>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <CardHeader
            title="Recent notifications"
            actions={
              <Link to="/notifications" className={linkAction}>
                View all
              </Link>
            }
          />
          {notifications.data?.content.length ? (
            <ul aria-label="Recent notifications" className="divide-y divide-neutral-100 px-2 pb-2">
              {notifications.data.content.map((n) => (
                <li key={n.id} className="px-3 py-3">
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
      </div>
    </div>
  )
}
