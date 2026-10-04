import type { TimetableEntry } from '@/types/api'
import { layoutTimetable } from './timetableLayout'

function entry(day: TimetableEntry['dayOfWeek'], start: string, end: string, code = 'CS101'): TimetableEntry {
  return { offeringId: 1, courseCode: code, courseTitle: 'Course', section: 'A', lecturer: null, dayOfWeek: day, startTime: start, endTime: end, venue: 'LT-1' }
}

describe('layoutTimetable', () => {
  it('shows the working week from 08:00 to 17:00 by default', () => {
    const layout = layoutTimetable([])
    expect(layout.days).toEqual(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'])
    expect(layout.startHour).toBe(8)
    expect(layout.endHour).toBe(17)
    expect(layout.hours).toHaveLength(9)
  })

  it('positions a session as a percentage of the visible hours', () => {
    // 08:00–17:00 is 540 minutes; 10:00–11:30 starts 120 minutes in and lasts 90.
    const [positioned] = layoutTimetable([entry('MONDAY', '10:00:00', '11:30:00')]).byDay.MONDAY
    expect(positioned?.topPercent).toBeCloseTo((120 / 540) * 100)
    expect(positioned?.heightPercent).toBeCloseTo((90 / 540) * 100)
  })

  it('widens the range for early and late classes and adds weekend days only when used', () => {
    const layout = layoutTimetable([entry('MONDAY', '07:30:00', '09:00:00'), entry('SATURDAY', '18:00:00', '19:45:00')])
    expect(layout.startHour).toBe(7)
    expect(layout.endHour).toBe(20)
    expect(layout.days).toContain('SATURDAY')
    expect(layout.days).not.toContain('SUNDAY')
  })

  it('sorts each day by start time', () => {
    const layout = layoutTimetable([entry('TUESDAY', '14:00', '15:00', 'B'), entry('TUESDAY', '09:00', '10:00', 'A')])
    expect(layout.byDay.TUESDAY.map((e) => e.courseCode)).toEqual(['A', 'B'])
  })
})
