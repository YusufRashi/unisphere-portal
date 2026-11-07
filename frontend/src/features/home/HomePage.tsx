import { lazy, Suspense, useState, type ComponentType } from 'react'
import { useAuth } from '@/auth/authContext'
import { PillTabs } from '@/components/ui/PillTabs'
import { DashboardLoading } from '@/features/dashboard/figures'
import { WelcomeBanner } from '@/features/home/WelcomeBanner'
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

/** The page after signing in: the dashboard of the user's role, with a tab per role for people who have several. */
export function HomePage() {
  const { user } = useAuth()
  const mine = DASHBOARDS.filter((d) => user?.roles.includes(d.role))
  const [selected, setSelected] = useState<Role | undefined>(mine[0]?.role)
  if (!user) return null
  const current = mine.find((d) => d.role === selected) ?? mine[0]

  return (
    <>
      <WelcomeBanner firstName={user.firstName} />
      {mine.length > 1 && (
        <PillTabs
          label="Dashboard"
          className="mb-5"
          options={mine.map((d) => ({ value: d.role, label: ROLE_LABELS[d.role] }))}
          value={current?.role ?? mine[0]!.role}
          onChange={setSelected}
        />
      )}
      {current && (
        <section aria-label={`${ROLE_LABELS[current.role]} dashboard`}>
          <Suspense fallback={<DashboardLoading label="Loading your dashboard…" />}>
            <current.Dashboard />
          </Suspense>
        </section>
      )}
    </>
  )
}
