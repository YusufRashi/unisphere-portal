import { createContext, use } from 'react'
import type { CurrentUser, Role } from '@/types/api'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: CurrentUser | null
  login: (email: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  /** Replace the session after an operation that issues new tokens (e.g. password change). */
  setSessionUser: (user: CurrentUser) => void
  hasRole: (...roles: Role[]) => boolean
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
