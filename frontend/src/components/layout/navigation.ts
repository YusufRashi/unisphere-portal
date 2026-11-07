import {
  BookOpen,
  Building2,
  CalendarDays,
  CalendarRange,
  Bell,
  ClipboardCheck,
  ClipboardList,
  Clock,
  FileCheck2,
  FileText,
  GraduationCap,
  History,
  Landmark,
  LayoutDashboard,
  Megaphone,
  NotebookPen,
  Presentation,
  Receipt,
  Scale,
  ShieldCheck,
  Settings,
  UserCog,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
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

/** Sidebar structure. Each phase adds the modules it delivers, so the menu only ever shows working screens. */
export const NAVIGATION: NavSection[] = [
  {
    items: [
      { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
      { label: 'Notifications', to: '/notifications', icon: Bell },
    ],
  },
  {
    title: 'My studies',
    items: [
      { label: 'Course registration', to: '/student/registration', icon: ClipboardList, roles: ['STUDENT'] },
      { label: 'My timetable', to: '/student/timetable', icon: Clock, roles: ['STUDENT'] },
      { label: 'My results', to: '/student/results', icon: GraduationCap, roles: ['STUDENT'] },
      { label: 'Transcript', to: '/student/transcript', icon: FileText, roles: ['STUDENT'] },
      { label: 'Fees', to: '/student/fees', icon: Wallet, roles: ['STUDENT'] },
      { label: 'Announcements', to: '/announcements', icon: Megaphone, roles: ['STUDENT'] },
    ],
  },
  {
    title: 'Teaching',
    items: [
      { label: 'My courses', to: '/lecturer/courses', icon: NotebookPen, roles: ['LECTURER'] },
      { label: 'Announcements', to: '/announcements', icon: Megaphone, roles: ['LECTURER'] },
    ],
  },
  {
    title: 'Academic',
    items: [
      { label: 'Departments', to: '/admin/departments', icon: Building2, roles: ['ADMIN'] },
      { label: 'Programs', to: '/admin/programs', icon: GraduationCap, roles: ['ADMIN'] },
      { label: 'Courses', to: '/admin/courses', icon: BookOpen, roles: ['ADMIN'] },
      { label: 'Semesters', to: '/admin/semesters', icon: CalendarDays, roles: ['ADMIN'] },
      { label: 'Course offerings', to: '/admin/offerings', icon: CalendarRange, roles: ['ADMIN'] },
      { label: 'Registrations', to: '/admin/registrations', icon: ClipboardCheck, roles: ['ADMIN'] },
      { label: 'Results', to: '/admin/results', icon: FileCheck2, roles: ['ADMIN'] },
      { label: 'Grading scale', to: '/admin/grading-scale', icon: Scale, roles: ['ADMIN'] },
      { label: 'Verify transcript', to: '/admin/transcripts/verify', icon: ShieldCheck, roles: ['ADMIN'] },
    ],
  },
  {
    title: 'Finance',
    items: [
      { label: 'Fee accounts', to: '/finance/accounts', icon: Wallet, roles: ['FINANCE', 'ADMIN'] },
      { label: 'Fee structures', to: '/finance/structures', icon: Landmark, roles: ['FINANCE', 'ADMIN'] },
      { label: 'Finance reports', to: '/finance/reports', icon: Receipt, roles: ['FINANCE', 'ADMIN'] },
    ],
  },
  {
    title: 'People',
    items: [
      { label: 'Students', to: '/admin/students', icon: Users, roles: ['ADMIN'] },
      { label: 'Lecturers', to: '/admin/lecturers', icon: Presentation, roles: ['ADMIN'] },
      { label: 'All accounts', to: '/admin/users', icon: UserCog, roles: ['ADMIN'] },
      { label: 'Audit log', to: '/admin/audit', icon: History, roles: ['ADMIN'] },
    ],
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
