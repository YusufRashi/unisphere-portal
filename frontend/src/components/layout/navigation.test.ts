import { NAVIGATION, navigationFor } from './navigation'

const labels = (roles: Parameters<typeof navigationFor>[0]) =>
  navigationFor(roles).flatMap((section) => section.items.map((item) => item.label))

const adminOnly = NAVIGATION.flatMap((s) => s.items).filter((i) => i.roles?.length === 1 && i.roles[0] === 'ADMIN')

describe('navigationFor', () => {
  it('shows every administration link to admins', () => {
    expect(adminOnly.length).toBeGreaterThan(0)
    expect(labels(['ADMIN'])).toEqual(expect.arrayContaining(adminOnly.map((i) => i.label)))
  })

  it('hides administration links and empty sections from other roles', () => {
    for (const role of ['STUDENT', 'LECTURER', 'FINANCE'] as const) {
      const visible = labels([role])
      adminOnly.forEach((item) => expect(visible).not.toContain(item.label))
      expect(navigationFor([role]).map((s) => s.title)).not.toContain('Academic')
      expect(navigationFor([role]).map((s) => s.title)).not.toContain('People')
    }
  })

  it('combines permissions for users with several roles', () => {
    expect(labels(['LECTURER', 'ADMIN'])).toContain('Students')
  })

  it('always offers the dashboard and account settings', () => {
    expect(labels(['STUDENT'])).toEqual(['Dashboard', 'Account settings'])
  })
})
