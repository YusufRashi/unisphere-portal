import { screen, within } from '@testing-library/react'
import * as resultsApi from '@/api/results'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { AcademicRecord, CourseGrade } from '@/types/api'
import { ResultsPage } from './ResultsPage'

vi.mock('@/api/results')

const student = { id: 4, studentNumber: 'S2026-00004', name: 'Amina Otieno', programCode: 'BSC-CS', programName: 'BSc Computer Science' }

function grade(id: number, code: string, letter: string, points: number, passed: boolean, counts = true): CourseGrade {
  return {
    resultId: id, courseId: id * 10, courseCode: code, courseTitle: `${code} title`, credits: 3, section: 'A', catMark: 20,
    examMark: 50, totalMark: 70, grade: letter, gradePoints: points, passed, countsTowardCgpa: counts,
  }
}

function renderPage() {
  return renderWithProviders([{ path: '/student/results', element: <ResultsPage /> }], {
    auth: makeAuth({ user: makeUser(['STUDENT']) }),
    initialPath: '/student/results',
  })
}

describe('ResultsPage', () => {
  it('shows each semester with its GPA and the cumulative GPA', async () => {
    const record: AcademicRecord = {
      student,
      semesters: [
        { semester: { id: 2, code: '2026-S2', name: 'Semester 2' }, courses: [grade(3, 'CS101', 'B', 3, true)], gpa: 3, creditsAttempted: 3, creditsEarned: 3 },
        {
          semester: { id: 1, code: '2026-S1', name: 'Semester 1' },
          courses: [grade(1, 'CS101', 'F', 0, false, false), grade(2, 'MATH110', 'A', 4, true)],
          gpa: 2, creditsAttempted: 6, creditsEarned: 3,
        },
      ],
      cgpa: 3.5, creditsAttempted: 6, creditsEarned: 6,
    }
    vi.mocked(resultsApi.getMyResults).mockResolvedValue(record)
    renderPage()

    expect(await screen.findByText('3.50')).toBeInTheDocument()
    const first = screen.getByRole('table', { name: 'Results for Semester 1' })
    expect(within(first).getByText('Fail')).toBeInTheDocument()
    expect(within(first).getByText('Repeated later — not counted in CGPA')).toBeInTheDocument()
    expect(screen.getByText('2.00')).toBeInTheDocument()
    expect(screen.getByText('3 of 6 credits earned')).toBeInTheDocument()
  })

  it('explains that results appear after finalization', async () => {
    vi.mocked(resultsApi.getMyResults).mockResolvedValue({ student, semesters: [], cgpa: null, creditsAttempted: 0, creditsEarned: 0 })
    renderPage()
    expect(await screen.findByText('No results yet')).toBeInTheDocument()
  })
})
