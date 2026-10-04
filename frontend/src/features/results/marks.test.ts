import type { GradeBand } from '@/types/api'
import { addMarks, formatGpa, formatNumber, markError, parseMark, previewGrade } from './marks'

const bands: GradeBand[] = [
  { letter: 'F', minMark: 0, gradePoints: 0, passing: false },
  { letter: 'A', minMark: 70, gradePoints: 4, passing: true },
  { letter: 'C', minMark: 50, gradePoints: 2, passing: true },
]

describe('marks helpers', () => {
  it('parses mark inputs', () => {
    expect(parseMark('')).toBeNull()
    expect(parseMark('  ')).toBeNull()
    expect(parseMark('25')).toBe(25)
    expect(parseMark('25.75')).toBe(25.75)
    expect(parseMark('25.755')).toBeUndefined()
    expect(parseMark('abc')).toBeUndefined()
    expect(parseMark('-3')).toBeUndefined()
  })

  it('explains invalid marks against the component weight', () => {
    expect(markError('', 30)).toBeNull()
    expect(markError('30', 30)).toBeNull()
    expect(markError('30.5', 30)).toBe('At most 30')
    expect(markError('-1', 30)).toBe('Must not be negative')
    expect(markError('12.345', 30)).toBe('At most 2 decimal places')
    expect(markError('ten', 30)).toBe('Enter a number')
  })

  it('previews the grade with inclusive lower bounds', () => {
    expect(previewGrade(null, bands)).toBeNull()
    expect(previewGrade(70, bands)?.letter).toBe('A')
    expect(previewGrade(69.99, bands)?.letter).toBe('C')
    expect(previewGrade(49.99, bands)?.letter).toBe('F')
  })

  it('adds marks without floating-point noise', () => {
    expect(addMarks(0.1, 0.2)).toBe(0.3)
    expect(addMarks(25.55, 44.45)).toBe(70)
    expect(addMarks(10, null)).toBeNull()
  })

  it('formats numbers and GPAs', () => {
    expect(formatNumber(85)).toBe('85')
    expect(formatNumber(85.5)).toBe('85.5')
    expect(formatNumber(null)).toBe('—')
    expect(formatGpa(3.4)).toBe('3.40')
    expect(formatGpa(null)).toBe('—')
  })
})
