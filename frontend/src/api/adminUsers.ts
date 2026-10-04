import { api } from './client'
import type { PageResponse, Role, UserAccount, UserStatus } from '@/types/api'

export interface UserSearchParams {
  q?: string
  role?: Role
  status?: UserStatus
  page?: number
  size?: number
  sort?: string
}

export async function searchUsers(params: UserSearchParams): Promise<PageResponse<UserAccount>> {
  // Drop empty values so they are not sent as "?role=".
  const query = Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== ''))
  const { data } = await api.get<PageResponse<UserAccount>>('/admin/users', { params: query })
  return data
}

export async function deactivateUser(id: number): Promise<void> {
  await api.post(`/admin/users/${id}/deactivate`)
}

export async function reactivateUser(id: number): Promise<void> {
  await api.post(`/admin/users/${id}/activate`)
}
