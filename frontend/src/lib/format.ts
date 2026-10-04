import type { Role, UserStatus } from '@/types/api'

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const date = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

export function formatDateTime(iso: string | null | undefined, fallback = '—'): string {
  return iso ? dateTime.format(new Date(iso)) : fallback
}

export function formatDate(iso: string | null | undefined, fallback = '—'): string {
  return iso ? date.format(new Date(iso)) : fallback
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
