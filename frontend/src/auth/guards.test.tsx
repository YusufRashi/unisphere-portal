import { screen } from '@testing-library/react'
import type { RouteObject } from 'react-router'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import { RequireAuth, RequireRole } from './guards'

const routes: RouteObject[] = [
  { path: '/login', element: <p>Login page</p> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/dashboard', element: <p>Dashboard</p> },
      {
        path: '/admin',
        element: <RequireRole roles={['ADMIN']} />,
        children: [{ path: 'users', element: <p>Admin users</p> }],
      },
    ],
  },
]

describe('route guards', () => {
  it('redirects anonymous visitors to the login page and remembers where they were going', async () => {
    const { router } = renderWithProviders(routes, { initialPath: '/admin/users' })

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(router.state.location.state).toEqual({ from: '/admin/users' })
  })

  it('shows a loading indicator while the session is being restored', () => {
    renderWithProviders(routes, { auth: makeAuth({ status: 'loading' }), initialPath: '/dashboard' })
    expect(screen.getByRole('status')).toHaveTextContent('Restoring your session')
  })

  it('lets an authenticated user into ordinary pages', async () => {
    renderWithProviders(routes, { auth: makeAuth({ user: makeUser(['STUDENT']) }), initialPath: '/dashboard' })
    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })

  it('blocks a student from an admin page even when the URL is typed directly', async () => {
    renderWithProviders(routes, { auth: makeAuth({ user: makeUser(['STUDENT']) }), initialPath: '/admin/users' })

    expect(await screen.findByRole('heading', { name: /don’t have access/i })).toBeInTheDocument()
    expect(screen.queryByText('Admin users')).not.toBeInTheDocument()
  })

  it('allows an admin into admin pages', async () => {
    renderWithProviders(routes, { auth: makeAuth({ user: makeUser(['ADMIN']) }), initialPath: '/admin/users' })
    expect(await screen.findByText('Admin users')).toBeInTheDocument()
  })
})
