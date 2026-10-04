import { LayoutDashboard, Settings, Users, type LucideIcon } from 'lucide-react'
import type { Role } from '@/types/api'

export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** Visible to users holding any of these roles; omit for everyone. */
  roles?: Role[]
}

export interface NavSection {
  title?: string
  items: NavItem[]
}

/**
 * Sidebar structure. Each phase adds the modules it delivers here (e.g. Phase 2 adds Departments,
 * Programs, Courses and Semesters under "Administration"), so the menu only ever shows working screens.
 */
export const NAVIGATION: NavSection[] = [
  {
    items: [{ label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard }],
  },
  {
    title: 'Administration',
    items: [{ label: 'Users', to: '/admin/users', icon: Users, roles: ['ADMIN'] }],
  },
  {
    title: 'Account',
    items: [{ label: 'Account settings', to: '/account', icon: Settings }],
  },
]

export function navigationFor(roles: Role[]): NavSection[] {
  return NAVIGATION.map((section) => ({
    ...section,
    items: section.items.filter((item) => !item.roles || item.roles.some((r) => roles.includes(r))),
  })).filter((section) => section.items.length > 0)
}
