import type { AwardLevel, Role, SemesterStatus, StudentStatus, UserStatus } from '@/types/api'

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const date = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

export function formatDateTime(iso: string | null | undefined, fallback = '—'): string {
  return iso ? dateTime.format(new Date(iso)) : fallback
}

export function formatDate(iso: string | null | undefined, fallback = '—'): string {
  return iso ? date.format(new Date(iso)) : fallback
}

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

/** "just now", "5 minutes ago", "yesterday"; the date itself once it is more than a week old. */
export function timeAgo(iso: string, now: number = Date.now()): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return 'just now'
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute')
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), 'hour')
  if (abs < 7 * 86_400) return relative.format(Math.round(seconds / 86_400), 'day')
  return formatDate(iso)
}

export const ROLE_LABELS: Record<Role, string> = {
  STUDENT: 'Student',
  LECTURER: 'Lecturer',
  ADMIN: 'Registrar / Admin',
  FINANCE: 'Finance',
}

export const STATUS_LABELS: Record<UserStatus, string> = {
  PENDING_ACTIVATION: 'Pending activation',
  ACTIVE: 'Active',
  DEACTIVATED: 'Deactivated',
}

export function initials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()
}

export const AWARD_LABELS: Record<AwardLevel, string> = {
  CERTIFICATE: 'Certificate',
  DIPLOMA: 'Diploma',
  BACHELOR: "Bachelor's",
  MASTER: "Master's",
  DOCTORATE: 'Doctorate',
}

export const SEMESTER_STATUS_LABELS: Record<SemesterStatus, string> = {
  UPCOMING: 'Upcoming',
  REGISTRATION_OPEN: 'Registration open',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
}

export const STUDENT_STATUS_LABELS: Record<StudentStatus, string> = {
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  GRADUATED: 'Graduated',
  WITHDRAWN: 'Withdrawn',
}

/** Converts an empty form value to null for optional API fields. */
export function emptyToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}
