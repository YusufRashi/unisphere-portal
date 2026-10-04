import { navigationFor } from './navigation'

const labels = (roles: Parameters<typeof navigationFor>[0]) =>
  navigationFor(roles).flatMap((section) => section.items.map((item) => item.label))

describe('navigationFor', () => {
  it('shows administration links to admins', () => {
    expect(labels(['ADMIN'])).toContain('Users')
  })

  it('hides administration links and empty sections from other roles', () => {
    for (const role of ['STUDENT', 'LECTURER', 'FINANCE'] as const) {
      expect(labels([role])).not.toContain('Users')
      expect(navigationFor([role]).map((s) => s.title)).not.toContain('Administration')
    }
  })

  it('combines permissions for users with several roles', () => {
    expect(labels(['LECTURER', 'ADMIN'])).toContain('Users')
  })
})
