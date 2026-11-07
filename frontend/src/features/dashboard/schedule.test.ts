import type { DayOfWeek, TimetableEntry } from '@/types/api'
import { dayLabel, startsIn, upcomingClasses } from './schedule'

function entry(code: string, day: DayOfWeek, start: string, end: string): TimetableEntry {
  return { offeringId: code.length, courseCode: code, courseTitle: code, section: 'A', lecturer: null, dayOfWeek: day, startTime: start, endTime: end, venue: 'LT1' }
}

// Wednesday 11 November 2026, 10:30 local time.
const now = new Date(2026, 10, 11, 10, 30)

describe('upcomingClasses', () => {
  it('orders the next sessions from now, keeping one that is under way', () => {
    const result = upcomingClasses(
      [entry('MON', 'MONDAY', '08:00:00', '10:00:00'), entry('WED-NOW', 'WEDNESDAY', '10:00:00', '12:00:00'), entry('THU', 'THURSDAY', '09:00:00', '11:00:00')],
      now,
      5,
    )
    expect(result.map((r) => r.entry.courseCode)).toEqual(['WED-NOW', 'THU', 'MON'])
    expect(result[2]?.starts).toEqual(new Date(2026, 10, 16, 8, 0))
  })

  it('moves a session that already ended today to next week', () => {
    const [only] = upcomingClasses([entry('WED-EARLY', 'WEDNESDAY', '07:00:00', '09:00:00')], now, 1)
    expect(only?.starts).toEqual(new Date(2026, 10, 18, 7, 0))
  })

  it('keeps at most the limit', () => {
    expect(upcomingClasses([entry('A', 'MONDAY', '08:00', '09:00'), entry('B', 'TUESDAY', '08:00', '09:00')], now, 1)).toHaveLength(1)
  })
})

describe('labels', () => {
  it('names the day relative to today', () => {
    expect(dayLabel(new Date(2026, 10, 11, 15), now)).toBe('Today')
    expect(dayLabel(new Date(2026, 10, 12, 9), now)).toBe('Tomorrow')
  })

  it('counts down to the start', () => {
    expect(startsIn(new Date(2026, 10, 11, 13, 15), now)).toBe('in 2 h 45 min')
    expect(startsIn(new Date(2026, 10, 11, 10, 55), now)).toBe('in 25 min')
    expect(startsIn(new Date(2026, 10, 11, 10, 0), now)).toBe('now')
    expect(startsIn(new Date(2026, 10, 13, 12, 30), now)).toBe('in 2 d 2 h')
  })
})
