import type { DayOfWeek, TimetableEntry } from '@/types/api'

const ISO_DAY: Record<DayOfWeek, number> = { MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6, SUNDAY: 7 }

export interface UpcomingClass {
  entry: TimetableEntry
  starts: Date
  ends: Date
}

function at(day: Date, time: string): Date {
  const [h = 0, m = 0] = time.split(':').map(Number)
  const d = new Date(day)
  d.setHours(h, m, 0, 0)
  return d
}

/**
 * The next weekly sessions from `now`, soonest first. Session times are wall-clock times at the university, read in
 * the browser's time zone. A class that is under way still counts until it ends.
 */
export function upcomingClasses(entries: TimetableEntry[], now: Date, limit: number): UpcomingClass[] {
  const today = (now.getDay() + 6) % 7 + 1
  const occurrences = entries.map((entry) => {
    let offset = (ISO_DAY[entry.dayOfWeek] - today + 7) % 7
    let day = new Date(now)
    day.setDate(now.getDate() + offset)
    let ends = at(day, entry.endTime)
    if (ends <= now) {
      offset += 7
      day = new Date(now)
      day.setDate(now.getDate() + offset)
      ends = at(day, entry.endTime)
    }
    return { entry, starts: at(day, entry.startTime), ends }
  })
  return occurrences.sort((a, b) => a.starts.getTime() - b.starts.getTime()).slice(0, limit)
}

/** "Today", "Tomorrow" or the weekday. */
export function dayLabel(date: Date, now: Date): string {
  const days = Math.round((new Date(date).setHours(0, 0, 0, 0) - new Date(now).setHours(0, 0, 0, 0)) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Tomorrow'
  return date.toLocaleDateString(undefined, { weekday: 'long' })
}

/** "in 2 h 45 min", "in 25 min", or "now" for a class that has started. */
export function startsIn(starts: Date, now: Date): string {
  const minutes = Math.round((starts.getTime() - now.getTime()) / 60_000)
  if (minutes <= 0) return 'now'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h >= 24) return `in ${Math.floor(h / 24)} d ${h % 24} h`
  return h > 0 ? `in ${h} h ${m} min` : `in ${m} min`
}
