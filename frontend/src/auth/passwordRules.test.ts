import { passwordProblems } from './passwordRules'

describe('passwordProblems (mirror of the backend PasswordPolicy)', () => {
  it('accepts a password that satisfies every rule', () => {
    expect(passwordProblems('Correct-Horse-42', 'jane.doe@uni.test')).toEqual([])
  })

  it.each([
    ['short1', 'At least 10 characters'],
    ['OnlyLettersHere', 'Contains a number'],
    ['1234567890123', 'Contains a letter'],
  ])('flags %s', (password, problem) => {
    expect(passwordProblems(password)).toContain(problem)
  })

  it('rejects passwords containing the email local part', () => {
    expect(passwordProblems('Jane.Doe-2026!', 'jane.doe@uni.test')).toContain('Does not contain your email')
  })

  it('rejects passwords beyond the 72-byte BCrypt limit', () => {
    expect(passwordProblems('a1'.repeat(40))).toContain('At most 72 bytes')
  })

  it('rejects leading or trailing whitespace', () => {
    expect(passwordProblems(' Password1234')).toContain('No leading or trailing spaces')
  })
})
