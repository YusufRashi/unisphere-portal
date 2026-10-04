import { DAYS_OF_WEEK, type DayOfWeek, type TimetableEntry } from '@/types/api'

export interface PositionedEntry extends TimetableEntry {
  /** Offset from the top of the grid and height, as percentages of the visible hours. */
  topPercent: number
  heightPercent: number
}

export interface TimetableLayout {
  days: DayOfWeek[]
  startHour: number
  endHour: number
  hours: number[]
  byDay: Record<DayOfWeek, PositionedEntry[]>
}

const toMinutes = (time: string) => {
  const [h = '0', m = '0'] = time.split(':')
  return Number(h) * 60 + Number(m)
}

/**
 * Lays out weekly sessions on a grid. Shows Monday–Friday always and weekend days only when used; the hour
 * range covers 08:00–17:00 at least and widens to fit early or late classes.
 */
export function layoutTimetable(entries: TimetableEntry[]): TimetableLayout {
  const weekend: DayOfWeek[] = ['SATURDAY', 'SUNDAY']
  const days = DAYS_OF_WEEK.filter((d) => !weekend.includes(d) || entries.some((e) => e.dayOfWeek === d))

  const earliest = Math.min(8 * 60, ...entries.map((e) => toMinutes(e.startTime)))
  const latest = Math.max(17 * 60, ...entries.map((e) => toMinutes(e.endTime)))
  const startHour = Math.floor(earliest / 60)
  const endHour = Math.ceil(latest / 60)
  const span = (endHour - startHour) * 60

  const byDay = Object.fromEntries(DAYS_OF_WEEK.map((d) => [d, [] as PositionedEntry[]])) as Record<DayOfWeek, PositionedEntry[]>
  for (const e of entries) {
    const start = toMinutes(e.startTime) - startHour * 60
    const end = toMinutes(e.endTime) - startHour * 60
    byDay[e.dayOfWeek].push({ ...e, topPercent: (start / span) * 100, heightPercent: ((end - start) / span) * 100 })
  }
  for (const d of DAYS_OF_WEEK) byDay[d].sort((a, b) => a.startTime.localeCompare(b.startTime))

  return {
    days,
    startHour,
    endHour,
    hours: Array.from({ length: endHour - startHour }, (_, i) => startHour + i),
    byDay,
  }
}
