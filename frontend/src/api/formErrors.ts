import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { toApiError } from './errors'

/**
 * Maps server-side field errors (Problem Details "errors") onto react-hook-form fields so the message
 * appears next to the right input. Returns a message for anything that could not be attached to a field.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): string | null {
  const apiError = toApiError(error)
  const unmatched: string[] = []
  Object.entries(apiError.fieldErrors).forEach(([field, message]) => {
    if ((knownFields as readonly string[]).includes(field)) {
      setError(field as Path<T>, { type: 'server', message: capitalize(message) })
    } else {
      unmatched.push(`${field}: ${message}`)
    }
  })
  if (Object.keys(apiError.fieldErrors).length === 0) return apiError.message
  return unmatched.length > 0 ? unmatched.join(' ') : null
}

function capitalize(message: string): string {
  return message.charAt(0).toUpperCase() + message.slice(1)
}
