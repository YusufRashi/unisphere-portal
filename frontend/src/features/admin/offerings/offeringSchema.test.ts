import { offeringSchema, type OfferingFormInput } from './offeringSchema'

function valid(overrides: Partial<OfferingFormInput> = {}): OfferingFormInput {
  return {
    course: { value: 1, label: 'CS201 — Data Structures' },
    semesterId: '3',
    section: 'A',
    lecturer: null,
    capacity: '40',
    catWeight: '30',
    examStartsAt: '',
    examVenue: '',
    sessions: [{ dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '10:00', venue: 'LT-1' }],
    ...overrides,
  }
}

function issues(input: OfferingFormInput) {
  const result = offeringSchema.safeParse(input)
  return result.success ? [] : result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`)
}

describe('offeringSchema', () => {
  it('accepts a valid offering and coerces numbers', () => {
    const result = offeringSchema.parse(valid())
    expect(result.capacity).toBe(40)
    expect(result.semesterId).toBe(3)
  })

  it('requires a course', () => {
    expect(issues(valid({ course: null }))).toContain('course: Choose a course.')
  })

  it('rejects a session that ends before it starts', () => {
    expect(issues(valid({ sessions: [{ dayOfWeek: 'MONDAY', startTime: '10:00', endTime: '09:00', venue: 'LT-1' }] })))
      .toContain('sessions.0.endTime: Must be after the start time.')
  })

  it('rejects overlapping sessions on the same day but allows back-to-back ones', () => {
    const overlapping = valid({
      sessions: [
        { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '10:00', venue: 'LT-1' },
        { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '11:00', venue: 'LAB' },
      ],
    })
    expect(issues(overlapping)).toContain('sessions.1.startTime: Overlaps session 1.')

    const backToBack = valid({
      sessions: [
        { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '10:00', venue: 'LT-1' },
        { dayOfWeek: 'MONDAY', startTime: '10:00', endTime: '12:00', venue: 'LT-1' },
      ],
    })
    expect(issues(backToBack)).toEqual([])
  })

  it('allows the same time on different days', () => {
    expect(issues(valid({
      sessions: [
        { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '10:00', venue: 'LT-1' },
        { dayOfWeek: 'TUESDAY', startTime: '08:00', endTime: '10:00', venue: 'LT-1' },
      ],
    }))).toEqual([])
  })

  it.each([
    ['capacity', { capacity: '0' }, 'At least 1 seat.'],
    ['catWeight', { catWeight: '120' }, 'Between 0 and 100.'],
    ['section', { section: 'A/B' }, 'Letters and digits only.'],
  ] as const)('validates %s', (field, overrides, message) => {
    expect(issues(valid(overrides))).toContain(`${field}: ${message}`)
  })

  it('requires a venue for every session', () => {
    expect(issues(valid({ sessions: [{ dayOfWeek: 'FRIDAY', startTime: '08:00', endTime: '09:00', venue: ' ' }] })))
      .toContain('sessions.0.venue: Enter a venue.')
  })
})
