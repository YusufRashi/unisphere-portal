import { fromLocalInput, shortTime, timesOverlap, toLocalInput } from './datetime'

describe('datetime helpers', () => {
  it('round-trips between a UTC instant and a datetime-local value', () => {
    const iso = '2026-09-01T05:30:00.000Z'
    const local = toLocalInput(iso)
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
    expect(fromLocalInput(local)).toBe(iso)
  })

  it('treats empty and invalid values as missing', () => {
    expect(toLocalInput(null)).toBe('')
    expect(fromLocalInput('')).toBeNull()
    expect(fromLocalInput('not a date')).toBeNull()
  })

  it('shortens API times to hours and minutes', () => {
    expect(shortTime('08:00:00')).toBe('08:00')
  })

  it('detects overlapping times but not back-to-back ones', () => {
    expect(timesOverlap('08:00', '10:00', '09:00', '11:00')).toBe(true)
    expect(timesOverlap('08:00', '10:00', '10:00', '12:00')).toBe(false)
    expect(timesOverlap('09:00', '09:30', '08:00', '12:00')).toBe(true)
  })
})
