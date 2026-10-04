import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as academic from '@/api/academic'
import { ApiError } from '@/api/errors'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { Department } from '@/types/api'
import { DepartmentsPage } from './DepartmentsPage'

vi.mock('@/api/academic')

const empty: Department = { id: 1, code: 'HIST', name: 'History', faculty: null, programCount: 0, courseCount: 0, lecturerCount: 0 }
const inUse: Department = { id: 2, code: 'CS', name: 'Computer Science', faculty: 'Science', programCount: 2, courseCount: 14, lecturerCount: 5 }

function renderPage() {
  return renderWithProviders([{ path: '/admin/departments', element: <DepartmentsPage /> }], {
    auth: makeAuth({ user: makeUser(['ADMIN']) }),
    initialPath: '/admin/departments',
  })
}

describe('DepartmentsPage', () => {
  beforeEach(() => {
    vi.mocked(academic.listDepartments).mockResolvedValue([inUse, empty])
  })

  it('lists departments with their counts', async () => {
    renderPage()
    const row = (await screen.findByText('Computer Science')).closest('tr')!
    expect(within(row).getByText('14')).toBeInTheDocument()
    expect(within(row).getByText('Science')).toBeInTheDocument()
  })

  it('only allows deleting empty departments, after confirmation', async () => {
    vi.mocked(academic.deleteDepartment).mockResolvedValue()
    renderPage()

    expect(await screen.findByRole('button', { name: 'Delete Computer Science' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Delete History' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Delete department?' })
    expect(academic.deleteDepartment).not.toHaveBeenCalled()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))
    expect(academic.deleteDepartment).toHaveBeenCalledWith(1)
    expect(await screen.findByText('History deleted.')).toBeInTheDocument()
  })

  it('validates the form before calling the API', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: /New department/ }))
    await userEvent.type(screen.getByLabelText('Code'), 'bad code')
    await userEvent.click(screen.getByRole('button', { name: 'Create department' }))

    expect(await screen.findByText('Letters, digits and hyphens only.')).toBeInTheDocument()
    expect(screen.getByText('Enter a name.')).toBeInTheDocument()
    expect(academic.createDepartment).not.toHaveBeenCalled()
  })

  it('shows a duplicate-code conflict from the server and keeps the dialog open', async () => {
    vi.mocked(academic.createDepartment).mockRejectedValue(
      new ApiError({ status: 409, code: 'DUPLICATE_RESOURCE', detail: "Department code 'CS' already exists." }),
    )
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: /New department/ }))
    await userEvent.type(screen.getByLabelText('Code'), 'CS')
    await userEvent.type(screen.getByLabelText('Name'), 'Computing')
    await userEvent.click(screen.getByRole('button', { name: 'Create department' }))

    expect(await screen.findByText("Department code 'CS' already exists.")).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'New department' })).toBeInTheDocument()
    expect(academic.createDepartment).toHaveBeenCalledWith({ code: 'CS', name: 'Computing', faculty: null })
  })
})
