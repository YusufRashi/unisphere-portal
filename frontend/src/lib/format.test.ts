import { formatDate, timeAgo } from './format'

describe('timeAgo', () => {
  const now = Date.parse('2026-10-11T12:00:00Z')
  const ago = (seconds: number) => new Date(now - seconds * 1000).toISOString()

  it('describes recent times relative to now', () => {
    expect(timeAgo(ago(20), now)).toBe('just now')
    expect(timeAgo(ago(5 * 60), now)).toBe('5 minutes ago')
    expect(timeAgo(ago(3 * 3600), now)).toBe('3 hours ago')
    expect(timeAgo(ago(26 * 3600), now)).toBe('yesterday')
    expect(timeAgo(ago(3 * 86_400), now)).toBe('3 days ago')
  })

  it('falls back to the date after a week', () => {
    const old = ago(10 * 86_400)
    expect(timeAgo(old, now)).toBe(formatDate(old))
  })
})
