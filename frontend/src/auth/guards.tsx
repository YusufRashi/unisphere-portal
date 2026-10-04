import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { FullPageSpinner } from '@/components/ui/Spinner'
import { ForbiddenPage } from '@/pages/ForbiddenPage'
import type { Role } from '@/types/api'
import { useAuth } from './authContext'

/**
 * Route guards improve UX only: they hide screens a user cannot use. Every API endpoint enforces the
 * same rules on the server, so editing the URL or calling the API directly gains nothing.
 */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <FullPageSpinner label="Restoring your session…" />
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return children ?? <Outlet />
}

export function RequireRole({ roles, children }: { roles: Role[]; children?: ReactNode }) {
  const { hasRole } = useAuth()
  if (!hasRole(...roles)) return <ForbiddenPage />
  return children ?? <Outlet />
}

/** For login/reset pages: an already signed-in user goes straight to the app. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <FullPageSpinner label="Loading…" />
  if (status === 'authenticated') return <Navigate to="/dashboard" replace />
  return children
}
