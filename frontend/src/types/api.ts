// Types mirroring the backend DTOs. Keep in sync with docs/04-api.md.

export const ROLES = ['STUDENT', 'LECTURER', 'ADMIN', 'FINANCE'] as const
export type Role = (typeof ROLES)[number]

export const USER_STATUSES = ['PENDING_ACTIVATION', 'ACTIVE', 'DEACTIVATED'] as const
export type UserStatus = (typeof USER_STATUSES)[number]

export interface CurrentUser {
  id: number
  email: string
  firstName: string
  lastName: string
  roles: Role[]
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: CurrentUser
}

export interface UserAccount {
  id: number
  email: string
  firstName: string
  lastName: string
  phone: string | null
  status: UserStatus
  roles: Role[]
  lastLoginAt: string | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** RFC 9457 Problem Details as returned by the API. */
export interface ProblemDetails {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
  violations?: string[]
  requestId?: string
  retryAfterSeconds?: number
}

// ---- Academic structure ------------------------------------------------------------------------

export interface DepartmentRef {
  id: number
  code: string
  name: string
}

export interface Department extends DepartmentRef {
  faculty: string | null
  programCount: number
  courseCount: number
  lecturerCount: number
}

export const AWARD_LEVELS = ['CERTIFICATE', 'DIPLOMA', 'BACHELOR', 'MASTER', 'DOCTORATE'] as const
export type AwardLevel = (typeof AWARD_LEVELS)[number]

export interface ProgramRef {
  id: number
  code: string
  name: string
}

export interface Program extends ProgramRef {
  awardLevel: AwardLevel
  durationYears: number
  active: boolean
  department: DepartmentRef
  studentCount: number
}

export interface CourseRef {
  id: number
  code: string
  title: string
  credits: number
}

export interface CourseSummary {
  id: number
  code: string
  title: string
  credits: number
  level: number
  active: boolean
  department: DepartmentRef
  prerequisiteCount: number
}

export interface CourseDetail {
  id: number
  code: string
  title: string
  description: string | null
  credits: number
  level: number
  active: boolean
  department: DepartmentRef
  prerequisites: CourseRef[]
  createdAt: string
  updatedAt: string
}

// ---- Calendar & offerings ------------------------------------------------------------------------

export const SEMESTER_STATUSES = ['UPCOMING', 'REGISTRATION_OPEN', 'IN_PROGRESS', 'COMPLETED'] as const
export type SemesterStatus = (typeof SEMESTER_STATUSES)[number]

export interface Semester {
  id: number
  code: string
  name: string
  academicYear: string
  term: number
  startDate: string
  endDate: string
  registrationOpensAt: string
  registrationClosesAt: string
  addDropDeadline: string
  /** What the administrator set. */
  registrationOpenFlag: boolean
  /** Whether students can register right now (flag and window). */
  registrationOpen: boolean
  requiresApproval: boolean
  minCredits: number
  maxCredits: number
  current: boolean
  status: SemesterStatus
  offeringCount: number
}

export const DAYS_OF_WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const
export type DayOfWeek = (typeof DAYS_OF_WEEK)[number]

export interface OfferingSession {
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  venue: string
}

export interface LecturerRef {
  id: number
  displayName: string
  staffNumber: string
}

export interface Offering {
  id: number
  course: { id: number; code: string; title: string; credits: number; level: number; departmentId: number; departmentCode: string }
  semester: { id: number; code: string; name: string }
  section: string
  lecturer: LecturerRef | null
  capacity: number
  enrolledCount: number
  seatsLeft: number
  catWeight: number
  examWeight: number
  examStartsAt: string | null
  examVenue: string | null
  resultsStatus: 'DRAFT' | 'SUBMITTED' | 'FINALIZED'
  sessions: OfferingSession[]
}

// ---- People ------------------------------------------------------------------------------------

export const STUDENT_STATUSES = ['ACTIVE', 'SUSPENDED', 'GRADUATED', 'WITHDRAWN'] as const
export type StudentStatus = (typeof STUDENT_STATUSES)[number]

export interface Student {
  id: number
  userId: number
  studentNumber: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  accountStatus: UserStatus
  program: ProgramRef
  department: DepartmentRef
  yearOfStudy: number
  admissionDate: string
  status: StudentStatus
  createdAt: string
}

export interface Lecturer {
  id: number
  userId: number
  staffNumber: string
  title: string | null
  firstName: string
  lastName: string
  displayName: string
  email: string
  phone: string | null
  accountStatus: UserStatus
  department: DepartmentRef
  office: string | null
}

// ---- Course registration -----------------------------------------------------------------------

export const REGISTRATION_STATUSES = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'] as const
export type RegistrationStatus = (typeof REGISTRATION_STATUSES)[number]

export interface SemesterWindow {
  id: number
  code: string
  name: string
  registrationOpensAt: string
  registrationClosesAt: string
  addDropDeadline: string
  registrationOpen: boolean
  addDropOpen: boolean
  minCredits: number
  maxCredits: number
  requiresApproval: boolean
}

export interface RegistrationItem {
  id: number
  offeringId: number
  courseId: number
  courseCode: string
  courseTitle: string
  credits: number
  section: string
  lecturer: string | null
  sessions: OfferingSession[]
  seatReserved: boolean
  addedAt: string
  droppedAt: string | null
}

export interface RegistrationPermissions {
  canAdd: boolean
  canRemove: boolean
  canSubmit: boolean
  /** True once submitted: removing a course is a drop that releases the seat. */
  removeMeansDrop: boolean
  lockedReason: string | null
}

export interface RegistrationView {
  id: number | null
  semester: SemesterWindow
  /** null until the student adds a first course. */
  status: RegistrationStatus | null
  items: RegistrationItem[]
  droppedItems: RegistrationItem[]
  totalCredits: number
  submittedAt: string | null
  reviewedAt: string | null
  rejectionReason: string | null
  permissions: RegistrationPermissions
}

export const AVAILABILITIES = [
  'AVAILABLE', 'IN_REGISTRATION', 'SAME_COURSE_REGISTERED', 'PREREQUISITES_MISSING', 'FULL', 'TIMETABLE_CLASH', 'CREDIT_LIMIT',
] as const
export type Availability = (typeof AVAILABILITIES)[number]

export interface CatalogueEntry {
  offering: Offering
  prerequisites: { courseId: number; code: string; met: boolean }[]
  availability: Availability
  reason: string | null
}

export interface TimetableEntry {
  offeringId: number
  courseCode: string
  courseTitle: string
  section: string
  lecturer: string | null
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  venue: string
}

export interface Timetable {
  semester: { id: number; code: string; name: string }
  status: RegistrationStatus | null
  entries: TimetableEntry[]
}

export interface RegistrationSummary {
  id: number
  student: { id: number; studentNumber: string; name: string; email: string; programCode: string }
  semester: { id: number; code: string; name: string }
  status: RegistrationStatus
  totalCredits: number
  courseCount: number
  submittedAt: string | null
  reviewedAt: string | null
}

export interface RegistrationDetail {
  summary: RegistrationSummary
  items: RegistrationItem[]
  droppedItems: RegistrationItem[]
  rejectionReason: string | null
  minCredits: number
  maxCredits: number
}
