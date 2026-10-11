import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as feesApi from '@/api/fees'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { FeeAccount, FeePayment } from '@/types/api'
import { FeesPage } from './FeesPage'

vi.mock('@/api/fees')

function account(balance: number, payments: FeePayment[] = []): FeeAccount {
  return {
    student: { id: 4, studentNumber: 'S2026-00004', name: 'Amina Otieno', email: 'amina@uni.test', programCode: 'BSC-CS', programName: 'BSc Computer Science' },
    currency: 'KES',
    summary: { totalCharged: 85000, totalPaid: 85000 - balance, balance },
    charges: [
      {
        id: 1, kind: 'TUITION', description: 'Tuition — Semester 1', semester: { id: 1, code: '2026-S1', name: 'Semester 1' },
        amount: 85000, status: 'ACTIVE', createdAt: '2026-09-01T08:00:00Z', voidedAt: null, voidReason: null,
      },
    ],
    payments,
  }
}

function payment(status: FeePayment['status'], amount: number): FeePayment {
  return {
    id: 9, amount, method: 'MOBILE_MONEY', channel: 'ONLINE', reference: 'ONL-20261011-AB12', status,
    failureReason: status === 'FAILED' ? 'The payment was declined by the provider. No money was taken.' : null,
    paidAt: '2026-10-11T09:00:00Z', note: null, reversedAt: null, reversalReason: null,
  }
}

function renderPage() {
  return renderWithProviders([{ path: '/student/fees', element: <FeesPage /> }], {
    auth: makeAuth({ user: makeUser(['STUDENT']) }),
    initialPath: '/student/fees',
  })
}

describe('FeesPage', () => {
  it('shows the balance and statement', async () => {
    vi.mocked(feesApi.getMyFees).mockResolvedValue(account(85000))
    renderPage()

    expect(await screen.findByText('Balance due')).toBeInTheDocument()
    const charges = screen.getByRole('table', { name: 'Charges' })
    expect(within(charges).getByText('Tuition — Semester 1')).toBeInTheDocument()
    expect(screen.getByText('No payments yet')).toBeInTheDocument()
  })

  it('pays the balance online and closes on success', async () => {
    const user = userEvent.setup()
    vi.mocked(feesApi.getMyFees).mockResolvedValue(account(85000))
    vi.mocked(feesApi.payOnline).mockResolvedValue({ payment: payment('COMPLETED', 85000), account: account(0, [payment('COMPLETED', 85000)]) })
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Pay online' }))
    const dialog = screen.getByRole('dialog', { name: 'Pay online' })
    expect(within(dialog).getByLabelText('Amount (KES)')).toHaveValue('85000.00')
    await user.click(within(dialog).getByRole('button', { name: 'Pay now' }))

    expect(vi.mocked(feesApi.payOnline).mock.calls[0]?.[0]).toMatchObject({ amount: '85000.00', method: 'MOBILE_MONEY' })
    expect(await screen.findByText('Settled')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pay online' })).not.toBeInTheDocument()
  })

  it('keeps the dialog open with the reason when the payment is declined, and retries with a new key', async () => {
    const user = userEvent.setup()
    vi.mocked(feesApi.getMyFees).mockResolvedValue(account(85000))
    vi.mocked(feesApi.payOnline).mockResolvedValue({ payment: payment('FAILED', 100.13), account: account(85000, [payment('FAILED', 100.13)]) })
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Pay online' }))
    const dialog = screen.getByRole('dialog', { name: 'Pay online' })
    const amount = within(dialog).getByLabelText('Amount (KES)')
    await user.clear(amount)
    await user.type(amount, '100.13')
    await user.click(within(dialog).getByRole('button', { name: 'Pay now' }))

    expect(await within(dialog).findByText('Payment declined')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Try again' }))
    const [first, second] = vi.mocked(feesApi.payOnline).mock.calls.map((c) => c[0].idempotencyKey)
    expect(first).toBeDefined()
    expect(second).not.toBe(first)
  })

  it('does not send more than the balance', async () => {
    const user = userEvent.setup()
    vi.mocked(feesApi.getMyFees).mockResolvedValue(account(500))
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Pay online' }))
    const dialog = screen.getByRole('dialog', { name: 'Pay online' })
    const amount = within(dialog).getByLabelText('Amount (KES)')
    await user.clear(amount)
    await user.type(amount, '600')
    await user.click(within(dialog).getByRole('button', { name: 'Pay now' }))

    expect(within(dialog).getByText('At most 500.00.')).toBeInTheDocument()
    expect(feesApi.payOnline).not.toHaveBeenCalled()
  })
})
