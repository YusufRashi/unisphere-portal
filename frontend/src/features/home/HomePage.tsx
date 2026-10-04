import {
  BookOpen,
  ClipboardList,
  FileText,
  GraduationCap,
  KeyRound,
  Landmark,
  Receipt,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router'
import { useAuth } from '@/auth/authContext'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { ROLE_LABELS } from '@/lib/format'
import type { Role } from '@/types/api'

interface Module {
  title: string
  description: string
  icon: LucideIcon
  roles: Role[]
  /** Route when the module is available; undefined while it is still on the roadmap. */
  to?: string
}

// Modules per role. Entries without a route are scheduled in docs/05-roadmap.md and gain a link as
// each phase ships; Phase 8 replaces this page with full role dashboards and charts.
const MODULES: Module[] = [
  { title: 'User accounts', description: 'Search, review and deactivate accounts.', icon: Users, roles: ['ADMIN'], to: '/admin/users' },
  { title: 'Academic structure', description: 'Departments, programs, courses and semesters.', icon: Landmark, roles: ['ADMIN'] },
  { title: 'Course registration', description: 'Browse this semester’s courses and register.', icon: ClipboardList, roles: ['STUDENT'] },
  { title: 'Results & GPA', description: 'Semester results, GPA and CGPA.', icon: GraduationCap, roles: ['STUDENT'] },
  { title: 'Transcript', description: 'Download an unofficial transcript.', icon: FileText, roles: ['STUDENT'] },
  { title: 'My courses', description: 'Class lists, marks entry and statistics.', icon: BookOpen, roles: ['LECTURER'] },
  { title: 'Fee accounts', description: 'Balances, payments and reports.', icon: Receipt, roles: ['FINANCE'] },
  { title: 'Account settings', description: 'Change your password.', icon: KeyRound, roles: ['STUDENT', 'LECTURER', 'ADMIN', 'FINANCE'], to: '/account' },
]

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export function HomePage() {
  const { user } = useAuth()
  if (!user) return null
  const modules = MODULES.filter((m) => m.roles.some((r) => user.roles.includes(r)))

  return (
    <>
      <PageHeader title={`${greeting()}, ${user.firstName}`} description="Here is what you can do in UniSphere." />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Your workspace" />
          <ul className="grid gap-px overflow-hidden rounded-b-lg bg-slate-200 sm:grid-cols-2">
            {modules.map(({ title, description, icon: Icon, to }) => {
              const content = (
                <div className="flex h-full gap-3 bg-white p-4">
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-800">
                    <Icon aria-hidden="true" className="size-4" />
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="font-medium text-slate-900">{title}</p>
                      {!to && <Badge>Coming soon</Badge>}
                    </div>
                    <p className="mt-0.5 text-sm text-slate-500">{description}</p>
                  </div>
                </div>
              )
              return (
                <li key={title}>
                  {to ? (
                    <Link to={to} className="block h-full transition-colors hover:[&>div]:bg-slate-50">
                      {content}
                    </Link>
                  ) : (
                    content
                  )}
                </li>
              )
            })}
          </ul>
        </Card>

        <Card>
          <CardHeader title="Your account" />
          <CardBody>
            <dl className="space-y-3 text-sm">
              <div>
                <dt className="text-slate-500">Name</dt>
                <dd className="font-medium text-slate-900">
                  {user.firstName} {user.lastName}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Email</dt>
                <dd className="font-medium break-all text-slate-900">{user.email}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Roles</dt>
                <dd className="mt-1 flex flex-wrap gap-1.5">
                  {user.roles.map((r) => (
                    <Badge key={r} tone="brand">
                      {ROLE_LABELS[r]}
                    </Badge>
                  ))}
                </dd>
              </div>
            </dl>
          </CardBody>
        </Card>
      </div>
    </>
  )
}
