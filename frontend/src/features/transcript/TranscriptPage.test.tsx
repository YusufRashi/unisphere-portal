import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '@/api/errors'
import * as transcriptsApi from '@/api/transcripts'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { Transcript } from '@/types/api'
import { TranscriptPage } from './TranscriptPage'
import { VerifyTranscriptPage } from './VerifyTranscriptPage'

vi.mock('@/api/transcripts')

const transcript: Transcript = {
  universityName: 'UniSphere University',
  title: 'Unofficial Academic Transcript',
  student: {
    id: 4, studentNumber: 'S2026-00004', name: 'Amina Otieno', programCode: 'BSC-CS', programName: 'BSc Computer Science',
    awardLevel: 'BACHELOR', departmentName: 'Computer Science', admissionDate: '2026-09-01', yearOfStudy: 1, status: 'ACTIVE',
  },
  semesters: [
    {
      semester: { id: 1, code: '2026-S1', name: 'Semester 1, 2026/2027' },
      courses: [
        { resultId: 1, courseId: 10, courseCode: 'CS101', courseTitle: 'Introduction to Programming', credits: 3, section: 'A',
          catMark: 24, examMark: 58, totalMark: 82, grade: 'A', gradePoints: 4, passed: true, countsTowardCgpa: true },
        { resultId: 2, courseId: 11, courseCode: 'MATH110', courseTitle: 'Calculus I', credits: 3, section: 'A',
          catMark: 10, examMark: 20, totalMark: 30, grade: 'F', gradePoints: 0, passed: false, countsTowardCgpa: false },
      ],
      gpa: 2, creditsAttempted: 6, creditsEarned: 3,
    },
  ],
  cgpa: 4, creditsAttempted: 3, creditsEarned: 3,
  generatedAt: '2026-10-04T16:10:00Z',
  reference: '1A2B-3C4D-5E6F-7A8B',
  watermark: 'UNOFFICIAL — NOT VALID WITHOUT REGISTRAR CERTIFICATION',
}

describe('TranscriptPage', () => {
  function renderStudent() {
    return renderWithProviders([{ path: '/student/transcript', element: <TranscriptPage mode="student" /> }], {
      auth: makeAuth({ user: makeUser(['STUDENT']) }),
      initialPath: '/student/transcript',
    })
  }

  it('shows the transcript with the watermark, reference and GPA', async () => {
    vi.mocked(transcriptsApi.getMyTranscript).mockResolvedValue(transcript)
    renderStudent()

    const doc = await screen.findByRole('article', { name: 'Unofficial Academic Transcript for Amina Otieno' })
    expect(within(doc).getByRole('heading', { name: 'UniSphere University' })).toBeInTheDocument()
    expect(within(doc).getByText("BSC-CS — BSc Computer Science (Bachelor's)")).toBeInTheDocument()
    expect(within(doc).getByText('Semester GPA 2.00')).toBeInTheDocument()
    expect(within(doc).getByText('Cumulative GPA').nextElementSibling).toHaveTextContent('4.00')
    expect(within(doc).getByText('1A2B-3C4D-5E6F-7A8B')).toBeInTheDocument()
    expect(within(doc).getAllByText('UNOFFICIAL — NOT VALID WITHOUT REGISTRAR CERTIFICATION').length).toBeGreaterThan(0)
    expect(within(doc).getByText(/excluded from the CGPA/)).toBeInTheDocument()
  })

  it('downloads the PDF and prints on request', async () => {
    vi.mocked(transcriptsApi.getMyTranscript).mockResolvedValue(transcript)
    vi.mocked(transcriptsApi.downloadTranscriptPdf).mockResolvedValue()
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    renderStudent()

    await userEvent.click(await screen.findByRole('button', { name: 'Download PDF' }))
    expect(transcriptsApi.downloadTranscriptPdf).toHaveBeenCalledWith(undefined)
    await userEvent.click(screen.getByRole('button', { name: 'Print' }))
    expect(print).toHaveBeenCalled()
    print.mockRestore()
  })

  it('loads any student’s transcript for the registrar', async () => {
    vi.mocked(transcriptsApi.getStudentTranscript).mockResolvedValue(transcript)
    vi.mocked(transcriptsApi.downloadTranscriptPdf).mockResolvedValue()
    renderWithProviders([{ path: '/admin/students/:studentId/transcript', element: <TranscriptPage mode="registrar" /> }], {
      auth: makeAuth({ user: makeUser(['ADMIN']) }),
      initialPath: '/admin/students/4/transcript',
    })
    await userEvent.click(await screen.findByRole('button', { name: 'Download PDF' }))
    expect(transcriptsApi.getStudentTranscript).toHaveBeenCalledWith(4)
    expect(transcriptsApi.downloadTranscriptPdf).toHaveBeenCalledWith(4)
  })
})

describe('VerifyTranscriptPage', () => {
  function renderPage() {
    return renderWithProviders([{ path: '/admin/transcripts/verify', element: <VerifyTranscriptPage /> }], {
      auth: makeAuth({ user: makeUser(['ADMIN']) }),
      initialPath: '/admin/transcripts/verify',
    })
  }

  it('checks the reference format before asking the server', async () => {
    renderPage()
    await userEvent.type(screen.getByLabelText('Verification reference'), '12345')
    await userEvent.click(screen.getByRole('button', { name: 'Check reference' }))
    expect(screen.getByText(/Enter the reference as printed/)).toBeInTheDocument()
    expect(transcriptsApi.verifyTranscript).not.toHaveBeenCalled()
  })

  it('reports a superseded transcript', async () => {
    vi.mocked(transcriptsApi.verifyTranscript).mockResolvedValue({
      reference: '1A2B-3C4D-5E6F-7A8B', studentNumber: 'S2026-00004', studentName: 'Amina Otieno', cgpa: 3.5, creditsEarned: 6,
      resultsCount: 2, firstIssuedAt: '2026-10-01T10:00:00Z', lastIssuedAt: '2026-10-02T10:00:00Z', issueCount: 3, current: false,
    })
    renderPage()
    await userEvent.type(screen.getByLabelText('Verification reference'), '1a2b-3c4d-5e6f-7a8b')
    await userEvent.click(screen.getByRole('button', { name: 'Check reference' }))
    expect(transcriptsApi.verifyTranscript).toHaveBeenCalledWith('1A2B-3C4D-5E6F-7A8B')
    expect(await screen.findByText('Superseded')).toBeInTheDocument()
    expect(screen.getByText('3.50')).toBeInTheDocument()
  })

  it('warns when a reference was never issued', async () => {
    vi.mocked(transcriptsApi.verifyTranscript).mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'not found' }))
    renderPage()
    await userEvent.type(screen.getByLabelText('Verification reference'), 'FFFF-FFFF-FFFF-FFFF')
    await userEvent.click(screen.getByRole('button', { name: 'Check reference' }))
    expect(await screen.findByText('Not issued by this system')).toBeInTheDocument()
  })
})
