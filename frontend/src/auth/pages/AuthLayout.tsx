import { useEffect, type ReactNode } from 'react'
import { Logo } from '@/components/layout/Logo'

/** Two-column layout for signed-out pages: institutional panel + form. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  useEffect(() => {
    document.title = `${title} · UniSphere`
  }, [title])

  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside className="relative m-3 hidden overflow-hidden rounded-card bg-hero p-10 text-white lg:flex lg:flex-col lg:justify-between">
        <Logo onDark />
        <div className="max-w-md">
          <h2 className="text-3xl leading-tight font-semibold tracking-tight">
            Everything about your academic life, in one place.
          </h2>
          <p className="mt-4 text-white/70">
            Register for courses, follow your results and GPA, download transcripts and keep track of fees — with
            dedicated workspaces for lecturers, the registrar and finance.
          </p>
        </div>
        <p className="text-xs text-white/50">UniSphere University Information System</p>
        {/* Subtle decorative rings */}
        <svg aria-hidden="true" className="pointer-events-none absolute -right-24 -bottom-24 size-96 text-white/5" viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="48" fill="none" stroke="currentColor" strokeWidth="0.6" />
          <circle cx="50" cy="50" r="36" fill="none" stroke="currentColor" strokeWidth="0.6" />
          <circle cx="50" cy="50" r="24" fill="none" stroke="currentColor" strokeWidth="0.6" />
        </svg>
      </aside>
      <main className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm rounded-card border border-neutral-200 bg-surface p-6 shadow-xs sm:p-8">
          <Logo className="mb-8 lg:hidden" />
          <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-neutral-500">{subtitle}</p>}
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  )
}
