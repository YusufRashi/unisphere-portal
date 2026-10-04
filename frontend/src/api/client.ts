import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '@/types/api'

/**
 * Shared HTTP client.
 *
 * - The access token lives only in memory (never localStorage) to limit what XSS could steal.
 * - The refresh token is an HttpOnly cookie the browser sends to /api/auth automatically.
 * - On a 401 the client refreshes once (single-flight, serialised across tabs) and retries the request.
 */
export const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
    // Required by the backend on cookie-authenticated endpoints (CSRF defence).
    'X-Requested-With': 'XMLHttpRequest',
  },
  timeout: 20_000,
})

let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse> | null = null
const sessionListeners = new Set<(session: AuthResponse | null) => void>()

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

/** Notified when a background refresh succeeds (new user data) or the session ends. */
export function onSessionChange(listener: (session: AuthResponse | null) => void) {
  sessionListeners.add(listener)
  return () => {
    sessionListeners.delete(listener)
  }
}

function notify(session: AuthResponse | null) {
  sessionListeners.forEach((listener) => listener(session))
}

async function doRefresh(): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/refresh', null, { skipAuthRefresh: true })
  setAccessToken(data.accessToken)
  return data
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request; the Web
 * Locks API (when available) also serialises refreshes across tabs so they never race on the same cookie.
 */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    const run = () => doRefresh()
    const locked = typeof navigator !== 'undefined' && navigator.locks
      ? navigator.locks.request('unisphere-auth-refresh', run)
      : run()
    refreshInFlight = locked.finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Do not attempt a token refresh when this request returns 401. */
    skipAuthRefresh?: boolean
  }
  interface InternalAxiosRequestConfig {
    _retried?: boolean
  }
}

api.interceptors.request.use((config) => {
  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as InternalAxiosRequestConfig | undefined
    if (error.response?.status !== 401 || !config || config.skipAuthRefresh || config._retried) {
      throw error
    }
    config._retried = true
    try {
      const session = await refreshSession()
      notify(session)
      config.headers.Authorization = `Bearer ${session.accessToken}`
      return api(config)
    } catch {
      setAccessToken(null)
      notify(null)
      throw error
    }
  },
)
