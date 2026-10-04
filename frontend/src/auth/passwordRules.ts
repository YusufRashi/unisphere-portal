// Mirrors PasswordPolicy.java for instant feedback. The server remains the authority.

export const PASSWORD_MIN_LENGTH = 10
const PASSWORD_MAX_BYTES = 72

export interface PasswordRule {
  id: string
  label: string
  test: (password: string, email?: string) => boolean
}

export const PASSWORD_RULES: PasswordRule[] = [
  { id: 'length', label: `At least ${PASSWORD_MIN_LENGTH} characters`, test: (p) => p.length >= PASSWORD_MIN_LENGTH },
  { id: 'letter', label: 'Contains a letter', test: (p) => /\p{L}/u.test(p) },
  { id: 'digit', label: 'Contains a number', test: (p) => /\p{Nd}/u.test(p) },
  {
    id: 'email',
    label: 'Does not contain your email',
    test: (p, email) => {
      if (!email || !p) return true
      const local = email.toLowerCase().split('@')[0] ?? ''
      const lower = p.toLowerCase()
      return lower !== email.toLowerCase() && !(local.length >= 4 && lower.includes(local))
    },
  },
]

export function passwordProblems(password: string, email?: string): string[] {
  const problems = PASSWORD_RULES.filter((r) => !r.test(password, email)).map((r) => r.label)
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) problems.push('At most 72 bytes')
  if (password !== password.trim()) problems.push('No leading or trailing spaces')
  return problems
}
