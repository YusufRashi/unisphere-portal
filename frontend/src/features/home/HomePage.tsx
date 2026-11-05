import { lazy, Suspense, type ComponentType } from 'react'
import { useAuth } from '@/auth/authContext'
import { PageHeader } from '@/components/layout/PageHeader'
import { DashboardLoading } from '@/features/dashboard/figures'
import { ROLE_LABELS } from '@/lib/format'
import type { Role } from '@/types/api'

// Code-split per role: each user only downloads their own dashboard, and only those with charts pull in the
// chart library.
const DASHBOARDS: { role: Role; Dashboard: ComponentType }[] = [
  { role: 'STUDENT', Dashboard: lazy(async () => ({ default: (await import('@/features/dashboard/StudentDashboard')).StudentDashboard })) },
  { role: 'LECTURER', Dashboard: lazy(async () => ({ default: (await import('@/features/dashboard/LecturerDashboard')).LecturerDashboard })) },
  { role: 'ADMIN', Dashboard: lazy(async () => ({ default: (await import('@/features/dashboard/AdminDashboard')).AdminDashboard })) },
  { role: 'FINANCE', Dashboard: lazy(async () => ({ default: (await import('@/features/dashboard/FinanceDashboard')).FinanceDashboard })) },
]

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

/** The page after signing in: the dashboard of each role the user holds. */
export function HomePage() {
  const { user } = useAuth()
  if (!user) return null
  const mine = DASHBOARDS.filter((d) => user.roles.includes(d.role))

  return (
    <>
      <PageHeader title={`${greeting()}, ${user.firstName}`} description="Here is where things stand." />
      <div className="space-y-10">
        {mine.map(({ role, Dashboard }) => (
          <section key={role} aria-label={`${ROLE_LABELS[role]} dashboard`}>
            {mine.length > 1 && <h2 className="mb-3 text-base font-semibold text-slate-900">{ROLE_LABELS[role]}</h2>}
            <Suspense fallback={<DashboardLoading label="Loading your dashboard…" />}>
              <Dashboard />
            </Suspense>
          </section>
        ))}
      </div>
    </>
  )
}
