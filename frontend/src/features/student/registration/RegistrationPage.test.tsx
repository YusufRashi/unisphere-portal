import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as academic from '@/api/academic'
import { ApiError } from '@/api/errors'
import * as registrationApi from '@/api/registration'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { CatalogueEntry, Offering, RegistrationView } from '@/types/api'
import { RegistrationPage } from './RegistrationPage'

vi.mock('@/api/registration')
vi.mock('@/api/academic')

const semester: RegistrationView['semester'] = {
  id: 1, code: '2026-S1', name: 'Semester 1, 2026/2027', registrationOpensAt: '2026-09-01T06:00:00Z',
  registrationClosesAt: '2026-09-15T21:00:00Z', addDropDeadline: '2026-09-22T21:00:00Z', registrationOpen: true,
  addDropOpen: true, minCredits: 6, maxCredits: 12, requiresApproval: false,
}

function offering(id: number, code: string, credits: number): Offering {
  return {
    id, course: { id: id * 10, code, title: `${code} title`, credits, level: 100, departmentId: 1, departmentCode: 'CS' },
    semester: { id: 1, code: '2026-S1', name: semester.name }, section: 'A',
    lecturer: { id: 1, displayName: 'Dr. Ada Lovelace', staffNumber: 'STF-00001' }, capacity: 40, enrolledCount: 3,
    seatsLeft: 37, catWeight: 30, examWeight: 70, examStartsAt: null, examVenue: null, resultsStatus: 'DRAFT',
    sessions: [{ dayOfWeek: 'MONDAY', startTime: '08:00:00', endTime: '10:00:00', venue: 'LT-1' }],
  }
}

const cs101 = offering(1, 'CS101', 3)
const cs201 = offering(2, 'CS201', 4)
const catalogue: CatalogueEntry[] = [
  { offering: cs101, prerequisites: [], availability: 'AVAILABLE', reason: null },
  {
    offering: cs201,
    prerequisites: [{ courseId: 10, code: 'CS101', met: false }],
    availability: 'PREREQUISITES_MISSING',
    reason: 'CS201 requires CS101, which you have not passed yet.',
  },
]

const emptyView: RegistrationView = {
  id: null, semester, status: null, items: [], droppedItems: [], totalCredits: 0, submittedAt: null, reviewedAt: null,
  rejectionReason: null, permissions: { canAdd: true, canRemove: false, canSubmit: false, removeMeansDrop: false, lockedReason: null },
}

function item(o: Offering, itemId: number) {
  return {
    id: itemId, offeringId: o.id, courseId: o.course.id, courseCode: o.course.code, courseTitle: o.course.title,
    credits: o.course.credits, section: 'A', lecturer: 'Dr. Ada Lovelace', sessions: o.sessions, seatReserved: false,
    addedAt: '2026-09-02T08:00:00Z', droppedAt: null,
  }
}

function renderPage() {
  return renderWithProviders([{ path: '/student/registration', element: <RegistrationPage /> }], {
    auth: makeAuth({ user: makeUser(['STUDENT']) }),
    initialPath: '/student/registration',
  })
}

describe('RegistrationPage', () => {
  beforeEach(() => {
    vi.mocked(academic.listDepartments).mockResolvedValue([])
    vi.mocked(registrationApi.getCatalogue).mockResolvedValue({ content: catalogue, page: 0, size: 10, totalElements: 2, totalPages: 1 })
  })

  it('offers available courses and explains why others cannot be added', async () => {
    vi.mocked(registrationApi.getMyRegistration).mockResolvedValue(emptyView)
    renderPage()

    expect(await screen.findByRole('button', { name: 'Add CS101 section A' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: /Add CS201/ })).not.toBeInTheDocument()
    expect(screen.getByText('Prerequisites missing')).toBeInTheDocument()
    expect(screen.getByText('CS201 requires CS101, which you have not passed yet.')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Prerequisites for CS201' })).getByText('(not passed)')).toBeInTheDocument()
  })

  it('adds a course and updates the registration panel', async () => {
    vi.mocked(registrationApi.getMyRegistration).mockResolvedValue(emptyView)
    vi.mocked(registrationApi.addCourse).mockResolvedValue({
      ...emptyView, id: 5, status: 'DRAFT', items: [item(cs101, 50)], totalCredits: 3,
      permissions: { canAdd: true, canRemove: true, canSubmit: true, removeMeansDrop: false, lockedReason: null },
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Add CS101 section A' }))

    expect(registrationApi.addCourse).toHaveBeenCalledWith(1)
    const mine = await screen.findByRole('list', { name: 'Courses in my registration' })
    expect(within(mine).getByText('CS101 title')).toBeInTheDocument()
    expect(screen.getByText('3 more credits needed (minimum 6)')).toBeInTheDocument()
    expect(screen.getByText('CS101 added to your registration.')).toBeInTheDocument()
  })

  it('shows a rule violation from the server', async () => {
    vi.mocked(registrationApi.getMyRegistration).mockResolvedValue(emptyView)
    vi.mocked(registrationApi.addCourse).mockRejectedValue(
      new ApiError({ status: 409, code: 'COURSE_FULL', detail: 'CS101 (A) is full. Choose another section or course.' }),
    )
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add CS101 section A' }))
    expect(await screen.findByText('CS101 (A) is full. Choose another section or course.')).toBeInTheDocument()
  })

  it('asks for confirmation before submitting', async () => {
    const draft: RegistrationView = {
      ...emptyView, id: 5, status: 'DRAFT', items: [item(cs101, 50), item(cs201, 51)], totalCredits: 7,
      permissions: { canAdd: true, canRemove: true, canSubmit: true, removeMeansDrop: false, lockedReason: null },
    }
    vi.mocked(registrationApi.getMyRegistration).mockResolvedValue(draft)
    vi.mocked(registrationApi.submitRegistration).mockResolvedValue({ ...draft, status: 'APPROVED' })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Submit registration' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Submit your registration?' })
    expect(dialog).toHaveTextContent('2 courses (7 credits)')
    expect(registrationApi.submitRegistration).not.toHaveBeenCalled()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit registration' }))
    expect(registrationApi.submitRegistration).toHaveBeenCalled()
    expect(await screen.findByText('You are registered for this semester.')).toBeInTheDocument()
  })

  it('confirms before dropping a course after registration', async () => {
    vi.mocked(registrationApi.getMyRegistration).mockResolvedValue({
      ...emptyView, id: 5, status: 'APPROVED', items: [item(cs101, 50), item(cs201, 51)], totalCredits: 7,
      permissions: { canAdd: true, canRemove: true, canSubmit: false, removeMeansDrop: true, lockedReason: null },
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Drop CS201' }))
    expect(screen.getByRole('alertdialog', { name: 'Drop CS201?' })).toHaveTextContent('will be released')
    expect(registrationApi.removeCourse).not.toHaveBeenCalled()
    expect(screen.getByText(/You can still add or drop courses until/)).toBeInTheDocument()
  })

  it('explains when registration is not open', async () => {
    vi.mocked(registrationApi.getMyRegistration).mockRejectedValue(
      new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'No semester is currently open for registration.' }),
    )
    renderPage()
    expect(await screen.findByText('Registration is not open')).toBeInTheDocument()
    expect(screen.getByText('No semester is currently open for registration.')).toBeInTheDocument()
  })
})
