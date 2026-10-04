import { isAxiosError } from 'axios'
import type { ProblemDetails } from '@/types/api'

/** Normalised error used throughout the UI, built from a Problem Details response. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  readonly violations: string[]
  readonly requestId?: string

  constructor(problem: ProblemDetails) {
    super(problem.detail ?? problem.title ?? 'Something went wrong.')
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code ?? 'UNKNOWN'
    this.fieldErrors = Object.fromEntries((problem.errors ?? []).map((e) => [e.field, e.message]))
    this.violations = problem.violations ?? []
    this.requestId = problem.requestId
  }
}

function isProblem(data: unknown): data is ProblemDetails {
  return typeof data === 'object' && data !== null && 'status' in data
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (isAxiosError(error)) {
    if (error.response && isProblem(error.response.data)) {
      return new ApiError(error.response.data)
    }
    if (error.response) {
      return new ApiError({ status: error.response.status, detail: 'The server returned an unexpected response.' })
    }
    return new ApiError({ status: 0, code: 'NETWORK_ERROR', detail: 'Cannot reach the server. Check your connection.' })
  }
  return new ApiError({ status: 0, detail: 'An unexpected error occurred.' })
}

/** User-facing message for any thrown value. */
export function errorMessage(error: unknown): string {
  return toApiError(error).message
}
