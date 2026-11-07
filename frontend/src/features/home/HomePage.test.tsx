import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as dashboardsApi from '@/api/dashboards'
import * as notificationsApi from '@/api/notifications'
import * as registrationApi from '@/api/registration'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { AdminDashboardData, FinanceDashboardData, LecturerDashboardData, Role, StudentDashboardData } from '@/types/api'
// HomePage loads these on demand. Loading them up front keeps a slow import from timing out a test's first query.
import '@/features/dashboard/AdminDashboard'
import '@/features/dashboard/FinanceDashboard'
import '@/features/dashboard/LecturerDashboard'
import '@/features/dashboard/StudentDashboard'
import { HomePage } from './HomePage'

vi.mock('@/api/dashboards')
vi.mock('@/api/notifications')
vi.mock('@/api/registration')

const semester = { id: 1, code: '2026-S1', name: 'Semester 1' }

const student: StudentDashboardData = {
  semester,
  cgpa: 3.42,
  creditsEarned: 45,
  gpaTrend: [
    { semester: { id: 0, code: '2025-S2', name: 'Semester 2, 2025' }, gpa: 3.2 },
    { semester, gpa: 3.6 },
  ],
  registration: { status: 'APPROVED', courses: 4, credits: 12, minCredits: 9, maxCredits: 24 },
  fees: { currency: 'KES', balance: 12500 },
  upcomingExams: [{ offeringId: 7, courseCode: 'CS101', courseTitle: 'Programming I', startsAt: '2026-12-01T08:00:00Z', venue: 'Hall B' }],
}

const lecturer: LecturerDashboardData = {
  semester,
  courses: 1,
  students: 30,
  marksMissing: 12,
  awaitingReview: 0,
  offerings: [{ offeringId: 7, courseCode: 'CS101', courseTitle: 'Programming I', section: 'A', resultsStatus: 'DRAFT', classSize: 30, marksComplete: 18 }],
}

const admin: AdminDashboardData = {
  semester,
  totalStudents: 1284,
  activeStudents: 1200,
  lecturers: 64,
  activeCourses: 120,
  registrations: { approved: 900, awaitingApproval: 15, draft: 40, rejected: 2 },
  resultsAwaitingReview: 3,
  outstandingFees: { currency: 'KES', amount: 250000, students: 18 },
  registrationActivity: [
    { date: '2026-11-04', count: 0 },
    { date: '2026-11-05', count: 1 },
  ],
  recentResults: [{ offeringId: 7, courseCode: 'CS101', courseTitle: 'Programming I', section: 'A', semesterName: 'Semester 1', finalizedAt: '2026-11-01T08:00:00Z', students: 30, passed: 27 }],
}

const finance: FinanceDashboardData = {
  currency: 'KES',
  totalCharged: 1000000,
  totalCollected: 750000,
  outstanding: 250000,
  studentsOwing: 18,
  collectionRate: 75,
  collectedLast30Days: 90000,
  weeklyCollections: [
    { weekStart: '2026-10-26', amount: 0 },
    { weekStart: '2026-11-02', amount: 90000 },
  ],
  methodsLast30Days: [{ method: 'MOBILE_MONEY', amount: 90000, payments: 12 }],
  largestBalances: [
    { studentId: 4, studentNumber: 'S2026-00004', name: 'Amina Otieno', email: 'amina@uni.test', programCode: 'BSC-CS', currency: 'KES', totalCharged: 85000, totalPaid: 0, balance: 85000 },
  ],
}

function renderPage(roles: Role[]) {
  return renderWithProviders([{ path: '/dashboard', element: <HomePage /> }], {
    auth: makeAuth({ user: makeUser(roles) }),
    initialPath: '/dashboard',
  })
}

function tile(summary: HTMLElement, label: string) {
  const term = within(summary).getByText(label)
  return within(term.parentElement as HTMLElement)
}

beforeEach(() => {
  vi.mocked(notificationsApi.getNotifications).mockResolvedValue({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 })
  vi.mocked(notificationsApi.getAnnouncements).mockResolvedValue({ content: [], page: 0, size: 3, totalElements: 0, totalPages: 0 })
  vi.mocked(registrationApi.getMyTimetable).mockResolvedValue({ semester: { id: 1, code: '2026-S1', name: 'Semester 1' }, status: 'APPROVED', entries: [] })
})

describe('HomePage', () => {
  it('shows a student their standing, load, exams and GPA as a table on request', async () => {
    const user = userEvent.setup()
    vi.mocked(dashboardsApi.getStudentDashboard).mockResolvedValue(student)
    renderPage(['STUDENT'])

    const summary = await screen.findByLabelText('Your summary')
    expect(tile(summary, 'CGPA').getByText('3.42')).toBeInTheDocument()
    expect(tile(summary, 'Balance due').getByText('KES 12,500.00')).toBeInTheDocument()
    expect(tile(summary, 'Balance due').getByRole('link', { name: /Pay fees/ })).toHaveAttribute('href', '/student/fees')
    expect(tile(summary, 'Registration').getByText('Registered')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Credit load' })).toHaveAttribute('aria-valuenow', '12')
    expect(within(screen.getByRole('list', { name: 'Upcoming exams' })).getByText(/Hall B/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Student' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /View as table/ }))
    const table = screen.getByRole('table', { name: 'GPA by semester' })
    expect(within(table).getByRole('row', { name: 'Semester 2, 2025 3.20' })).toBeInTheDocument()
    expect(within(table).getByRole('row', { name: 'Semester 1 3.60' })).toBeInTheDocument()
  })

  it('does not draw a trend from fewer than two semesters', async () => {
    vi.mocked(dashboardsApi.getStudentDashboard).mockResolvedValue({ ...student, gpaTrend: student.gpaTrend.slice(1), fees: { currency: 'KES', balance: 0 } })
    renderPage(['STUDENT'])

    expect(await screen.findByText('Semester 1: GPA 3.60')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /View as table/ })).not.toBeInTheDocument()
    expect(screen.getByText('Settled')).toBeInTheDocument()
  })

  it('handles a new student with nothing yet', async () => {
    vi.mocked(dashboardsApi.getStudentDashboard).mockResolvedValue({
      semester: null, cgpa: null, creditsEarned: 0, gpaTrend: [], registration: null, fees: { currency: 'KES', balance: 0 }, upcomingExams: [],
    })
    renderPage(['STUDENT'])

    expect(await screen.findByText('No final results yet')).toBeInTheDocument()
    expect(screen.getByText('Not started')).toBeInTheDocument()
    expect(screen.getByText('No semester is open yet.')).toBeInTheDocument()
    expect(screen.getByText('No exams scheduled')).toBeInTheDocument()
  })

  it('shows a lecturer what is left to mark', async () => {
    vi.mocked(dashboardsApi.getLecturerDashboard).mockResolvedValue(lecturer)
    renderPage(['LECTURER'])

    const summary = await screen.findByLabelText('Your teaching')
    expect(tile(summary, 'Marks to enter').getByText('12')).toBeInTheDocument()
    const meter = screen.getByRole('meter', { name: 'Marks entered for CS101' })
    expect(meter).toHaveAttribute('aria-valuenow', '18')
    expect(meter).toHaveAttribute('aria-valuemax', '30')
    expect(screen.getByRole('link', { name: /CS101 · Section A/ })).toHaveAttribute('href', '/lecturer/courses/7')
  })

  it('shows the registrar system-wide figures and links to what needs review', async () => {
    const user = userEvent.setup()
    vi.mocked(dashboardsApi.getAdminDashboard).mockResolvedValue(admin)
    renderPage(['ADMIN'])

    const summary = await screen.findByLabelText('University summary')
    expect(tile(summary, 'Current registrations').getByText((915).toLocaleString())).toBeInTheDocument()
    expect(tile(summary, 'Outstanding fees').getByText('KES 250,000.00')).toBeInTheDocument()
    expect(tile(summary, 'Awaiting approval').getByRole('link', { name: /Review/ })).toHaveAttribute('href', '/admin/registrations?status=SUBMITTED')
    expect(screen.getByText('27 of 30 passed', { exact: false })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /View as table/ }))
    expect(within(screen.getByRole('table', { name: 'Registrations submitted' })).getByText('1 registration')).toBeInTheDocument()
  })

  it('shows finance staff collections', async () => {
    vi.mocked(dashboardsApi.getFinanceDashboard).mockResolvedValue(finance)
    renderPage(['FINANCE'])

    const summary = await screen.findByLabelText('Collections summary')
    expect(tile(summary, 'Outstanding').getByText('KES 250,000.00')).toBeInTheDocument()
    expect(tile(summary, 'Collection rate').getByText('75.0%')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Payment methods' })).getByText('Mobile money')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Largest balances' })).getByRole('link', { name: /Amina Otieno/ })).toHaveAttribute('href', '/finance/accounts/4')
  })

  it('gives someone with several roles a tab per dashboard', async () => {
    const user = userEvent.setup()
    vi.mocked(dashboardsApi.getLecturerDashboard).mockResolvedValue(lecturer)
    vi.mocked(dashboardsApi.getAdminDashboard).mockResolvedValue(admin)
    renderPage(['LECTURER', 'ADMIN'])

    const tabs = screen.getByRole('group', { name: 'Dashboard' })
    expect(within(tabs).getByRole('button', { name: 'Lecturer' })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByLabelText('Your teaching')).toBeInTheDocument()
    expect(screen.queryByLabelText('University summary')).not.toBeInTheDocument()

    await user.click(within(tabs).getByRole('button', { name: 'Registrar / Admin' }))
    expect(await screen.findByLabelText('University summary')).toBeInTheDocument()
    expect(screen.queryByLabelText('Your teaching')).not.toBeInTheDocument()
  })

  it('shows a student their next classes, soonest first, and the latest announcements', async () => {
    vi.mocked(dashboardsApi.getStudentDashboard).mockResolvedValue(student)
    const today = (['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'] as const)[new Date().getDay()] ?? 'MONDAY'
    vi.mocked(registrationApi.getMyTimetable).mockResolvedValue({
      semester: { id: 1, code: '2026-S1', name: 'Semester 1' },
      status: 'APPROVED',
      entries: [
        { offeringId: 7, courseCode: 'CS101', courseTitle: 'Programming I', section: 'A', lecturer: null, dayOfWeek: today, startTime: '23:59:00', endTime: '23:59:59', venue: 'LT1' },
      ],
    })
    vi.mocked(notificationsApi.getAnnouncements).mockResolvedValue({
      content: [{ id: 5, offeringId: 7, courseCode: 'CS101', courseTitle: 'Programming I', section: 'A', semester: { id: 1, code: '2026-S1', name: 'Semester 1' }, title: 'Room change', body: 'We meet in LT2.', authorName: 'Dr. Grace Wanjiru', createdAt: new Date().toISOString() }],
      page: 0, size: 3, totalElements: 1, totalPages: 1,
    })
    renderPage(['STUDENT'])

    const classes = await screen.findByRole('list', { name: 'Upcoming classes' })
    expect(within(classes).getByText('Programming I')).toBeInTheDocument()
    expect(within(classes).getByText('Today')).toBeInTheDocument()
    const announcements = await screen.findByRole('list', { name: 'Latest announcements' })
    expect(within(announcements).getByText('Room change')).toBeInTheDocument()
    expect(within(announcements).getByRole('link', { name: /Read more/ })).toHaveAttribute('href', '/announcements?offeringId=7')
  })

  it('shows an error instead of an endless spinner', async () => {
    vi.mocked(dashboardsApi.getFinanceDashboard).mockRejectedValue(new Error('boom'))
    renderPage(['FINANCE'])

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
