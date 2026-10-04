import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '@/api/errors'
import * as resultsApi from '@/api/results'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { MarkRow, MarksSheet } from '@/types/api'
import { MarksSheetPage } from './MarksSheetPage'

vi.mock('@/api/results')

function row(id: number, name: string, cat: number | null, exam: number | null): MarkRow {
  const total = cat !== null && exam !== null ? cat + exam : null
  return {
    registrationItemId: id, studentId: id * 10, studentNumber: `S2026-0000${id}`, name, catMark: cat, examMark: exam,
    totalMark: total, grade: null, gradePoints: null, passed: null,
  }
}

function sheet(overrides: Partial<MarksSheet> = {}): MarksSheet {
  return {
    offering: {
      id: 7, courseCode: 'CS101', courseTitle: 'Introduction to Programming', credits: 3, section: 'A',
      semester: { id: 1, code: '2026-S1', name: 'Semester 1, 2026/2027' }, lecturer: 'Dr. Ada Lovelace', catWeight: 30,
      examWeight: 70, resultsStatus: 'DRAFT', submittedAt: null, finalizedAt: null, returnComment: null,
    },
    rows: [row(1, 'Amina Otieno', null, null), row(2, 'Brian Kamau', 20, null)],
    classSize: 2,
    complete: 0,
    permissions: { canEdit: true, canSubmit: false, lockedReason: null },
    ...overrides,
  }
}

function renderPage(mode: 'lecturer' | 'registrar' = 'lecturer') {
  const path = mode === 'lecturer' ? '/lecturer/courses/:offeringId' : '/admin/results/:offeringId'
  return renderWithProviders([{ path, element: <MarksSheetPage mode={mode} /> }], {
    auth: makeAuth({ user: makeUser([mode === 'lecturer' ? 'LECTURER' : 'ADMIN']) }),
    initialPath: mode === 'lecturer' ? '/lecturer/courses/7' : '/admin/results/7',
  })
}

describe('MarksSheetPage', () => {
  beforeEach(() => {
    vi.mocked(resultsApi.getGradingScale).mockResolvedValue({
      id: 1, name: 'Standard', bands: [
        { letter: 'A', minMark: 70, gradePoints: 4, passing: true },
        { letter: 'C', minMark: 50, gradePoints: 2, passing: true },
        { letter: 'F', minMark: 0, gradePoints: 0, passing: false },
      ],
    })
    vi.mocked(resultsApi.getClassStatistics).mockResolvedValue({
      classSize: 2, graded: 0, mean: null, median: null, highest: null, lowest: null, passRate: null, distribution: [],
    })
  })

  it('previews totals and grades while typing and saves only changed students', async () => {
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(sheet())
    vi.mocked(resultsApi.saveMarks).mockResolvedValue(sheet({ complete: 1 }))
    renderPage()

    const cat = await screen.findByRole('textbox', { name: 'CAT mark for Amina Otieno' })
    await userEvent.type(cat, '25')
    await userEvent.type(screen.getByRole('textbox', { name: 'Exam mark for Amina Otieno' }), '60')
    const amina = screen.getByText('Amina Otieno').closest('tr')!
    expect(within(amina).getByText('85')).toBeInTheDocument()
    expect(within(amina).getByText('A')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }))
    expect(resultsApi.saveMarks).toHaveBeenCalledWith(7, [{ registrationItemId: 1, catMark: 25, examMark: 60 }])
    expect(await screen.findByText('Marks saved for 1 student.')).toBeInTheDocument()
  })

  it('flags marks above the component weight and does not send them', async () => {
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(sheet())
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'CAT mark for Amina Otieno' }), '31')
    expect(screen.getByText('At most 30')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'CAT mark for Amina Otieno' })).toHaveAttribute('aria-invalid', 'true')
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }))
    expect(resultsApi.saveMarks).not.toHaveBeenCalled()
  })

  it('shows server field errors next to the right cell', async () => {
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(sheet())
    vi.mocked(resultsApi.saveMarks).mockRejectedValue(new ApiError({
      status: 400, code: 'VALIDATION_ERROR', detail: 'Some marks are not valid.',
      errors: [{ field: 'entries[0].examMark', message: 'must be at most 70' }],
    }))
    renderPage()

    await userEvent.type(await screen.findByRole('textbox', { name: 'Exam mark for Brian Kamau' }), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }))
    const brian = screen.getByText('Brian Kamau').closest('tr')!
    expect(await within(brian).findByText('must be at most 70')).toBeInTheDocument()
  })

  it('explains why marks cannot be submitted yet', async () => {
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(sheet())
    renderPage()
    expect(await screen.findByRole('button', { name: 'Submit to registrar' })).toBeDisabled()
    expect(screen.getByText('2 of 2 students still need both marks.')).toBeInTheDocument()
  })

  it('confirms before submitting complete marks', async () => {
    const complete = sheet({
      rows: [row(1, 'Amina Otieno', 25, 60), row(2, 'Brian Kamau', 20, 30)],
      complete: 2,
      permissions: { canEdit: true, canSubmit: true, lockedReason: null },
    })
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(complete)
    vi.mocked(resultsApi.submitMarks).mockResolvedValue({
      ...complete, offering: { ...complete.offering, resultsStatus: 'SUBMITTED' },
      permissions: { canEdit: false, canSubmit: false, lockedReason: 'Marks have been submitted and are awaiting the registrar.' },
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Submit to registrar' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Submit marks for CS101 (A)?' })
    expect(resultsApi.submitMarks).not.toHaveBeenCalled()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit marks' }))
    expect(resultsApi.submitMarks).toHaveBeenCalledWith(7)
    expect(await screen.findByText('Marks for CS101 (A) submitted to the registrar.')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /CAT mark/ })).not.toBeInTheDocument()
  })

  it('shows the registrar’s return comment to the lecturer', async () => {
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(sheet({
      offering: { ...sheet().offering, returnComment: 'Re-check Brian’s exam script.' },
    }))
    renderPage()
    expect(await screen.findByText('Re-check Brian’s exam script.')).toBeInTheDocument()
  })

  it('lets the registrar return submitted marks with a comment', async () => {
    const submitted = sheet({
      offering: { ...sheet().offering, resultsStatus: 'SUBMITTED', submittedAt: '2026-12-10T10:00:00Z' },
      rows: [row(1, 'Amina Otieno', 25, 60), row(2, 'Brian Kamau', 20, 30)],
      complete: 2,
      permissions: { canEdit: false, canSubmit: false, lockedReason: 'Marks have been submitted and are awaiting the registrar.' },
    })
    vi.mocked(resultsApi.getMarksSheet).mockResolvedValue(submitted)
    vi.mocked(resultsApi.returnResults).mockResolvedValue({ ...submitted, offering: { ...submitted.offering, resultsStatus: 'DRAFT' } })
    renderPage('registrar')

    expect(await screen.findByRole('button', { name: 'Finalize results' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Save draft' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Return to lecturer' }))
    const dialog = screen.getByRole('dialog', { name: 'Return marks for CS101 (A)' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Return marks' }))
    expect(within(dialog).getByText('Tell the lecturer what needs correcting.')).toBeInTheDocument()
    expect(resultsApi.returnResults).not.toHaveBeenCalled()

    await userEvent.type(within(dialog).getByLabelText('Comment for the lecturer'), 'Two CAT marks look swapped.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Return marks' }))
    expect(resultsApi.returnResults).toHaveBeenCalledWith(7, 'Two CAT marks look swapped.')
  })
})
