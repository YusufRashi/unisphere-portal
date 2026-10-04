import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '@/api/auth'
import { onSessionChange } from '@/api/client'
import type { CurrentUser, Role } from '@/types/api'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<CurrentUser | null>(null)

  // Restore an existing session from the refresh cookie on first load.
  useEffect(() => {
    let cancelled = false
    authApi
      .refresh()
      .then((session) => {
        if (!cancelled) {
          setUser(session.user)
          setStatus('authenticated')
        }
      })
      .catch(() => {
        if (!cancelled) setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [])

  // Keep React state in sync with refreshes / expiry detected by the HTTP client.
  useEffect(
    () =>
      onSessionChange((session) => {
        if (session) {
          setUser(session.user)
          setStatus('authenticated')
        } else {
          setUser(null)
          setStatus('anonymous')
          queryClient.clear()
        }
      }),
    [queryClient],
  )

  const login = useCallback(async (email: string, password: string) => {
    const session = await authApi.login(email, password)
    setUser(session.user)
    setStatus('authenticated')
    return session.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
      setStatus('anonymous')
      // Never let cached data from one user leak into the next session on a shared computer.
      queryClient.clear()
    }
  }, [queryClient])

  const setSessionUser = useCallback((next: CurrentUser) => {
    setUser(next)
    setStatus('authenticated')
  }, [])

  const hasRole = useCallback((...roles: Role[]) => !!user && roles.some((r) => user.roles.includes(r)), [user])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, setSessionUser, hasRole }),
    [status, user, login, logout, setSessionUser, hasRole],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
