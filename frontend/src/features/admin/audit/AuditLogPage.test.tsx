import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as auditApi from '@/api/audit'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { AuditEntry, PageResponse } from '@/types/api'
import { AuditLogPage } from './AuditLogPage'

vi.mock('@/api/audit')

const approved: AuditEntry = {
  id: 2,
  createdAt: '2026-11-05T09:00:00Z',
  actor: { id: 1, name: 'Rose Mutiso', email: 'registrar@uni.test' },
  action: 'REGISTRATION_APPROVED',
  entityType: 'Registration',
  entityId: '42',
  details: { student: 'S2026-00001', credits: 12 },
  ipAddress: '10.0.0.5',
}

const system: AuditEntry = { id: 1, createdAt: '2026-11-05T06:00:00Z', actor: null, action: 'USER_CREATED', entityType: null, entityId: null, details: {}, ipAddress: null }

function page(content: AuditEntry[]): PageResponse<AuditEntry> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 }
}

function renderPage(path = '/admin/audit') {
  return renderWithProviders([{ path: '/admin/audit', element: <AuditLogPage /> }], {
    auth: makeAuth({ user: makeUser(['ADMIN']) }),
    initialPath: path,
  })
}

beforeEach(() => {
  vi.mocked(auditApi.getAuditFilters).mockResolvedValue({ actions: ['REGISTRATION_APPROVED', 'USER_CREATED'], entityTypes: ['Registration', 'Semester'] })
})

describe('AuditLogPage', () => {
  it('shows who did what, with details, and system actions without a person', async () => {
    vi.mocked(auditApi.searchAuditLogs).mockResolvedValue(page([approved, system]))
    renderPage()

    const row = (await screen.findByText('Rose Mutiso')).closest('tr') as HTMLElement
    expect(within(row).getByText('Registration approved')).toBeInTheDocument()
    expect(within(row).getByText('Registration #42')).toBeInTheDocument()
    expect(within(row).getByText(/S2026-00001/)).toBeInTheDocument()
    expect(screen.getByText('System')).toBeInTheDocument()
  })

  it('filters by action and date, keeping them in the URL', async () => {
    const user = userEvent.setup()
    vi.mocked(auditApi.searchAuditLogs).mockResolvedValue(page([approved]))
    const { router } = renderPage()

    await screen.findByText('Rose Mutiso')
    await screen.findByRole('option', { name: 'Registration approved' })
    await user.selectOptions(screen.getByLabelText('Action'), 'REGISTRATION_APPROVED')

    expect(router.state.location.search).toContain('action=REGISTRATION_APPROVED')
    expect(auditApi.searchAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ action: 'REGISTRATION_APPROVED', page: 0 }))

    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(router.state.location.search).toBe('')
  })

  it('reads its filters from a shared link', async () => {
    vi.mocked(auditApi.searchAuditLogs).mockResolvedValue(page([]))
    renderPage('/admin/audit?from=2026-11-01&to=2026-11-05&entityType=Semester')

    expect(await screen.findByText('No entries match your filters')).toBeInTheDocument()
    expect(auditApi.searchAuditLogs).toHaveBeenCalledWith(expect.objectContaining({ from: '2026-11-01', to: '2026-11-05', entityType: 'Semester' }))
  })
})
