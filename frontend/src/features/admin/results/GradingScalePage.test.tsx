import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '@/api/errors'
import * as resultsApi from '@/api/results'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import { GradingScalePage } from './GradingScalePage'

vi.mock('@/api/results')

const scale = {
  id: 1,
  name: 'Standard 4.0 scale',
  bands: [
    { letter: 'A', minMark: 70, gradePoints: 4, passing: true },
    { letter: 'C', minMark: 50, gradePoints: 2, passing: true },
    { letter: 'F', minMark: 0, gradePoints: 0, passing: false },
  ],
}

function renderPage() {
  return renderWithProviders([{ path: '/admin/grading-scale', element: <GradingScalePage /> }], {
    auth: makeAuth({ user: makeUser(['ADMIN']) }),
    initialPath: '/admin/grading-scale',
  })
}

describe('GradingScalePage', () => {
  beforeEach(() => vi.mocked(resultsApi.getGradingScale).mockResolvedValue(scale))

  it('shows the range each grade covers', async () => {
    renderPage()
    expect(await screen.findByText('70 – 100')).toBeInTheDocument()
    expect(screen.getByText('50 – below 70')).toBeInTheDocument()
    expect(screen.getByText('0 – below 50')).toBeInTheDocument()
  })

  it('saves after confirmation', async () => {
    vi.mocked(resultsApi.updateGradingScale).mockResolvedValue(scale)
    renderPage()
    const minA = await screen.findByRole('textbox', { name: 'Minimum mark for row 1' })
    await userEvent.clear(minA)
    await userEvent.type(minA, '75')
    await userEvent.click(screen.getByRole('button', { name: 'Save scale' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Save the grading scale?' })
    expect(dialog).toHaveTextContent('already finalized keep their grades')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save scale' }))
    expect(resultsApi.updateGradingScale).toHaveBeenCalledWith([
      { letter: 'A', minMark: 75, gradePoints: 4, passing: true },
      { letter: 'C', minMark: 50, gradePoints: 2, passing: true },
      { letter: 'F', minMark: 0, gradePoints: 0, passing: false },
    ])
  })

  it('blocks obviously invalid values before calling the server', async () => {
    renderPage()
    const points = await screen.findByRole('textbox', { name: 'Grade points for row 1' })
    await userEvent.clear(points)
    await userEvent.type(points, '9')
    await userEvent.click(screen.getByRole('button', { name: 'Save scale' }))
    expect(screen.getByText('0–5, up to 2 decimals')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('lists every problem the server finds with the scale', async () => {
    vi.mocked(resultsApi.updateGradingScale).mockRejectedValue(new ApiError({
      status: 400, code: 'VALIDATION_ERROR', detail: 'The grading scale is not valid.',
      errors: [
        { field: 'bands', message: 'At least one band must be a fail.' },
        { field: 'bands', message: 'The lowest band must start at 0 so every mark gets a grade (it starts at 10).' },
      ],
    }))
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Save scale' }))
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Save scale' }))
    expect(await screen.findByText('At least one band must be a fail.')).toBeInTheDocument()
    expect(screen.getByText(/lowest band must start at 0/)).toBeInTheDocument()
  })
})
