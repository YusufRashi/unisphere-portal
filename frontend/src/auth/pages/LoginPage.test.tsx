import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '@/api/errors'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import { LoginPage } from './LoginPage'

function renderLogin(login = vi.fn(), state?: { from: string }) {
  const auth = makeAuth({ login })
  const result = renderWithProviders(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/dashboard', element: <p>Dashboard page</p> },
      { path: '/admin/users', element: <p>Users page</p> },
    ],
    { auth, initialPath: '/login' },
  )
  if (state) result.router.navigate('/login', { state, replace: true })
  return result
}

describe('LoginPage', () => {
  it('shows field errors and does not call the API when the form is empty', async () => {
    const login = vi.fn()
    renderLogin(login)

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your email address.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email address')).toHaveAttribute('aria-invalid', 'true')
    expect(login).not.toHaveBeenCalled()
  })

  it('rejects a malformed email address', async () => {
    const login = vi.fn()
    renderLogin(login)

    await userEvent.type(screen.getByLabelText('Email address'), 'not-an-email')
    await userEvent.type(screen.getByLabelText('Password'), 'whatever')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('signs in with trimmed credentials and goes to the dashboard', async () => {
    const login = vi.fn().mockResolvedValue(makeUser(['STUDENT']))
    renderLogin(login)

    await userEvent.type(screen.getByLabelText('Email address'), '  amina.otieno@uni.test ')
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-42')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
    expect(login).toHaveBeenCalledWith('amina.otieno@uni.test', 'Correct-Horse-42')
  })

  it('returns the user to the page they originally requested', async () => {
    const login = vi.fn().mockResolvedValue(makeUser(['ADMIN']))
    renderLogin(login, { from: '/admin/users' })

    await userEvent.type(screen.getByLabelText('Email address'), 'admin@uni.test')
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-42')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Users page')).toBeInTheDocument()
  })

  it('ignores redirect targets outside the application', async () => {
    const login = vi.fn().mockResolvedValue(makeUser(['STUDENT']))
    renderLogin(login, { from: '//evil.example.com' })

    await userEvent.type(screen.getByLabelText('Email address'), 'amina.otieno@uni.test')
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-42')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
  })

  it('shows the server error message when sign-in fails', async () => {
    const login = vi.fn().mockRejectedValue(
      new ApiError({ status: 401, code: 'INVALID_CREDENTIALS', detail: 'Invalid email or password.' }),
    )
    renderLogin(login)

    await userEvent.type(screen.getByLabelText('Email address'), 'amina.otieno@uni.test')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
  })
})
