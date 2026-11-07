import { LogOut, Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '@/auth/authContext'
import { NotificationBell } from '@/features/notifications/NotificationBell'
import { cn } from '@/lib/cn'
import { initials, ROLE_LABELS } from '@/lib/format'
import { Logo } from './Logo'
import { navigationFor } from './navigation'
import { QuickSearch } from './QuickSearch'
import { ThemeSwitch } from './ThemeSwitch'

const CURRENT_YEAR = new Date().getFullYear()

function AccountCard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [signingOut, setSigningOut] = useState(false)
  if (!user) return null

  async function handleLogout() {
    setSigningOut(true)
    try {
      await logout()
    } finally {
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="rounded-xl border border-neutral-200 bg-neutral-50 p-3">
      <div className="flex items-center gap-3">
        <div aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-800">
          {initials(user.firstName, user.lastName)}
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-neutral-900">
            {user.firstName} {user.lastName}
          </p>
          <p className="truncate text-xs text-neutral-500">{user.roles.map((r) => ROLE_LABELS[r]).join(' · ')}</p>
        </div>
      </div>
      <div className="mt-3 flex items-center justify-between gap-2">
        <ThemeSwitch />
        <button
          type="button"
          onClick={handleLogout}
          disabled={signingOut}
          className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-medium text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900"
        >
          <LogOut aria-hidden="true" className="size-3.5" />
          Sign out
        </button>
      </div>
    </div>
  )
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth()
  const sections = navigationFor(user?.roles ?? [])
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center px-5">
        <Logo />
      </div>
      <nav aria-label="Main" className="flex-1 space-y-5 overflow-y-auto px-3 py-2">
        {sections.map((section, i) => (
          <div key={section.title ?? i}>
            <p className="mb-1 px-3 text-[11px] font-medium text-neutral-400">{section.title ?? 'Menu'}</p>
            <ul className="space-y-0.5">
              {section.items.map(({ to, label, icon: Icon }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        'group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
                        isActive ? 'bg-neutral-100 font-medium text-neutral-900' : 'text-neutral-600 hover:bg-neutral-50 hover:text-neutral-900',
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && <span aria-hidden="true" className="absolute top-2 bottom-2 -left-3 w-1 rounded-r-full bg-accent" />}
                        <Icon aria-hidden="true" className={cn('size-4 shrink-0', isActive ? 'text-brand-600' : 'text-neutral-400 group-hover:text-neutral-600')} />
                        {label}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
      <div className="space-y-3 p-3">
        <AccountCard />
        <p className="px-2 text-[11px] text-neutral-400">© {CURRENT_YEAR} UniSphere</p>
      </div>
    </div>
  )
}

/** Shell for every signed-in page: a floating sidebar on large screens, a slide-over drawer on small ones. */
export function AppLayout() {
  // Nav links close the drawer themselves (onNavigate), so no route-change effect is needed.
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    if (!drawerOpen) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setDrawerOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [drawerOpen])

  return (
    <div className="min-h-dvh">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>

      <aside className="fixed inset-y-3 left-3 hidden w-64 rounded-card border border-neutral-200 bg-surface shadow-xs lg:block print:hidden">
        <SidebarContent />
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-black/50" onClick={() => setDrawerOpen(false)} aria-hidden="true" />
          <aside className="relative h-full w-72 max-w-[85vw] border-r border-neutral-200 bg-surface shadow-xl">
            <button
              type="button"
              onClick={() => setDrawerOpen(false)}
              className="absolute top-4 right-3 rounded-lg p-1.5 text-neutral-500 hover:bg-neutral-100"
              aria-label="Close navigation"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
            <SidebarContent onNavigate={() => setDrawerOpen(false)} />
          </aside>
        </div>
      )}

      <div className="lg:pl-[17.5rem] print:pl-0">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 bg-canvas/85 px-4 backdrop-blur sm:px-6 lg:px-8 print:hidden">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            className="rounded-lg p-2 text-neutral-600 hover:bg-neutral-100 lg:hidden"
            aria-label="Open navigation"
          >
            <Menu aria-hidden="true" className="size-5" />
          </button>
          <div className="min-w-0 flex-1">
            <QuickSearch />
          </div>
          <NotificationBell />
        </header>
        <main id="main" className="mx-auto max-w-7xl px-4 pt-2 pb-10 sm:px-6 lg:px-8 print:max-w-none print:p-0">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
