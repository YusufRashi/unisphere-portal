import { api, refreshSession, setAccessToken } from './client'
import type { AuthResponse, CurrentUser } from '@/types/api'

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', { email, password }, { skipAuthRefresh: true })
  setAccessToken(data.accessToken)
  return data
}

export function refresh(): Promise<AuthResponse> {
  return refreshSession()
}

export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout', null, { skipAuthRefresh: true })
  } finally {
    setAccessToken(null)
  }
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const { data } = await api.get<CurrentUser>('/auth/me')
  return data
}

export async function activateAccount(token: string, password: string): Promise<void> {
  await api.post('/auth/activate', { token, password }, { skipAuthRefresh: true })
}

export async function requestPasswordReset(email: string): Promise<void> {
  await api.post('/auth/password/forgot', { email }, { skipAuthRefresh: true })
}

export async function resetPassword(token: string, password: string): Promise<void> {
  await api.post('/auth/password/reset', { token, password }, { skipAuthRefresh: true })
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/password/change', { currentPassword, newPassword })
  setAccessToken(data.accessToken)
  return data
}
