import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmDialog } from './ConfirmDialog'

function renderDialog(overrides: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
  const props = {
    open: true,
    title: 'Deactivate account?',
    description: 'The user will be signed out.',
    confirmLabel: 'Deactivate',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
    ...overrides,
  }
  render(<ConfirmDialog {...props} />)
  return props
}

describe('ConfirmDialog', () => {
  it('renders nothing when closed', () => {
    renderDialog({ open: false })
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('is labelled by its title and description and focuses the safe action', () => {
    renderDialog()
    const dialog = screen.getByRole('alertdialog', { name: 'Deactivate account?' })
    expect(dialog).toHaveAccessibleDescription('The user will be signed out.')
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('confirms only when the confirm button is pressed', async () => {
    const props = renderDialog()
    await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }))
    expect(props.onConfirm).toHaveBeenCalledOnce()
    expect(props.onCancel).not.toHaveBeenCalled()
  })

  it('cancels on Escape', async () => {
    const props = renderDialog()
    await userEvent.keyboard('{Escape}')
    expect(props.onCancel).toHaveBeenCalledOnce()
  })

  it('keeps keyboard focus inside the dialog', async () => {
    renderDialog()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Deactivate' })).toHaveFocus()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('disables both actions while the operation is running', () => {
    renderDialog({ loading: true })
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeDisabled()
  })
})
