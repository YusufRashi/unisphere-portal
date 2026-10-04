import type { BadgeTone } from '@/components/ui/Badge'
import type { Availability, RegistrationStatus } from '@/types/api'

export const REGISTRATION_STATUS_LABELS: Record<RegistrationStatus, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Awaiting approval',
  APPROVED: 'Registered',
  REJECTED: 'Returned for changes',
}

export const REGISTRATION_STATUS_TONES: Record<RegistrationStatus, BadgeTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
}

/** Short badge text for catalogue rows that cannot be added; the full reason comes from the API. */
export const AVAILABILITY_LABELS: Record<Exclude<Availability, 'AVAILABLE'>, string> = {
  IN_REGISTRATION: 'Added',
  SAME_COURSE_REGISTERED: 'Other section added',
  PREREQUISITES_MISSING: 'Prerequisites missing',
  FULL: 'Full',
  TIMETABLE_CLASH: 'Timetable clash',
  CREDIT_LIMIT: 'Over credit limit',
}
