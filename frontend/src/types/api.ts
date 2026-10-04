// Types mirroring the backend DTOs. Keep in sync with docs/04-api.md.

export const ROLES = ['STUDENT', 'LECTURER', 'ADMIN', 'FINANCE'] as const
export type Role = (typeof ROLES)[number]

export const USER_STATUSES = ['PENDING_ACTIVATION', 'ACTIVE', 'DEACTIVATED'] as const
export type UserStatus = (typeof USER_STATUSES)[number]

export interface CurrentUser {
  id: number
  email: string
  firstName: string
  lastName: string
  roles: Role[]
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: CurrentUser
}

export interface UserAccount {
  id: number
  email: string
  firstName: string
  lastName: string
  phone: string | null
  status: UserStatus
  roles: Role[]
  lastLoginAt: string | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** RFC 9457 Problem Details as returned by the API. */
export interface ProblemDetails {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
  violations?: string[]
  requestId?: string
  retryAfterSeconds?: number
}
