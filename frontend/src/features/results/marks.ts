import type { GradeBand } from '@/types/api'

/**
 * Client-side mirror of the server's mark rules, used only for instant feedback while typing. The API
 * validates again and computes the authoritative grade.
 */

/** Parses a mark input: '' → null (not entered), otherwise a number, or undefined when not a valid number. */
export function parseMark(input: string): number | null | undefined {
  const trimmed = input.trim()
  if (trimmed === '') return null
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(trimmed)) return undefined
  return Number(trimmed)
}

/** Why a typed mark is not acceptable for a component worth `weight` marks, or null if it is. */
export function markError(input: string, weight: number): string | null {
  const trimmed = input.trim()
  if (trimmed === '') return null
  if (/^-/.test(trimmed)) return 'Must not be negative'
  if (/^\d+\.\d{3,}$/.test(trimmed)) return 'At most 2 decimal places'
  const value = parseMark(trimmed)
  if (value === undefined || value === null) return 'Enter a number'
  if (value > weight) return `At most ${formatNumber(weight)}`
  return null
}

/** The band a total falls into (bands may be in any order), or null without a total. */
export function previewGrade(total: number | null, bands: GradeBand[]): GradeBand | null {
  if (total === null) return null
  const ordered = [...bands].sort((a, b) => b.minMark - a.minMark)
  return ordered.find((b) => total >= b.minMark) ?? null
}

/** Adds two marks without floating-point noise (both have at most 2 decimals). */
export function addMarks(cat: number | null, exam: number | null): number | null {
  if (cat === null || exam === null) return null
  return Math.round((cat + exam) * 100) / 100
}

/** 85 → "85", 85.5 → "85.5", 3.428 → "3.43" (for marks and grade points). */
export function formatNumber(value: number | null | undefined, maxDecimals = 2): string {
  if (value === null || value === undefined) return '—'
  return Number(value.toFixed(maxDecimals)).toString()
}

/** GPA is always shown with two decimals, as computed by the server. */
export function formatGpa(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toFixed(2)
}
