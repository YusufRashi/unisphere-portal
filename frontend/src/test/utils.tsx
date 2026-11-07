import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { ThemeProvider } from '@/app/ThemeProvider'
import { AuthContext, type AuthContextValue } from '@/auth/authContext'
import { ToastProvider } from '@/components/ui/Toast'
import type { CurrentUser, Role } from '@/types/api'

export function makeUser(roles: Role[], overrides: Partial<CurrentUser> = {}): CurrentUser {
  return { id: 1, email: 'amina.otieno@uni.test', firstName: 'Amina', lastName: 'Otieno', roles, ...overrides }
}

export function makeAuth(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  const user = overrides.user ?? null
  return {
    status: user ? 'authenticated' : 'anonymous',
    user,
    login: vi.fn(),
    logout: vi.fn(),
    setSessionUser: vi.fn(),
    hasRole: (...roles: Role[]) => !!user && roles.some((r) => user.roles.includes(r)),
    ...overrides,
  }
}

/** Renders routes with an in-memory router, a fresh query client and a stubbed auth context. */
export function renderWithProviders(
  routes: RouteObject[],
  { auth = makeAuth(), initialPath = '/' }: { auth?: AuthContextValue; initialPath?: string } = {},
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] })
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthContext value={auth}>
          <ToastProvider>{children}</ToastProvider>
        </AuthContext>
      </ThemeProvider>
    </QueryClientProvider>
  )
  return { router, ...render(wrap(<RouterProvider router={router} />)) }
}
