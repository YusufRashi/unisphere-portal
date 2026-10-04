import { api } from './client'
import { cleanParams, type ListParams } from './query'
import type { PageResponse, Role, UserAccount, UserStatus } from '@/types/api'

export interface UserSearchParams extends ListParams {
  role?: Role
  status?: UserStatus
}

export async function searchUsers(params: UserSearchParams): Promise<PageResponse<UserAccount>> {
  return (await api.get<PageResponse<UserAccount>>('/admin/users', { params: cleanParams(params) })).data
}

export async function deactivateUser(id: number): Promise<void> {
  await api.post(`/admin/users/${id}/deactivate`)
}

export async function reactivateUser(id: number): Promise<void> {
  await api.post(`/admin/users/${id}/activate`)
}
