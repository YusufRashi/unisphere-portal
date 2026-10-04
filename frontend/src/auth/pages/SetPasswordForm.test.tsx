import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '@/api/errors'
import { SetPasswordForm } from './SetPasswordForm'

describe('SetPasswordForm', () => {
  it('ticks off requirements as the user types', async () => {
    render(<SetPasswordForm submitLabel="Activate account" onSubmit={vi.fn()} />)
    const requirements = screen.getByRole('list', { name: 'Password requirements' })
    expect(requirements).toHaveTextContent('At least 10 characters(not met)')

    await userEvent.type(screen.getByLabelText('New password'), 'Correct-Horse-42')

    expect(requirements).toHaveTextContent('At least 10 characters(met)')
    expect(requirements).toHaveTextContent('Contains a number(met)')
  })

  it('requires the confirmation to match', async () => {
    const onSubmit = vi.fn()
    render(<SetPasswordForm submitLabel="Activate account" onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('New password'), 'Correct-Horse-42')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Correct-Horse-43')
    await userEvent.click(screen.getByRole('button', { name: 'Activate account' }))

    expect(await screen.findByText('Passwords do not match.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits a valid password', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<SetPasswordForm submitLabel="Activate account" onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('New password'), 'Correct-Horse-42')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Correct-Horse-42')
    await userEvent.click(screen.getByRole('button', { name: 'Activate account' }))

    expect(onSubmit).toHaveBeenCalledWith('Correct-Horse-42')
  })

  it('lists policy violations reported by the server', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({
        status: 400,
        code: 'WEAK_PASSWORD',
        detail: 'Password does not meet the policy',
        violations: ['Password must not contain your email address.'],
      }),
    )
    render(<SetPasswordForm submitLabel="Activate account" onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('New password'), 'Amina-Otieno-1')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Amina-Otieno-1')
    await userEvent.click(screen.getByRole('button', { name: 'Activate account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Password must not contain your email address.')
  })
})
